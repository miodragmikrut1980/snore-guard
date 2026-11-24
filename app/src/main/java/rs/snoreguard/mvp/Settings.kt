package rs.snoreguard.mvp

import android.content.Context

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("snoreguard_settings", Context.MODE_PRIVATE)

    var phoneVibration: Boolean
        get() = prefs.getBoolean("phone_vibration", false)
        set(value) { prefs.edit().putBoolean("phone_vibration", value).apply() }

    companion object {
        const val MODEL_THRESHOLD = 0.07f // HIGH sensitivity; must be calibrated on real recordings.
        const val MIN_RMS = 0.0025f // Suppress near-silence only.
    }
}
