package rs.snoreguard.mvp

/** Weighted rate avoids comparing raw totals from different monitoring durations. */
object HistoryTrends {
    data class Night(val start: Long, val sensitivity: String, val events: Int,
        val analysedHours: Double, val coverage: Double)
    data class Trend(val sessions: Int, val events: Long, val hours: Double) {
        val eventsPerHour: Double? get() = if (hours > 0) events / hours else null
    }
    fun summarize(nights: List<Night>, from: Long, through: Long, sensitivity: String): Trend {
        val valid = nights.filter {
            it.start in from..through && it.sensitivity == sensitivity &&
            it.events >= 0 && it.analysedHours.isFinite() && it.analysedHours >= 0.5 &&
            it.coverage.isFinite() && it.coverage in 0.8..1.0
        }
        return Trend(valid.size, valid.sumOf { it.events.toLong() }, valid.sumOf { it.analysedHours })
    }
}
