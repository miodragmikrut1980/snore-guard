package rs.snoreguard.mvp

import android.content.Context

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("snoreguard_settings", Context.MODE_PRIVATE)

    var phoneVibration: Boolean
        get() = prefs.getBoolean("phone_vibration", false)
        set(value) { prefs.edit().putBoolean("phone_vibration", value).apply() }

    var sensitivity: Sensitivity
        get() = Sensitivity.fromStored(prefs.getString("sensitivity", null))
        set(value) { prefs.edit().putString("sensitivity", value.name).apply() }

    var alertMode: AlertMode
        get() = AlertMode.fromStored(prefs.getString("alert_mode", null))
        set(value) { prefs.edit().putString("alert_mode", value.name).apply() }

    /** Local time, minute of day. Editing the window affects the next start. */
    var windowStartMinutes: Int
        get() = prefs.getInt("window_start", 22 * 60).coerceIn(0, 1439)
        set(value) {
            require(value in 0..1439)
            prefs.edit().putInt("window_start", value).apply()
        }

    var windowEndMinutes: Int
        get() = prefs.getInt("window_end", 8 * 60).coerceIn(0, 1439)
        set(value) {
            require(value in 0..1439)
            prefs.edit().putInt("window_end", value).apply()
        }

    /** OFF by default. Enables short WAV evidence clips, not continuous recording. */
    var saveSnoreClips: Boolean
        get() = prefs.getBoolean("save_snore_clips", false)
        set(value) { prefs.edit().putBoolean("save_snore_clips", value).apply() }

    fun windowStart(): java.time.LocalTime =
        java.time.LocalTime.of(windowStartMinutes / 60, windowStartMinutes % 60)

    fun windowEnd(): java.time.LocalTime =
        java.time.LocalTime.of(windowEndMinutes / 60, windowEndMinutes % 60)

    companion object {
        fun timeLabel(minutes: Int): String =
            String.format(java.util.Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60)

        const val MIN_RMS = 0.0025f // Suppress near-silence only.
    }
}
