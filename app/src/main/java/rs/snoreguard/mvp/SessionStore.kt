package rs.snoreguard.mvp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Private app storage. No raw microphone data, recordings, or network uploads. */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("snoreguard_sessions", Context.MODE_PRIVATE)
    private val lock = Any()

    fun begin() = synchronized(lock) {
        val active = JSONObject()
            .put("start", System.currentTimeMillis())
            .put("end", 0L)
            .put("snoreFrames", 0)
            // New sessions sample overlapping 975 ms windows every 250 ms.
            // Old history without hopMs still uses its original 975 ms unit.
            .put("hopMs", 250)
            .put("detectorVersion", "breaths_v1")
            .put("episodes", 0)
            .put("alerts", 0)
            .put("notifications", 0)
            .put("lastScore", 0.0)
            .put("modelActive", false)
        prefs.edit().putString("active", active.toString()).putString("error", "").apply()
    }

    fun update(engine: SnoreEngine, lastScore: Float, modelActive: Boolean) = synchronized(lock) {
        val active = activeOrNull() ?: return@synchronized
        active.put("snoreFrames", engine.snoreFrames)
            .put("episodes", engine.episodes)
            .put("alerts", engine.alerts)
            .put("lastScore", lastScore.toDouble())
            .put("modelActive", modelActive)
        prefs.edit().putString("active", active.toString()).apply()
    }

    /** Actual posted notifications (up to three per confirmed alert series). */
    fun incrementNotifications() = synchronized(lock) {
        val active = activeOrNull() ?: return@synchronized
        active.put("notifications", active.optInt("notifications") + 1)
        prefs.edit().putString("active", active.toString()).apply()
    }

    fun fail(message: String) = synchronized(lock) {
        prefs.edit().putString("error", message.take(220)).apply()
    }

    fun finish() = synchronized(lock) {
        val active = activeOrNull() ?: return@synchronized
        active.put("end", System.currentTimeMillis())
        val history = safeArray(prefs.getString("history", "[]"))
        val merged = JSONArray()
        merged.put(active)
        for (i in 0 until minOf(29, history.length())) merged.put(history.getJSONObject(i))
        prefs.edit().remove("active").putString("history", merged.toString()).commit()
    }

    fun recoverInterrupted() = synchronized(lock) {
        // Called only when the service is not alive. An interrupted process must not
        // be presented as an ongoing microphone recording.
        if (activeOrNull() != null) {
            prefs.edit().putString("error", "Prethodno praćenje je prekinuo sistem ili je telefon restartovan.").apply()
            finish()
        }
    }

    fun active(): JSONObject? = synchronized(lock) { activeOrNull()?.let { JSONObject(it.toString()) } }
    fun history(): JSONArray = synchronized(lock) { safeArray(prefs.getString("history", "[]")) }
    fun error(): String = synchronized(lock) { prefs.getString("error", "") ?: "" }

    private fun activeOrNull(): JSONObject? = try {
        prefs.getString("active", null)?.let { JSONObject(it) }
    } catch (_: Exception) { null }

    private fun safeArray(raw: String?): JSONArray = try {
        JSONArray(raw ?: "[]")
    } catch (_: Exception) { JSONArray() }
}
