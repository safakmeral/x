package tr.yasliasistan.app.device

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import tr.yasliasistan.app.accessibility.ScreenActions
import tr.yasliasistan.app.accessibility.ScreenReader
import tr.yasliasistan.app.voice.AssistantVoice
import tr.yasliasistan.core.action.Point
import tr.yasliasistan.core.action.Swipe
import tr.yasliasistan.core.action.frontPackage
import tr.yasliasistan.core.log.SafeLog
import tr.yasliasistan.core.screen.Bounds
import tr.yasliasistan.core.screen.ScreenElement
import tr.yasliasistan.core.screen.ScreenSnapshot
import tr.yasliasistan.core.text.TurkishText

/**
 * "Ben yapayım" teacher mode (demo 18.11.2026, minimal G-41/G-42): "Şafak'a WhatsApp'tan fotoğraf
 * göndermeyi öğret". The assistant never presses anything. It reads the screen every moment, works
 * out where the user is, and shows the next thing to press with one yellow ring (or an arrow for a
 * swipe) and one spoken sentence. When the user presses it, the old ring goes and the next step is
 * shown: home screen / folder / app list -> WhatsApp icon -> the person's chat -> attach (Ekle) ->
 * Galeri -> photo -> Gönder. Labels were read from WhatsApp on the student's Galaxy A15.
 *
 * The screen is read only while this guide runs (memory only, only state codes are logged).
 * Ends when the photo is sent, after [MAX_MS], or when the bubble or "Dur" is pressed ([stop]).
 */
internal object PhotoGuideTask {

    private enum class Step { OTHER_APP, HOME, APP_LIST, WA_OTHER, CHAT_LIST, CHAT, ATTACH_SHEET, GALLERY, PREVIEW, DONE }

    /** What is shown now: the step, the ringed element's place and the sentence (to avoid repeats). */
    private data class Shown(val step: Step, val target: Bounds?, val page: Int)

    @Volatile
    var active: Boolean = false
        private set

    private var thread: Thread? = null
    private val main = Handler(Looper.getMainLooper())

    fun start(contactName: String) {
        stop()
        val reader = ScreenActions.reader ?: return AssistantVoice.sayNow("Önce asistanın erişilebilirlik iznini açmanız gerekiyor.")
        active = true
        val first = contactName.split(' ').first()
        thread = Thread({ run(reader, first) }, "assistant-guide").also { it.start() }
    }

    fun stop() {
        active = false
        thread?.interrupt()
        thread = null
        ScreenActions.guide?.clearGuide()
    }

    private fun run(reader: ScreenReader, name: String) {
        val key = TurkishText.normalize(name)
        val started = SystemClock.elapsedRealtime()
        var last: Shown? = null
        var lastSaid = 0L
        var sawPreview = false
        try {
            while (active && SystemClock.elapsedRealtime() - started < MAX_MS) {
                val screen = reader.snapshot()
                if (screen != null) {
                    var step = classify(screen)
                    if (step == Step.PREVIEW) sawPreview = true
                    if (sawPreview && step == Step.CHAT) step = Step.DONE
                    val target = target(step, screen, key)
                    val shown = Shown(step, target?.bounds, pageSignature(screen, step))
                    val now = SystemClock.elapsedRealtime()
                    val moved = last != null && last.step == step && last.target != shown.target
                    when {
                        last == null || last.step != step || last.page != shown.page -> {
                            SafeLog.event(TAG, "guide step $step")
                            show(step, screen, target, name)
                            lastSaid = now
                        }
                        // Same step, but the element moved (keyboard, sheet): only the ring follows it.
                        moved -> ring(target)
                        now - lastSaid > REPEAT_MS -> {
                            show(step, screen, target, name)
                            lastSaid = now
                        }
                    }
                    last = shown
                    if (step == Step.DONE) break
                }
                Thread.sleep(POLL_MS)
            }
            if (active && last?.step != Step.DONE) say("Rehberliği bitirdim. İsterseniz baloncuğa dokunup yeniden isteyin.")
        } catch (_: InterruptedException) {
            // stopped
        } finally {
            active = false
            ScreenActions.guide?.clearGuide()
        }
    }

    private fun classify(s: ScreenSnapshot): Step {
        val pkg = s.frontPackage()
        if (pkg == WHATSAPP) {
            return when {
                has(s, "gonder") -> Step.PREVIEW
                has(s, "klasor secin") -> Step.GALLERY // the gallery sheet above the chat
                has(s, "galeri") && has(s, "belge") -> Step.ATTACH_SHEET
                has(s, "ekle") && has(s, "kamera") -> Step.CHAT
                has(s, "yeni sohbet") -> Step.CHAT_LIST
                else -> Step.WA_OTHER
            }
        }
        if (pkg != null && pkg.contains("launcher")) {
            // Samsung home pages have the "Google Keşfet" page dot; the app list has not.
            return if (s.elements.any { it.label == "Google Keşfet" }) Step.HOME else Step.APP_LIST
        }
        return Step.OTHER_APP
    }

