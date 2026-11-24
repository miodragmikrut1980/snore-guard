package rs.snoreguard.mvp

/** Nighttime 22:00-08:00, in chronological order across midnight. */
object NightHours {
    val orderedHours: List<Int> = listOf(22, 23, 0, 1, 2, 3, 4, 5, 6, 7)
    fun indexOf(hour: Int): Int = orderedHours.indexOf(hour)
    fun labels(): List<String> = orderedHours.map { "%02d".format(java.util.Locale.ROOT, it) }
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
