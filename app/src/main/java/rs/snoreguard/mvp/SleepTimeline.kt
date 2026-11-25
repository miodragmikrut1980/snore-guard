package rs.snoreguard.mvp

/** Epoch seconds are deliberately bounded: full hourly counters remain independent. */
object SleepTimeline {
    const val SNORE_LIMIT = 256
    const val ALERT_LIMIT = 128
    const val MAX_SECONDS = 172800
    data class Stage(val start: Long, val end: Long, val kind: Kind)
    enum class Kind(val label: String) {
        AWAKE("Budan"), REM("REM"), LIGHT("Lagan"), DEEP("Dubok"),
        SLEEPING("San bez faze"), UNKNOWN("Nepoznato")
    }
    data class Night(val start: Long, val end: Long, val source: String, val stages: List<Stage>)
    fun overlap(start: Long, end: Long, night: Night): Long =
        (minOf(end, night.end) - maxOf(start, night.start)).coerceAtLeast(0L)
    fun append(raw: String, offsetSeconds: Long, limit: Int): String {
        if (offsetSeconds !in 0L..MAX_SECONDS.toLong()) return raw
        if (raw.isNotEmpty() && raw.count { it == ',' } + 1 >= limit) return raw
        val last = raw.substringAfterLast(',').toLongOrNull()
        if (last != null && offsetSeconds < last) return raw
        return if (raw.isEmpty()) offsetSeconds.toString() else "$raw,$offsetSeconds"
    }
    fun parse(raw: String, limit: Int): List<Int>? {
        if (raw.length > limit * 7) return null
        if (raw.isEmpty()) return emptyList()
        val entries = raw.split(',')
        if (entries.size > limit) return null
        val values = entries.map { it.toIntOrNull() ?: return null }
        if (values.any { it !in 0..MAX_SECONDS } || values.zipWithNext().any { it.first > it.second }) return null
        return values
    }
    /** A second-wide marker spanning a stage boundary cannot be assigned a phase. */
    fun kindAt(epochMs: Long, stages: List<Stage>): Kind {
        val matching = stages.filter { it.start <= epochMs && it.end >= epochMs + 1000 }
        return matching.singleOrNull()?.kind ?: Kind.UNKNOWN
    }
    fun durationByKind(night: Night): Map<Kind, Long> {
        val stages = night.stages.filter { it.end > it.start && it.start >= night.start && it.end <= night.end }
            .sortedBy { it.start }
        if (stages.zipWithNext().any { it.first.end > it.second.start })
            return mapOf(Kind.UNKNOWN to (night.end - night.start).coerceAtLeast(0L))
        val totals = stages.groupBy { it.kind }.mapValues { (_, list) -> list.sumOf { it.end - it.start } }.toMutableMap()
        val missing = ((night.end - night.start) - totals.values.sum()).coerceAtLeast(0L)
        totals[Kind.UNKNOWN] = (totals[Kind.UNKNOWN] ?: 0L) + missing
        return totals
    }
}
