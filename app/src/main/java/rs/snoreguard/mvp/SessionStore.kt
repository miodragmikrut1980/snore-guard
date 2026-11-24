package rs.snoreguard.mvp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Private app storage. No raw microphone data, recordings, or network uploads. */
class SessionStore(context: Context) {
    companion object {
        const val MAX_HISTORY = 365
        const val MAX_BACKUP_BYTES = 2_000_000
    }
    private val appContext = context.applicationContext
    private val prefs = context.getSharedPreferences("snoreguard_sessions", Context.MODE_PRIVATE)
    private val lock = Any()

    fun begin(
        sensitivity: Sensitivity = Sensitivity.HIGH,
        mode: AlertMode = AlertMode.STRONG,
        windowStart: Int = 22 * 60,
        windowEnd: Int = 8 * 60,
        isTest: Boolean = false
    ) = synchronized(lock) {
        val active = JSONObject()
            .put("start", System.currentTimeMillis())
            .put("end", 0L)
            .put("snoreFrames", 0)
            // New sessions sample overlapping 975 ms windows every 250 ms.
            // Old history without hopMs still uses its original 975 ms unit.
            .put("hopMs", 250)
            .put("detectorVersion", "breaths_v1")
            .put("sensitivity", sensitivity.name)
            .put("threshold", sensitivity.modelThreshold.toDouble())
            .put("alertMode", mode.name)
            .put("analysedFrames", 0)
            .put("lastUpdate", System.currentTimeMillis())
            .put("stopReason", "")
            .put("timelineVersion", 1)
            .put("hourly", JSONObject())
            .put("windowStart", windowStart)
            .put("windowEnd", windowEnd)
            .put("isTest", isTest)
            .put("episodes", 0)
            .put("alerts", 0)
            .put("notifications", 0)
            .put("lastScore", 0.0)
            .put("modelActive", false)
        prefs.edit().putString("active", active.toString()).putString("error", "").apply()
    }

    fun update(engine: SnoreEngine, lastScore: Float, modelActive: Boolean) = synchronized(lock) {
        val active = activeOrNull() ?: return@synchronized
        active.put("analysedFrames", engine.analysedFrames)
            .put("lastUpdate", System.currentTimeMillis())
            .put("snoreFrames", engine.snoreFrames)
            .put("episodes", engine.episodes)
            .put("alerts", engine.alerts)
            .put("lastScore", lastScore.toDouble())
            .put("modelActive", modelActive)
        prefs.edit().putString("active", active.toString()).apply()
    }

