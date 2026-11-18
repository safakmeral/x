package tr.yasliasistan.app.embedding

import android.os.Build
import android.os.Debug
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import tr.yasliasistan.core.embedding.E5TextEmbedder
import tr.yasliasistan.core.embedding.SimilarityProbe
import tr.yasliasistan.core.embedding.XlmRobertaTokenizer
import java.io.File
import java.util.Locale

/**
 * G-43 [Cihaz] measurement (run with tools/gomme-olcum.ps1): model size, first load, query
 * latency (1/2/4 threads), peak memory (PSS) and the Turkish pair similarity check. Uses only the
 * fixed sentences in assets/gomme/ciftler.tsv; writes numbers to
 * Android/data/tr.yasliasistan.app/files/gomme-sonuc/<run>/ for the script to pull.
 *
 * Instrumentation arguments: run (folder name), threads ("1,2,4"), repeats (3).
 */
@RunWith(AndroidJUnit4::class)
class EmbeddingBenchmarkTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val args = InstrumentationRegistry.getArguments()
    private val app = instrumentation.targetContext
    private val lines = mutableListOf<String>()

    private fun record(key: String, value: Any) {
        lines += "$key=$value"
    }

    private fun pssMb() = Debug.getPss() / 1024.0

    @Test
    fun measure() {
        val dir = app.getExternalFilesDir(EmbeddingModelFiles.FOLDER)!!
        assumeTrue("Model yok: $dir (tools/gomme-olcum.ps1 modeli yükler)", EmbeddingModelFiles.isInstalled(dir))
        val threads = (args.getString("threads") ?: "1,2,4").split(',').map { it.trim().toInt() }
        val repeats = (args.getString("repeats") ?: "3").toInt()
        val pairs = SimilarityProbe.parse(
            instrumentation.context.assets.open("gomme/ciftler.tsv").bufferedReader().readText(),
        )
        val texts = pairs.flatMap { listOf(it.a, it.b) }.distinct()

        record("device", "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})")
        record("model_bytes", File(dir, EmbeddingModelFiles.MODEL.fileName).length())
        record("tokenizer_bytes", File(dir, EmbeddingModelFiles.TOKENIZER.fileName).length())
        record("texts", texts.size)

        System.gc()
        val pssStart = pssMb()
        val heapBefore = usedHeapMb()
        var t0 = SystemClock.elapsedRealtimeNanos()
        val tokenizer = XlmRobertaTokenizer.load(File(dir, EmbeddingModelFiles.TOKENIZER.fileName))
        record("tokenizer_load_ms", ms(t0))
        record("tokenizer_heap_mb", fmt(usedHeapMb() - heapBefore))
        record("token_counts_max", texts.maxOf { tokenizer.encode(E5TextEmbedder.QUERY_PREFIX + it).size })

        var peak = pssMb()
        var lastEmbedder: E5TextEmbedder? = null
        for (n in threads) {
            lastEmbedder?.close()
            t0 = SystemClock.elapsedRealtimeNanos()
            val embedder = E5TextEmbedder(tokenizer, OnnxTokenEncoder(File(dir, EmbeddingModelFiles.MODEL.fileName), n))
            record("t$n.session_load_ms", ms(t0))
            t0 = SystemClock.elapsedRealtimeNanos()
            embedder.embed(texts.first())
            record("t$n.first_query_ms", ms(t0))
            val latencies = mutableListOf<Double>()
            repeat(repeats) {
                for (text in texts) {
                    t0 = SystemClock.elapsedRealtimeNanos()
                    embedder.embed(text)
                    latencies += (SystemClock.elapsedRealtimeNanos() - t0) / 1e6
                }
                peak = maxOf(peak, pssMb())
            }
            latencies.sort()
            record("t$n.median_ms", fmt(latencies[latencies.size / 2]))
            record("t$n.p95_ms", fmt(latencies[(latencies.size * 95 / 100).coerceAtMost(latencies.size - 1)]))
            record("t$n.max_ms", fmt(latencies.last()))
            lastEmbedder = embedder
        }
        record("pss_start_mb", fmt(pssStart))
        record("pss_peak_mb", fmt(peak))
        record("pss_added_peak_mb", fmt(peak - pssStart))

        val embedder = lastEmbedder!!
        val scored = SimilarityProbe.score(pairs) { embedder.embed(it) }
        val s = SimilarityProbe.summarize(scored, DEFAULT_THRESHOLD)
        embedder.close()
        System.gc()
        record("pss_after_close_mb", fmt(pssMb()))
        record("same_min", fmt(s.sameMin))
        record("same_mean", fmt(s.sameMean))
        record("different_max", fmt(s.differentMax))
        record("different_mean", fmt(s.differentMean))
        record("accuracy_at_0_80", fmt(s.accuracyAtDefault))
        record("best_threshold", fmt(s.bestThreshold))
        record("best_accuracy", fmt(s.bestAccuracy))
        record("safe_threshold", fmt(s.safeThreshold))
        record("same_accepted_at_safe", "${s.sameAcceptedAtSafe}/${scored.count { it.pair.same }}")

        val out = File(app.getExternalFilesDir("gomme-sonuc"), args.getString("run") ?: "son").apply { mkdirs() }
        File(out, "sonuc.txt").writeText(lines.joinToString("\n", postfix = "\n"))
        File(out, "ciftler.tsv").writeText(
            scored.joinToString("\n", postfix = "\n") { "${if (it.pair.same) "AYNI" else "FARKLI"}\t${fmt(it.cosine)}\t${it.pair.a}\t${it.pair.b}" },
        )
    }

    private fun usedHeapMb(): Double {
        System.gc()
        val rt = Runtime.getRuntime()
        return (rt.totalMemory() - rt.freeMemory()) / 1_048_576.0
    }

    private fun ms(start: Long) = fmt((SystemClock.elapsedRealtimeNanos() - start) / 1e6)

    private fun fmt(v: Number) = String.format(Locale.ROOT, "%.3f", v.toDouble())

    private companion object {
        /** V-03 approved value, checked here. */
        const val DEFAULT_THRESHOLD = 0.80f
    }
}
