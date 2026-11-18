package tr.yasliasistan.core.masking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tr.yasliasistan.core.screen.Bounds
import tr.yasliasistan.core.screen.ElementRole
import tr.yasliasistan.core.screen.LabelSource
import tr.yasliasistan.core.screen.OwnWindowFilter
import tr.yasliasistan.core.screen.RawNode
import tr.yasliasistan.core.screen.RawWindow
import tr.yasliasistan.core.screen.ScreenCompressor
import tr.yasliasistan.core.screen.ScreenElement
import tr.yasliasistan.core.screen.ScreenSnapshot
import tr.yasliasistan.core.screen.ScreenWindow
import tr.yasliasistan.core.screen.TextKind
import tr.yasliasistan.core.screen.WindowKind

/**
 * PLAN G-32 with invented screens that are NOT in mask-corpus.json (independent of the lexicon
 * that was derived from the corpus). All names, numbers and addresses are made up.
 */
class ScreenMaskingTest {

    private val contacts = ContactIndex.build(listOf(ContactName("c1", "Nuri Örnek"), ContactName("c2", "Fadime Örnek")))
    private val scope = ScreenScope(
        allowedPackages = setOf(WHATSAPP, SETTINGS, LAUNCHER),
        hiddenAppNames = listOf("Şeker Günlüğüm"),
    )

    private fun session() = MaskingSession(contacts)

    private fun masked(vararg elements: ScreenElement, session: MaskingSession = session()): List<String> {
        val result = session.maskScreen(snapshot(*elements), scope)
        return (result as ScreenMaskResult.Masked).screen.elements.map { it.label }
    }

    @Test
    fun settingsRowsStayButTheirPersonalSummariesAreMasked() {
        val labels = masked(
            el(1, "Bağlantılar", TextKind.LIST_CONTENT, pkg = SETTINGS, inList = true),
            el(2, "Wi-Fi, Komsu_Kablosuz_2", TextKind.LIST_CONTENT, pkg = SETTINGS, source = LabelSource.CHILD_TEXT, inList = true),
            el(3, "Cihaz adı, Nuri'nin telefonu", TextKind.LIST_CONTENT, pkg = SETTINGS, source = LabelSource.CHILD_TEXT, inList = true),
            el(4, "Karanlık mod", TextKind.UI_LABEL, pkg = SETTINGS, role = ElementRole.SWITCH),
            el(5, "Açık", TextKind.LIST_CONTENT, pkg = SETTINGS, inList = true),
        )
        assertEquals(listOf("Bağlantılar", "Wi-Fi, [METIN_1]", "Cihaz adı, [METIN_2]", "Karanlık mod", "Açık"), labels)
    }

    @Test
    fun trustedSettingsLabelKeepsTextButMasksValuesInside() {
        val labels = masked(
            el(1, "Oturum açan: nuri.ornek@example.org", TextKind.UI_LABEL, pkg = SETTINGS, role = ElementRole.BUTTON),
            el(2, "Yedek numara 0599 000 77 31", TextKind.UI_LABEL, pkg = SETTINGS, role = ElementRole.BUTTON),
        )
        assertEquals(listOf("Oturum açan: [EPOSTA_1]", "Yedek numara [TEL_1]"), labels)
    }

    @Test
    fun chatScreenMasksTitleRowsAndTypedText() {
        val labels = masked(
            // Toolbar title of a chat with someone outside the address book: clickable text, not trusted.
            el(1, "Gülizar Hanım Komşu", TextKind.UI_LABEL, pkg = WHATSAPP, role = ElementRole.BUTTON),
            el(2, "Fadime Örnek, Pazartesi pilav günü, 09:12", TextKind.LIST_CONTENT, pkg = WHATSAPP,
                source = LabelSource.CHILD_TEXT, inList = true),
            el(3, "Sabah sekizde kapıdayım", TextKind.USER_INPUT, pkg = WHATSAPP, role = ElementRole.TEXT_FIELD, editable = true),
            el(4, "Gönder", TextKind.UI_LABEL, pkg = WHATSAPP, role = ElementRole.ICON_BUTTON, source = LabelSource.DESCRIPTION),
            el(5, "Mesaj yazın", TextKind.UI_LABEL, pkg = WHATSAPP, role = ElementRole.TEXT_FIELD, source = LabelSource.HINT, editable = true),
        )
        assertEquals(
            listOf("[METIN_1]", "[KISI_1], [METIN_2], 09:12", "[METIN_3]", "Gönder", "Mesaj yazın"),
            labels,
        )
    }

