package tr.yasliasistan.speech.whisper

/**
 * Device-dependent choices for whisper.cpp (NFG-72: must run on weak phones too). Pure logic.
 */
object DeviceProfile {

    /** Native library built for ARMv8.2 + dotprod + fp16 (faster on most phones since ~2019). */
    const val FAST_LIBRARY = "whisper_v82"

    /** Baseline ARMv8.0 library: runs on every arm64 phone. */
    const val BASE_LIBRARY = "whisper_v80"

    /** Feature words from the "Features" lines of /proc/cpuinfo (lowest common set over all cores). */
    fun cpuFeatures(cpuinfo: String): Set<String> {
        val perCore = cpuinfo.lineSequence()
            .filter { it.substringBefore(':').trim().equals("Features", ignoreCase = true) }
            .map { line -> line.substringAfter(':').trim().split(Regex("\\s+")).filter(String::isNotEmpty).toSet() }
            .toList()
        if (perCore.isEmpty()) return emptySet()
        return perCore.reduce { acc, set -> acc intersect set }
    }

    /** dotprod ("asimddp") and half-precision SIMD ("asimdhp") are what the fast library needs. */
    fun libraryFor(features: Set<String>): String =
        if ("asimddp" in features && "asimdhp" in features) FAST_LIBRARY else BASE_LIBRARY

    /**
     * Decoding threads: half of the cores, between 1 and 4. On big.LITTLE phones (e.g. Helio G99:
     * 2 big + 6 little) more threads than this mostly add heat and contention. G-14 may tune it.
     */
    fun threadsFor(cpuCount: Int): Int = (cpuCount / 2).coerceIn(1, MAX_THREADS)

    private const val MAX_THREADS = 4
}
