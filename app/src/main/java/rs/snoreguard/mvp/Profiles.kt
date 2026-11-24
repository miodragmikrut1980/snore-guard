package rs.snoreguard.mvp

/** Configurable detection threshold (experimental, not a clinical confidence value). */
enum class Sensitivity(val title: String, val modelThreshold: Float) {
    LOW("Niska — manje lažnih alarma", 0.16f),
    NORMAL("Normalna — uravnotežena", 0.10f),
    HIGH("Visoka — hvata tiše zvuke", 0.07f);

    companion object {
        fun fromStored(value: String?): Sensitivity =
            values().firstOrNull { it.name == value } ?: HIGH
    }
}

enum class AlertMode(val title: String, val steps: Int, val intervalMillis: Long) {
    GENTLE("Blagi — 1 obaveštenje", 1, 16_000L),
    NORMAL("Normalni — 2 obaveštenja", 2, 16_000L),
    STRONG("Intenzivni — 3 obaveštenja", 3, 16_000L);

    companion object {
        fun fromStored(value: String?): AlertMode =
            values().firstOrNull { it.name == value } ?: STRONG
    }
}