    @Test
    fun commandAndScreenShareOnePlaceholderTable() {
        val s = session()
        val cmd = (s.maskCommand("nuriyi ara", InputSource.SPEECH) as CommandMaskResult.Masked).text
        val labels = masked(
            el(1, "Nuri Örnek", TextKind.LIST_CONTENT, pkg = WHATSAPP, inList = true),
            el(2, "+90 599 000 77 40", TextKind.LIST_CONTENT, pkg = WHATSAPP, inList = true),
            session = s,
        )
        assertEquals("[KISI_1]'i ara", cmd.text)
        assertEquals(listOf("[KISI_1]", "[TEL_1]"), labels)
        val decoded = s.unmask("[KISI_1] satırındaki [TEL_1] numarası") as UnmaskResult.Restored
        assertEquals("Nuri Örnek satırındaki +90 599 000 77 40 numarası", decoded.text)
    }

    @Test
    fun passwordFieldsAndUnlabeledElementsSendNoText() {
        val secret = el(1, null, TextKind.NONE, pkg = SETTINGS, role = ElementRole.PASSWORD_FIELD).copy(secret = true)
        assertEquals(listOf(""), masked(secret))
    }

    @Test
    fun appsOutsideTheAllowListAndHiddenAppNamesAreDropped() {
        val result = session().maskScreen(
            snapshot(
                el(1, "Kur'an-ı Kerim Meali", TextKind.OTHER_TEXT, pkg = "com.example.notallowed"),
                el(2, "Şeker Günlüğüm", TextKind.UI_LABEL, pkg = LAUNCHER, role = ElementRole.ICON_BUTTON),
                el(3, "Saat", TextKind.UI_LABEL, pkg = LAUNCHER, role = ElementRole.ICON_BUTTON),
                el(4, "Bilinmeyen", TextKind.OTHER_TEXT, pkg = null),
            ),
            scope,
        ) as ScreenMaskResult.Masked
        assertEquals(listOf(3), result.screen.elements.map { it.id })
        assertEquals("Saat", result.screen.elements.single().label)
    }

    @Test
    fun verificationCodeAnywhereOnScreenBlocksTheRequest() {
        val s = session()
        val result = s.maskScreen(
            snapshot(
                el(1, "Ayarlar", TextKind.UI_LABEL, pkg = SETTINGS),
                el(2, "Onay şifreniz 7316, kimseye söylemeyin", TextKind.LIST_CONTENT, pkg = WHATSAPP, inList = true),
            ),
            scope,
        )
        assertEquals(ScreenMaskResult.Blocked(BlockReason.GIZLI_BILGI), result)
        assertEquals("a blocked screen numbers nothing", 0, s.table.size)
        assertEquals(
            ScreenMaskResult.Blocked(BlockReason.DOGRULAMA_KODU),
            s.maskScreen(snapshot(el(1, "Tek kullanımlık giriş anahtarı", TextKind.UI_LABEL, pkg = SETTINGS)), scope),
        )
    }

    @Test
    fun auditPassesMaskedOutputAndBuildsTheOnlySendableRequest() {
        val s = session()
        val cmd = (s.maskCommand("fadimeye yarın uğrarım diye yaz", InputSource.SPEECH) as CommandMaskResult.Masked).text
        val screen = (s.maskScreen(
            snapshot(
                el(1, "Fadime Örnek", TextKind.OTHER_TEXT, pkg = WHATSAPP, heading = true),
                el(2, "Papatya Sokak No 41 kapı kodu yok", TextKind.LIST_CONTENT, pkg = WHATSAPP, inList = true),
            ),
            scope,
        ) as ScreenMaskResult.Masked).screen
        val audit = s.audit(cmd, screen)
        assertTrue("$audit", audit is AuditResult.Passed)
        val request = (audit as AuditResult.Passed).request
        assertEquals(cmd, request.command)
        assertEquals(listOf("[KISI_1]", "[METIN_2]"), request.screen!!.elements.map { it.label })
    }

    @Test
    fun auditRejectsDeliberatelyBrokenInput() {
        val s = session()
        s.maskCommand("nuriyi ara", InputSource.SPEECH)
        fun verdict(text: String) = (s.audit(MaskedText(text), null) as? AuditResult.Blocked)?.reason
        assertEquals(BlockReason.REHBER_ADI, verdict("[KISI_1]'i ara, Fadime'ye de söyle"))
        assertEquals(BlockReason.TEL, verdict("[KISI_1] 0599 000 77 52"))
        assertEquals(BlockReason.EPOSTA, verdict("fadime.ornek@example.net"))
        assertEquals(BlockReason.ADRES, verdict("Menekşe Mahallesi Çınar Caddesi"))
        assertEquals(BlockReason.OZEL_TERIM, verdict("insülin kalemi"))
        assertEquals(BlockReason.RAKAM_DIZISI, verdict("abone 4417 2209"))
        assertEquals(BlockReason.YAZIYLA_SAYI, verdict("dokuz yüz yirmi yedi ara"))
        assertEquals(BlockReason.BILINMEYEN_YER_TUTUCU, verdict("[KISI_9]'a yaz"))
        assertEquals(BlockReason.BILINMEYEN_YER_TUTUCU, verdict("[KISI_1 ara"))
        assertEquals(BlockReason.DOGRULAMA_KODU, verdict("kod 4471"))
        assertEquals(null, verdict("[KISI_1]'i saat 19:45'te ara"))
    }

