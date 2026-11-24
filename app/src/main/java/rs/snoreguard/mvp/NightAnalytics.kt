package rs.snoreguard.mvp

import kotlin.math.roundToInt

/** Descriptive detector metrics, never a clinical score or measured sound intensity. */
object NightAnalytics {
    data class Result(val analysedHours: Double, val coverage: Double,
        val eventsPerHour: Double, val positivePercent: Double, val score: Int?)

    fun calculate(frames: Int, positives: Int, events: Int, hopMs: Int,
                  elapsedMs: Long): Result? {
        if (frames <= 0 || positives !in 0..frames || events !in 0..frames ||
            hopMs != 250 || elapsedMs <= 0) return null
        val analysedMs = frames.toLong() * hopMs
        // Reject nonsensical imported counters, allow initial-window rounding.
        if (analysedMs > elapsedMs + 1000L) return null
        val coverage = (analysedMs.toDouble() / elapsedMs).coerceIn(0.0, 1.0)
        val hours = analysedMs / 3_600_000.0
        val positivePercent = 100.0 * positives / frames
        // A transparent index: percent of AI windows positive, rounded.
        // No thresholds for medical severity, no loudness or sleep-duration claims.
        val score = if (analysedMs >= 1_800_000L && coverage >= 0.80)
            positivePercent.roundToInt() else null
        return Result(hours, coverage, events / hours, positivePercent, score)
    }

    data class Review(val snore: Int, val falseAlarm: Int, val uncertain: Int, val unreviewed: Int) {
        val reviewed: Int get() = snore + falseAlarm
        val confirmedPercent: Int? get() = if (reviewed == 0) null
            else (100.0 * snore / reviewed).roundToInt()
    }

    fun reviews(labels: List<ClipLabel>): Review = Review(
        labels.count { it == ClipLabel.SNORE }, labels.count { it == ClipLabel.FALSE_ALARM },
        labels.count { it == ClipLabel.UNCERTAIN }, labels.count { it == ClipLabel.UNREVIEWED })
}
