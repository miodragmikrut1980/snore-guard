package rs.snoreguard.mvp

/** Nighttime 22:00-08:00, in chronological order across midnight. */
object NightHours {
    val orderedHours: List<Int> = listOf(22, 23, 0, 1, 2, 3, 4, 5, 6, 7)
    fun indexOf(hour: Int): Int = orderedHours.indexOf(hour)
    fun labels(): List<String> = orderedHours.map { "%02d".format(java.util.Locale.ROOT, it) }
    fun allLabels(): List<String> = (0..23).map { "%02d".format(java.util.Locale.ROOT, it) }

    /** Includes partial start/end clock hours for daytime and cross-midnight windows. */
    fun labelsForWindow(startMinutes: Int, endMinutes: Int): List<String> {
        require(startMinutes in 0..1439 && endMinutes in 0..1439)
        val length = (endMinutes - startMinutes + 1440) % 1440
        val duration = if (length == 0) 1440 else length
        val count = ((startMinutes % 60 + duration + 59) / 60).coerceIn(1, 24)
        return (0 until count).map {
            "%02d".format(java.util.Locale.ROOT, ((startMinutes / 60) + it) % 24)
        }
    }
}

/** RFC 4180-style quoting with protection against spreadsheet formula injection. */
object CsvCell {
    fun encode(value: String): String {
        val trimmed = value.trimStart()
        val protected = if (trimmed.firstOrNull() in listOf('=', '+', '-', '@')) "'$value" else value
        return "\"" + protected.replace("\"", "\"\"") + "\""
    }
    fun row(values: List<String>): String = values.joinToString(",") { encode(it) }
}