    private fun target(step: Step, s: ScreenSnapshot, key: String): ScreenElement? = when (step) {
        Step.HOME, Step.APP_LIST -> find(s, "whatsapp")
        Step.CHAT_LIST -> s.elements.firstOrNull { e ->
            e.clickable && e.label?.let { TurkishText.normalize(it).split(' ').contains(key) } == true
        } ?: s.elements.firstOrNull { it.clickable && it.label?.contains("Ara") == true }
        Step.CHAT -> bottomMost(s, "ekle")
        Step.ATTACH_SHEET -> find(s, "galeri")
        // Photos selected: WhatsApp shows a green button at the bottom; ring it as soon as it appears.
        Step.GALLERY -> s.elements.filter { e ->
            e.clickable && e.enabled && e.label?.let { l ->
                val n = TurkishText.normalize(l)
                n.startsWith("gonder") || n.startsWith("ileri") || n == "tamam" || n.startsWith("devam")
            } == true
        }.maxByOrNull { it.bounds.top }
        Step.PREVIEW -> find(s, "gonder")
        else -> null
    }

    /** Changes when the user moves to another launcher page or opens a folder (new things visible). */
    private fun pageSignature(s: ScreenSnapshot, step: Step): Int =
        if (step == Step.HOME || step == Step.APP_LIST) s.elements.mapNotNull { it.label }.take(6).hashCode() else 0

    private fun ring(target: ScreenElement?) {
        val guide = ScreenActions.guide ?: return
        if (target != null) guide.showGuide(target.bounds) else guide.clearGuide()
    }

    private fun show(step: Step, s: ScreenSnapshot, target: ScreenElement?, name: String) {
        val guide = ScreenActions.guide
        ring(target)
        val folders = s.elements.any { it.label?.contains("Klasör") == true }
        when (step) {
            Step.OTHER_APP -> say("Önce ana ekrana dönün. Ekranın en altındaki ana ekran tuşuna basın.")
            Step.HOME -> if (target != null) {
                say("Bu WhatsApp. Açmak için sarı halkadaki simgeye bir kez dokunun.")
            } else {
                guide?.showSwipe(Swipe(Point(540, 1700), Point(540, 700)))
                say(
                    (if (folders) "WhatsApp bu sayfada görünmüyor. Bir klasörün içinde olabilir; klasöre dokunup içine bakabilirsiniz. " else "WhatsApp bu sayfada görünmüyor. ") +
                        "Ya da tüm uygulamaları görmek için ekranı aşağıdan yukarı kaydırın.",
                )
            }
            Step.APP_LIST -> if (target != null) {
                say("İşte WhatsApp. Sarı halkadaki simgeye bir kez dokunun.")
            } else {
                guide?.showSwipe(Swipe(Point(950, 1200), Point(150, 1200)))
                say(
                    "WhatsApp bu sayfada yok. Sonraki sayfa için ekranı sağdan sola kaydırın." +
                        if (folders) " Bir klasörün içinde de olabilir; klasöre dokunup bakabilirsiniz." else "",
                )
            }
            Step.WA_OTHER -> say("WhatsApp'ta sohbetler sayfasına dönmek için geri tuşuna basın.")
            Step.CHAT_LIST -> if (target != null && target.label?.contains("Ara") != true) {
                say("$name ile sohbetiniz burada. Sarı halkadaki ismin üzerine dokunun. Profil fotoğrafına değil, ismine dokunun.")
            } else {
                say("Yukarıdaki arama çubuğuna dokunun ve $name yazın. Sonra çıkan sohbete dokunun.")
            }
            Step.CHAT -> say("Sohbeti açtınız. Şimdi mesaj kutusunun yanındaki ataş düğmesine dokunun. Sarı halkayla gösterdim.")
            Step.ATTACH_SHEET -> say("Fotoğraflarınızı görmek için Galeri'ye dokunun. Ya da aşağıdaki son fotoğraflardan birine dokunabilirsiniz.")
            Step.GALLERY -> say(
                "Göndermek istediğiniz fotoğrafların üzerine dokunarak seçin. Seçmeyi bitirince sağ alttaki yeşil düğmeye basın; " +
                    "o düğme görünce sarı halkayla göstereceğim. Tek fotoğrafa dokunursanız doğrudan gönderme ekranı açılır.",
            )
            Step.PREVIEW -> say("Fotoğraflar hazır. Göndermek için sarı halkadaki yeşil Gönder düğmesine basın.")
            Step.DONE -> say("Tebrikler, fotoğrafı kendiniz gönderdiniz.")
        }
    }

    private fun has(s: ScreenSnapshot, label: String) = find(s, label) != null

    private fun find(s: ScreenSnapshot, label: String): ScreenElement? =
        s.elements.lastOrNull { e -> e.enabled && !e.secret && e.label?.let { TurkishText.normalize(it) } == label }

    /** The lowest match on the screen (the chat's attach button sits in the input bar at the bottom). */
    private fun bottomMost(s: ScreenSnapshot, label: String): ScreenElement? =
        s.elements.filter { e -> e.enabled && e.clickable && e.label?.let { TurkishText.normalize(it) } == label }
            .maxByOrNull { it.bounds.top }

    private fun say(text: String) = main.post { AssistantVoice.sayNow(text) }

    private const val TAG = "PhotoGuide"
    private const val WHATSAPP = "com.whatsapp"
    private const val POLL_MS = 600L
    private const val REPEAT_MS = 25_000L
    private const val MAX_MS = 4 * 60_000L
}
