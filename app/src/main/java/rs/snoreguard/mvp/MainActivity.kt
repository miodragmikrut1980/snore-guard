package rs.snoreguard.mvp

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.LocalTime
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var store: SessionStore
    private lateinit var settings: Settings
    private lateinit var startStop: Button
    private lateinit var test: Button
    private lateinit var testBurst: Button
    private lateinit var testBurstStatus: TextView
    private lateinit var manualBurst: AlarmBurst
    private lateinit var status: TextView
    private lateinit var description: TextView
    private lateinit var confidence: TextView
    private lateinit var sessionTime: TextView
    private lateinit var episodeStats: TextView
    private lateinit var snoreStats: TextView
    private lateinit var alertStats: TextView
    private lateinit var history: TextView
    private lateinit var errors: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var pendingAction: String? = null
    private val dateFormat = SimpleDateFormat("dd.MM. HH:mm", Locale.forLanguageTag("sr-RS"))
    private val updater = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1_500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Show the version installed on THIS phone, never a manually hard-coded label.
        val installedPackage = packageManager.getPackageInfo(packageName, 0)
        val installedVersion = installedPackage.versionName ?: "?"
        val installedBuild = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            installedPackage.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            installedPackage.versionCode.toLong()
        }
        findViewById<TextView>(R.id.versionText).text = "v$installedVersion · build $installedBuild"
        store = SessionStore(this)
        settings = Settings(this)
        if (!MonitorService.isActive) store.recoverInterrupted()
        AlertNotifier.ensureChannels(this)

        startStop = findViewById(R.id.startStopButton)
        test = findViewById(R.id.testButton)
        testBurst = findViewById(R.id.testBurstButton)
        testBurstStatus = findViewById(R.id.testBurstStatus)
        manualBurst = AlarmBurst(
            scheduler = BurstScheduler { delay, action -> handler.postDelayed({ action() }, delay) },
            canContinue = { !isFinishing && !isDestroyed && notificationsEnabled() },
            emit = { step ->
                AlertNotifier.sendWatchAlert(this, test = true, step = step)
                testBurstStatus.text = if (step < 3) {
                    "Test: poslato ${step}/3 • sledeće za 16 s"
                } else "Test završen: 3 odvojena obaveštenja za 32 s"
                if (step == 3) testBurst.isEnabled = true
            }
        )
        status = findViewById(R.id.statusLabel)
        description = findViewById(R.id.liveDescription)
        confidence = findViewById(R.id.liveConfidence)
        sessionTime = findViewById(R.id.sessionTime)
        episodeStats = findViewById(R.id.episodeStats)
        snoreStats = findViewById(R.id.snoreStats)
        alertStats = findViewById(R.id.alertStats)
        history = findViewById(R.id.historyText)
        errors = findViewById(R.id.errorText)

        val vibration = findViewById<Switch>(R.id.phoneVibrationSwitch)
        vibration.isChecked = settings.phoneVibration
        vibration.setOnCheckedChangeListener { _, checked ->
            settings.phoneVibration = checked
        }

        startStop.setOnClickListener {
            if (MonitorService.isActive) {
                startService(Intent(this, MonitorService::class.java).setAction(MonitorService.ACTION_STOP))
                toast("Praćenje se zaustavlja…")
            } else {
                requirePermissionsThen("start")
            }
            refresh()
        }
        test.setOnClickListener { requirePermissionsThen("test") }
        testBurst.setOnClickListener { requirePermissionsThen("test_burst") }
        refresh()
    }

    private fun requirePermissionsThen(action: String) {
        val missing = mutableListOf<String>()
        if (action == "start" && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (missing.isNotEmpty()) {
            pendingAction = action
            requestPermissions(missing.toTypedArray(), REQUEST_PERMISSIONS)
            return
        }
        executeAction(action)
    }

    private fun executeAction(action: String) {
        if (action == "test" || action == "test_burst") {
            if (!notificationsEnabled()) {
                toast("Uključi SnoreGuard obaveštenja u Android podešavanjima.")
                return
            }
            if (action == "test") {
                try {
                    AlertNotifier.sendWatchAlert(this, test = true)
                    toast("Jedno test obaveštenje je poslato.")
                } catch (ex: Exception) {
                    toast("Test nije uspeo: ${ex.message}")
                }
            } else if (!manualBurst.isRunning) {
                testBurst.isEnabled = false
                testBurstStatus.text = "Počinje test: 0 s / 16 s / 32 s"
                if (!manualBurst.start()) {
                    testBurst.isEnabled = true
                    testBurstStatus.text = "Test nije pokrenut: proveri dozvole i obaveštenja."
                }
            }
            return
        }
        if (action == "start") {
            if (!NightWindow.isNight(LocalTime.now())) {
                toast("Noćni režim: praćenje može da počne od 22:00 do 08:00.")
                return
            }
            val manager = getSystemService(android.app.NotificationManager::class.java)
            if (!manager.areNotificationsEnabled()) {
                toast("Obaveštenja moraju biti uključena za GT 2 alarm.")
                return
            }
            try {
                startForegroundService(Intent(this, MonitorService::class.java).setAction(MonitorService.ACTION_START))
                toast("Pokrećem lokalno praćenje hrkanja.")
            } catch (ex: Exception) {
                toast("Praćenje nije pokrenuto: ${ex.message}")
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            val action = pendingAction
            pendingAction = null
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED } && action != null) {
                executeAction(action)
            } else {
                toast("Dozvole nisu date. Mikrofon i obaveštenja su potrebni za praćenje.")
            }
        }
    }

    private fun notificationsEnabled(): Boolean =
        getSystemService(android.app.NotificationManager::class.java).areNotificationsEnabled()

    private fun refresh() {
        val running = MonitorService.isActive
        startStop.text = if (running) "Zaustavi praćenje" else "Pokreni noćno praćenje"
        status.text = if (running) "● Praćenje je aktivno" else "● Praćenje nije aktivno"
        description.text = if (running) {
            if (NightWindow.isNight(LocalTime.now())) {
                "AI analizira mikrofon; alarmi su dozvoljeni do 08:00."
            } else {
                "Noćni prozor je završen; praćenje se automatski zaustavlja."
            }
        } else "Pokreni praćenje pre spavanja. Test sata radi u bilo koje vreme."

        val active = store.active()
        val latest = active ?: store.history().optJSONObject(0)
        if (active != null) {
            confidence.text = if (active.optBoolean("modelActive")) {
                "YAMNet lokalno • Snoring skor: ${"%.2f".format(Locale.US, active.optDouble("lastScore"))} (prag 0.07)"
            } else "AI model se učitava…"
        } else {
            confidence.text = "YAMNet • zvuk se ne čuva, obrada samo na telefonu"
        }
        showStats(latest, active != null)
        showHistory()
        errors.text = store.error()
    }

    private fun showStats(item: JSONObject?, active: Boolean) {
        if (item == null) {
            sessionTime.text = "Još nema praćenja"
            episodeStats.text = "Epizode hrkanja: 0"
            snoreStats.text = "Procenjeno hrkanje: 0 s"
            alertStats.text = "Serije alarma: 0 • Obaveštenja: 0"
            return
        }
        val begin = item.optLong("start")
        sessionTime.text = (if (active) "Aktivno od " else "Poslednja sesija ") + dateFormat.format(Date(begin))
        val newDetector = item.optString("detectorVersion") == "breaths_v1"
        val episodeTitle = if (newDetector) "Prepoznati pojedinačni hrkaji" else "Epizode hrkanja (stara detekcija)"
        episodeStats.text = "$episodeTitle: ${item.optInt("episodes")}"
        val seconds = (item.optInt("snoreFrames").toLong() * item.optInt("hopMs", 975) / 1_000L).toInt()
        snoreStats.text = "Procenjeno hrkanje: ${seconds / 60} min ${seconds % 60} s"
        alertStats.text = "Serije alarma: ${item.optInt("alerts")} • Obaveštenja: ${item.optInt("notifications", item.optInt("alerts"))}"
    }

    private fun showHistory() {
        val records = store.history()
        if (records.length() == 0) {
            history.text = "Još nema sačuvanih noći."
            return
        }
        val rows = ArrayList<String>()
        for (index in 0 until minOf(7, records.length())) {
            val item = records.optJSONObject(index) ?: continue
            val date = dateFormat.format(Date(item.optLong("start")))
            val mins = (item.optInt("snoreFrames").toLong() * item.optInt("hopMs", 975) / 60_000L).toInt()
            val episodeUnit = if (item.optString("detectorVersion") == "breaths_v1") "hrkaja" else "ep."
            rows.add("$date  •  $mins min  •  ${item.optInt("episodes")} $episodeUnit  •  ${item.optInt("alerts")} serija  •  ${item.optInt("notifications", item.optInt("alerts"))} obav.")
        }
        history.text = rows.joinToString("\n\n")
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(updater)
        handler.post(updater)
    }

    override fun onPause() {
        handler.removeCallbacks(updater)
        super.onPause()
    }

    override fun onDestroy() {
        if (::manualBurst.isInitialized) manualBurst.cancel()
        super.onDestroy()
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    companion object { private const val REQUEST_PERMISSIONS = 1001 }
}