    @Test
    fun auditCatchesAScreenLabelThatSkippedMasking() {
        val s = session()
        val broken = MaskedScreen(
            WHATSAPP,
            listOf(MaskedElement(1, ElementRole.TEXT, "Nuri Örnek: 0599 000 77 63", false, false, false, false, false, true, false)),
            truncated = false,
        )
        assertEquals(AuditResult.Blocked(BlockReason.TEL), s.audit(null, broken))
    }

    @Test
    fun compressedChatRowIsMaskedEndToEnd() {
        val row = RawNode(
            className = "android.widget.LinearLayout", clickable = true, collectionItem = true, bounds = B,
            children = listOf(
                RawNode(className = "android.widget.TextView", text = "Bekir Usta", bounds = B),
                RawNode(className = "android.widget.TextView", text = "Kombi yarın 14:00'te", bounds = B),
                RawNode(className = "android.widget.TextView", text = "Dün", bounds = B),
            ),
        )
        val list = RawNode(className = "androidx.recyclerview.widget.RecyclerView", scrollable = true, bounds = B, children = listOf(row))
        val window = RawWindow(1, WindowKind.APPLICATION, packageName = WHATSAPP, active = true, root = list)
        val snap = ScreenCompressor().compress(listOf(window), OwnWindowFilter("tr.yasliasistan.app", emptySet(), "x"))
        val screen = (session().maskScreen(snap, scope) as ScreenMaskResult.Masked).screen
        val labels = screen.elements.map { it.label }
        assertEquals(WHATSAPP, screen.packageName)
        assertTrue(labels.toString(), "[METIN_1], [METIN_2], Dün" in labels)
        assertFalse(labels.any { "Bekir" in it || "Kombi" in it })
    }

    @Test
    fun maskingTwoHundredElementsIsFast() {
        val many = ContactIndex.build((1..2000).map { ContactName("k$it", "Kişi$it Örnekoğlu$it") })
        val elements = (1..200).map { i ->
            when (i % 4) {
                0 -> el(i, "Bildirimler", TextKind.LIST_CONTENT, pkg = SETTINGS, inList = true)
                1 -> el(i, "Kişi$i Örnekoğlu$i, Akşam görüşürüz, 21:0${i % 10}", TextKind.LIST_CONTENT, pkg = WHATSAPP,
                    source = LabelSource.CHILD_TEXT, inList = true)
                2 -> el(i, "Hesap: kisi$i@example.org", TextKind.UI_LABEL, pkg = SETTINGS, role = ElementRole.BUTTON)
                else -> el(i, "Yeni mesaj yazın", TextKind.USER_INPUT, pkg = WHATSAPP, role = ElementRole.TEXT_FIELD, editable = true)
            }
        }
        val snap = snapshot(*elements.toTypedArray())
        repeat(5) { MaskingSession(many).let { it.audit(null, (it.maskScreen(snap, scope) as ScreenMaskResult.Masked).screen) } }
        val runs = 10
        val start = System.nanoTime()
        repeat(runs) {
            val s = MaskingSession(many)
            val screen = (s.maskScreen(snap, scope) as ScreenMaskResult.Masked).screen
            assertTrue(s.audit(null, screen) is AuditResult.Passed)
        }
        val perScreenMs = (System.nanoTime() - start) / 1_000_000.0 / runs
        println("G-32 timing: ${"%.1f".format(perScreenMs)} ms per 200-element screen incl. audit (JVM)")
        assertTrue("too slow: $perScreenMs ms", perScreenMs < 60.0)
    }

    private fun snapshot(vararg elements: ScreenElement): ScreenSnapshot {
        val windows = elements.mapNotNull { it.packageName }.distinct().mapIndexed { i, p -> ScreenWindow(i + 1, WindowKind.APPLICATION, p, i == 0) }
        return ScreenSnapshot(windows, elements.toList(), truncated = false)
    }

    private fun el(
        id: Int,
        label: String?,
        kind: TextKind,
        pkg: String?,
        role: ElementRole = ElementRole.TEXT,
        source: LabelSource = if (label == null) LabelSource.NONE else LabelSource.TEXT,
        inList: Boolean = false,
        editable: Boolean = false,
        heading: Boolean = false,
    ) = ScreenElement(
        id = id, role = role, label = label, labelSource = source, textKind = kind,
        clickable = role == ElementRole.BUTTON || role == ElementRole.ICON_BUTTON, longClickable = false,
        scrollable = false, editable = editable, checkable = role == ElementRole.SWITCH, checked = false,
        enabled = true, selected = false, focused = false, heading = heading, inListItem = inList,
        secret = false, unlabeledIcon = false, bounds = B, windowId = 1, packageName = pkg,
    )

    private companion object {
        const val WHATSAPP = "com.whatsapp"
        const val SETTINGS = "com.android.settings"
        const val LAUNCHER = "com.sec.android.app.launcher"
        val B = Bounds(0, 0, 100, 50)
    }
}
