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

    companion object {
        const val MIN_RMS = 0.0025f // Suppress near-silence only.
    }
}
