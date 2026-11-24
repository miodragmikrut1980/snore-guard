package rs.snoreguard.mvp

/** Monotonic AI diagnostic; slow intervals do not prove missed watch alerts. */
class AnalysisHeartbeat(private val thresholdMs: Long = 5_000L) {
    init { require(thresholdMs > 0) }
    var maxGapMillis: Long = 0L
        private set
    var stalls: Int = 0
        private set
    private var previousAt: Long? = null

    fun record(completedAtElapsedMillis: Long) {
        require(completedAtElapsedMillis >= 0)
        previousAt?.let { previous ->
            require(completedAtElapsedMillis >= previous) { "Inference time must be monotonic" }
            val gap = completedAtElapsedMillis - previous
            maxGapMillis = maxOf(maxGapMillis, gap)
            if (gap > thresholdMs) stalls++
        }
        previousAt = completedAtElapsedMillis
    }
}