    /**
     * Only DISTINCT snore events are counted (not overlapping AI windows).
     * Local clock hour labels are from 22:00 to 07:59. No raw audio is saved.
     */
    fun recordSnoreAt(timestampMillis: Long) = synchronized(lock) {
        val active = activeOrNull() ?: return@synchronized
        val hour = Calendar.getInstance().apply { timeInMillis = timestampMillis }
            .get(Calendar.HOUR_OF_DAY)
        val histogram = active.optJSONObject("hourly") ?: JSONObject()
        val key = String.format(Locale.ROOT, "%02d", hour)
        histogram.put(key, histogram.optInt(key, 0) + 1)
        active.put("hourly", histogram)
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

    fun finish(reason: String = "Završeno") = synchronized(lock) {
        val active = activeOrNull() ?: return@synchronized
        active.put("end", System.currentTimeMillis())
            .put("stopReason", reason)
        if (active.optBoolean("isTest", false)) {
            prefs.edit().remove("active").commit()
            return@synchronized
        }
        val history = safeArray(prefs.getString("history", "[]"))
        val merged = JSONArray()
        merged.put(active)
        for (i in 0 until minOf(MAX_HISTORY - 1, history.length())) merged.put(history.getJSONObject(i))
        prefs.edit().remove("active").putString("history", merged.toString()).commit()
    }

    fun recoverInterrupted() = synchronized(lock) {
        // Called only when the service is not alive. An interrupted process must not
        // be presented as an ongoing microphone recording.
        if (activeOrNull() != null) {
            prefs.edit().putString("error", "Prethodno praćenje je prekinuo sistem ili je telefon restartovan.").apply()
            finish("Sistem je prekinuo praćenje")
        }
    }

    fun active(): JSONObject? = synchronized(lock) { activeOrNull()?.let { JSONObject(it.toString()) } }
    fun history(): JSONArray = synchronized(lock) { safeArray(prefs.getString("history", "[]")) }
    fun error(): String = synchronized(lock) { prefs.getString("error", "") ?: "" }

    /**
     * Export only finished sessions plus user preferences. The active microphone
     * session is not backed up, and no audio or private files leave the device.
     */
    fun exportBackup(): String = synchronized(lock) {
        val settings = Settings(appContext)
        JSONObject()
            .put("app", "SnoreGuard")
            .put("schema", 1)
            .put("createdAt", System.currentTimeMillis())
            .put("history", history())
            .put("settings", JSONObject()
                .put("sensitivity", settings.sensitivity.name)
                .put("alertMode", settings.alertMode.name)
                .put("phoneVibration", settings.phoneVibration)
                .put("windowStart", settings.windowStartMinutes)
                .put("windowEnd", settings.windowEndMinutes))
            .toString(2)
    }

    /**
     * Merge imported completed sessions with local ones. Never delete valid
     * local sessions to make space for older imported entries. Up to 365 newest
     * unique records are retained, matching the existing history limit.
     * Validation completes before any persistent settings are changed.
     */
    fun importBackup(raw: String): Int = synchronized(lock) {
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_BYTES) {
            "Rezervna kopija je prevelika."
        }
        val root = JSONObject(raw)
        require(root.optString("app") == "SnoreGuard" && root.optInt("schema") == 1) {
            "Nepodržan format rezervne kopije."
        }
        val incoming = root.optJSONArray("history")
            ?: throw IllegalArgumentException("Nedostaje istorija.")
        require(incoming.length() <= MAX_HISTORY) { "Previše sesija u rezervnoj kopiji." }
        val received = mutableListOf<JSONObject>()
        for (i in 0 until incoming.length()) {
            val obj = incoming.optJSONObject(i)
                ?: throw IllegalArgumentException("Neispravna sesija na poziciji ${i + 1}.")
            val start = obj.optLong("start")
            val end = obj.optLong("end")
            require(start > 0 && end >= start && end - start <= 48L * 60 * 60 * 1000) {
                "Neispravno vreme sesije ${i + 1}."
            }
            require(obj.optInt("episodes") >= 0 && obj.optInt("alerts") >= 0) {
                "Neispravni brojači sesije ${i + 1}."
            }
            val histogram = obj.optJSONObject("hourly")
            if (histogram != null) {
                for (hour in NightHours.allLabels()) {
                    require(histogram.optInt(hour, 0) in 0..1_000_000) {
                        "Neispravan satni podatak."
                    }
                }
            }
            received.add(JSONObject(obj.toString()))
        }
        val preferences = root.optJSONObject("settings")
        val sensitivityName = preferences?.optString("sensitivity")
        val modeName = preferences?.optString("alertMode")
        val startMinutes = preferences?.optInt("windowStart", 22 * 60) ?: 22 * 60
        val endMinutes = preferences?.optInt("windowEnd", 8 * 60) ?: 8 * 60
        require(startMinutes in 0..1439 && endMinutes in 0..1439) {
            "Neispravno vreme u rezervnoj kopiji."
        }
        require(sensitivityName == null || Sensitivity.values().any { it.name == sensitivityName }) {
            "Nepoznata osetljivost u kopiji."
        }
        require(modeName == null || AlertMode.values().any { it.name == modeName }) {
            "Nepoznat režim alarma u kopiji."
        }

        val merged = mutableListOf<JSONObject>()
        val seen = HashSet<String>()
        val local = history()
        // Local history takes precedence when a session is present in both sets.
        for (i in 0 until local.length()) {
            val entry = local.optJSONObject(i) ?: continue
            val key = "${entry.optLong("start")}:${entry.optLong("end")}"
            if (seen.add(key)) merged.add(entry)
        }
        val addedKeys = HashSet<String>()
        for (entry in received) {
            val key = "${entry.optLong("start")}:${entry.optLong("end")}"
            if (seen.add(key)) {
                merged.add(entry)
                addedKeys.add(key)
            }
        }
        merged.sortByDescending { it.optLong("start") }
        val retained = merged.take(MAX_HISTORY)
        val imported = retained.count {
            "${it.optLong("start")}:${it.optLong("end")}" in addedKeys
        }
        val compact = JSONArray()
        retained.forEach { compact.put(it) }
        check(prefs.edit().putString("history", compact.toString()).commit()) {
            "Čuvanje rezervne kopije nije uspelo."
        }
        if (preferences != null) {
            val settings = Settings(appContext)
            if (sensitivityName != null) settings.sensitivity = Sensitivity.fromStored(sensitivityName)
            if (modeName != null) settings.alertMode = AlertMode.fromStored(modeName)
            if (preferences.has("windowStart")) settings.windowStartMinutes = startMinutes
            if (preferences.has("windowEnd")) settings.windowEndMinutes = endMinutes
            if (preferences.has("phoneVibration")) {
                settings.phoneVibration = preferences.optBoolean("phoneVibration")
            }
        }
        imported
    }

    fun exportCsv(): String = synchronized(lock) {
        val columns = listOf(
            "start_local", "end_local", "detector_version", "sensitivity",
            "recognized_events", "alarm_series", "posted_notifications",
            "estimated_snoring_seconds", "stop_reason"
        ) + NightHours.allLabels().map { "hour_${it}_events" }
        val text = StringBuilder().append(CsvCell.row(columns)).append("\r\n")
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ROOT)
        val records = history()
        for (i in 0 until records.length()) {
            val item = records.optJSONObject(i) ?: continue
            val start = item.optLong("start")
            val end = item.optLong("end")
            val hist = item.optJSONObject("hourly")
            val seconds = item.optLong("snoreFrames") * item.optLong("hopMs", 975) / 1000
            val values = listOf(
                format.format(Date(start)),
                if (end > 0) format.format(Date(end)) else "",
                item.optString("detectorVersion"),
                item.optString("sensitivity"),
                item.optInt("episodes").toString(),
                item.optInt("alerts").toString(),
                item.optInt("notifications").toString(),
                seconds.toString(),
                item.optString("stopReason")
            ) + NightHours.allLabels().map { hour ->
                if (hist == null) "" else hist.optInt(hour, 0).toString()
            }
            text.append(CsvCell.row(values)).append("\r\n")
        }
        text.toString()
    }

    private fun activeOrNull(): JSONObject? = try {
        prefs.getString("active", null)?.let { JSONObject(it) }
    } catch (_: Exception) { null }

    private fun safeArray(raw: String?): JSONArray = try {
        JSONArray(raw ?: "[]")
    } catch (_: Exception) { JSONArray() }
}
