package rs.snoreguard.mvp

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.media.MediaPlayer
import android.widget.LinearLayout
import androidx.core.content.FileProvider
import android.app.NotificationManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
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
import android.text.format.DateFormat
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
    private lateinit var sensitivitySpinner: Spinner
    private lateinit var alertModeSpinner: Spinner
    private lateinit var readinessStatus: TextView
    private lateinit var diagnostics: TextView
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
    private lateinit var hourlyChart: SnoreBarChartView
    private lateinit var comparisonChart: SnoreBarChartView
    private lateinit var hourlyDetails: TextView
    private lateinit var comparisonDetails: TextView
    private lateinit var exportStatus: TextView
    private lateinit var clipStore: SnoreClipStore
    private lateinit var clipList: LinearLayout
    private lateinit var clipCountText: TextView
    private lateinit var recordingDescription: TextView
    private lateinit var snoreTestButton: Button
    private lateinit var snoreTestStatus: TextView
    private var testStopRequested = false
    private var player: MediaPlayer? = null
    private var lastClipSignature = ""
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
        clipStore = SnoreClipStore(this)
        if (!MonitorService.isActive) store.recoverInterrupted()
        AlertNotifier.ensureChannels(this)

        startStop = findViewById(R.id.startStopButton)
        test = findViewById(R.id.testButton)
        testBurst = findViewById(R.id.testBurstButton)
        testBurstStatus = findViewById(R.id.testBurstStatus)
        configureTestBurst()
        status = findViewById(R.id.statusLabel)
        description = findViewById(R.id.liveDescription)
        confidence = findViewById(R.id.liveConfidence)
        sessionTime = findViewById(R.id.sessionTime)
        episodeStats = findViewById(R.id.episodeStats)
        snoreStats = findViewById(R.id.snoreStats)
        alertStats = findViewById(R.id.alertStats)
        history = findViewById(R.id.historyText)
        errors = findViewById(R.id.errorText)
        hourlyChart = findViewById(R.id.hourlyChart)
        comparisonChart = findViewById(R.id.comparisonChart)
        hourlyDetails = findViewById(R.id.hourlyDetails)
        comparisonDetails = findViewById(R.id.comparisonDetails)
        exportStatus = findViewById(R.id.exportStatus)
        clipList = findViewById(R.id.clipList)
        clipCountText = findViewById(R.id.clipCountText)
        recordingDescription = findViewById(R.id.recordingDescription)
        snoreTestButton = findViewById(R.id.snoreTestButton)
        snoreTestStatus = findViewById(R.id.snoreTestStatus)
        setupRecordingAndTimeControls()
        snoreTestButton.setOnClickListener {
            when {
                MonitorService.isActive && MonitorService.isTestSession -> {
                    if (!testStopRequested) {
                        testStopRequested = true
                        snoreTestButton.isEnabled = false
                        snoreTestButton.text = "Zaustavljam test…"
                        snoreTestStatus.text = "Zaustavljanje testa je zatraženo."
                        startService(Intent(this, MonitorService::class.java)
                            .setAction(MonitorService.ACTION_STOP))
                    }
                }
                MonitorService.isActive -> toast(
                    "Noćno praćenje je aktivno. Zaustavi ga na dugme 'Zaustavi praćenje'.")
                else -> requirePermissionsThen("snore_test")
            }
        }
        findViewById<Button>(R.id.deleteAllClipsButton).setOnClickListener {
            if (MonitorService.isActive) {
                toast("Zaustavi praćenje pre brisanja snimaka.")
            } else {
                AlertDialog.Builder(this)
                    .setTitle("Obriši sve zvučne snimke?")
                    .setMessage("Ova radnja trajno briše sve WAV snimke iz aplikacije.")
                    .setNegativeButton("Otkaži", null)
                    .setPositiveButton("Obriši") { _, _ ->
                        stopPlayback()
                        if (clipStore.deleteAll()) {
                            lastClipSignature = ""
                            showSavedClips()
                        } else toast("Brisanje nije uspelo.")
                    }.show()
            }
        }
        findViewById<Button>(R.id.exportCsvButton).setOnClickListener {
            createDocument("text/csv", "SnoreGuard-statistika.csv", REQUEST_EXPORT_CSV)
        }
        findViewById<Button>(R.id.backupButton).setOnClickListener {
            if (MonitorService.isActive) {
                toast("Zaustavi praćenje da bi i trenutna noć ušla u export.")
            } else {
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
                createDocument("application/json", "SnoreGuard-statistika-$date.json", REQUEST_BACKUP)
            }
        }
        findViewById<Button>(R.id.restoreButton).setOnClickListener {
            if (MonitorService.isActive) {
                toast("Zaustavi praćenje pre importa statistike.")
            } else {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                }
                startActivityForResult(intent, REQUEST_RESTORE)
            }
        }
        readinessStatus = findViewById(R.id.readinessStatus)
        diagnostics = findViewById(R.id.diagnosticsText)
        setupProfileControls()
        findViewById<Button>(R.id.readinessButton).setOnClickListener {
            readinessStatus.text = evaluateReadiness()
        }
        findViewById<Button>(R.id.appSettingsButton).setOnClickListener {
            startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")))
        }
        findViewById<Button>(R.id.batterySettingsButton).setOnClickListener {
            try {
                startActivity(Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {
                startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")))
            }
        }

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
        if ((action == "start" || action == "snore_test") &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.RECORD_AUDIO)
        }
        // Detection test must run even if the user has notifications disabled.
        if (action != "snore_test" && Build.VERSION.SDK_INT >= 33 &&
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
                    AlertNotifier.sendWatchAlert(this, test = true, totalSteps = 1)
                    toast("Jedno test obaveštenje je poslato.")
                } catch (ex: Exception) {
                    toast("Test nije uspeo: ${ex.message}")
                }
            } else if (!manualBurst.isRunning) {
                testBurst.isEnabled = false
                configureTestBurst()
                testBurstStatus.text = "Počinje test: ${settings.alertMode.steps} obaveštenja"
                if (!manualBurst.start()) {
                    testBurst.isEnabled = true
                    testBurstStatus.text = "Test nije pokrenut: proveri dozvole i obaveštenja."
                }
            }
            return
        }
        if (action == "start" || action == "snore_test") {
            val isTest = action == "snore_test"
            if (!isTest && !NightWindow.isNight(
                    LocalTime.now(), settings.windowStart(), settings.windowEnd())) {
                toast("Praćenje je dostupno samo u izabranom intervalu " +
                    Settings.timeLabel(settings.windowStartMinutes) + "–" +
                    Settings.timeLabel(settings.windowEndMinutes) + ".")
                return
            }
            val manager = getSystemService(android.app.NotificationManager::class.java)
            if (!isTest && !manager.areNotificationsEnabled()) {
                toast("Obaveštenja moraju biti uključena za GT 2 alarm.")
                return
            }
            try {
                startForegroundService(Intent(this, MonitorService::class.java).setAction(
                    if (isTest) MonitorService.ACTION_TEST else MonitorService.ACTION_START))
                toast(if (isTest) "Pokrećem 90-sekundni test prepoznavanja hrkanja."
                      else "Pokrećem lokalno praćenje hrkanja.")
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
        val testing = running && MonitorService.isTestSession
        startStop.text = if (running) "Zaustavi praćenje" else "Pokreni praćenje"
        status.text = when {
            testing -> "● Test prepoznavanja hrkanja je aktivan"
            running -> "● Praćenje je aktivno"
            else -> "● Praćenje nije aktivno"
        }
        if (!running) testStopRequested = false
        snoreTestButton.isEnabled = !running || (testing && !testStopRequested)
        snoreTestButton.text = when {
            testing && testStopRequested -> "Zaustavljam test…"
            testing -> "Zaustavi test hrkanja"
            else -> "Testiraj prepoznavanje hrkanja (90 s)"
        }
        snoreTestStatus.text = when {
            testing && testStopRequested -> "Zaustavljam test…"
            testing -> "Test traje najviše 90 sekundi. Probaj da simuliraš hrkanje. " +
                "Dodirni isto dugme da ga prekineš. Snimci i noćna istorija se ne čuvaju."
            running -> "Noćno praćenje je aktivno. Test možeš pokrenuti nakon zaustavljanja."
            else -> "Test prepoznavanja radi kada god želiš, nezavisno od izabranog vremena."
        }
        description.text = if (testing) {
            "AI proverava mikrofon i prepoznavanje hrkanja u probnom režimu."
        } else if (running) {
            "AI radi do " + Settings.timeLabel(settings.windowEndMinutes) +
                ". Promene rasporeda važe pri sledećem pokretanju."
        } else "Ručno pokreni praćenje tokom izabranog intervala. Test može bilo kada."

        val active = store.active()
        val latest = active ?: store.history().optJSONObject(0)
        if (active != null) {
            confidence.text = if (active.optBoolean("modelActive")) {
                "YAMNet lokalno • Snoring skor: ${"%.2f".format(Locale.US, active.optDouble("lastScore"))} (prag ${"%.2f".format(Locale.US, active.optDouble("threshold", 0.07))})"
            } else "AI model se učitava…"
        } else {
            confidence.text = "YAMNet • zvuk se ne čuva, obrada samo na telefonu"
        }
        showStats(latest, active != null)
        showDiagnostics(latest, active != null)
        showHistory()
        showCharts(latest)
        showSavedClips()
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

    private fun showDiagnostics(item: JSONObject?, active: Boolean) {
        if (item == null) {
            diagnostics.text = "Dijagnostika: nema podataka."
            return
        }
        val frames = item.optInt("analysedFrames")
        val threshold = item.optDouble("threshold", 0.07)
        val age = System.currentTimeMillis() - item.optLong("lastUpdate", System.currentTimeMillis())
        val freshness = if (active && age > 30_000L) {
            "⚠ Nema novih rezultata duže od 30 s — proveri praćenje."
        } else if (active) {
            "AI analiza je aktivna."
        } else {
            "Završetak: ${item.optString("stopReason", "Nepoznato")}"
        }
        diagnostics.text = "Obrađeno prozora: $frames • prag: ${"%.2f".format(Locale.US, threshold)}\n$freshness"
    }

    private fun showCharts(latest: JSONObject?) {
        val hourly = latest?.optJSONObject("hourly")
        val labels = NightHours.labelsForWindow(
            latest?.optInt("windowStart", 22 * 60) ?: 22 * 60,
            latest?.optInt("windowEnd", 8 * 60) ?: 8 * 60
        )
        hourlyChart.showSeries(labels, labels.map { hourly?.optInt(it, 0) ?: 0 }, hourly != null)
        hourlyDetails.text = if (hourly == null) {
            "Satna statistika postoji tek za sesije snimljene verzijom v0.5.0 i novijom."
        } else {
            "Prepoznata pojedinačna hrkanja po satu: ${labels.sumOf { hourly.optInt(it, 0) }}. " +
                "Grafikon nije medicinsko merenje."
        }

        val recent = mutableListOf<JSONObject>()
        val history = store.history()
        for (i in 0 until history.length()) {
            val item = history.optJSONObject(i) ?: continue
            // Legacy versions used a different event definition: never compare them.
            if (item.optString("detectorVersion") != "breaths_v1") continue
            recent.add(item)
            if (recent.size == 7) break
        }
        val sorted = recent.reversed()
        val smallDate = SimpleDateFormat("dd.MM", Locale.forLanguageTag("sr-RS"))
        comparisonChart.showSeries(
            sorted.map { smallDate.format(Date(it.optLong("start"))) },
            sorted.map { it.optInt("episodes") },
            sorted.isNotEmpty()
        )
        comparisonDetails.text = if (recent.isEmpty()) {
            "Još nema uporedivih završenih noći. Praćenje je potrebno završiti da bi se pojavilo ovde."
        } else {
            val delta = if (recent.size >= 2) {
                val diff = recent[0].optInt("episodes") - recent[1].optInt("episodes")
                "Poslednja noć: ${recent[0].optInt("episodes")} prepoznatih hrkanja; " +
                    "razlika prema prethodnoj: ${if (diff > 0) "+" else ""}$diff."
            } else "Sačuvana je jedna noć. Poređenje će se pojaviti posle druge."
            "$delta Promena osetljivosti i dužine praćenja utiče na rezultat."
        }
    }

    private fun createDocument(mime: String, title: String, requestCode: Int) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mime
            putExtra(Intent.EXTRA_TITLE, title)
        }
        startActivityForResult(intent, requestCode)
    }

    @Deprecated("Uses platform SAF callbacks to support Android 8 without AndroidX")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            when (requestCode) {
                REQUEST_EXPORT_CSV, REQUEST_BACKUP -> {
                    val content = if (requestCode == REQUEST_EXPORT_CSV) {
                        store.exportCsv()
                    } else {
                        store.exportBackup()
                    }
                    val target = contentResolver.openOutputStream(uri, "w")
                        ?: throw IllegalStateException("Ne mogu da otvorim odredište.")
                    target.bufferedWriter(Charsets.UTF_8).use { it.write(content) }
                    exportStatus.text = if (requestCode == REQUEST_EXPORT_CSV) {
                        "✓ CSV statistika je sačuvana u izabranom folderu."
                    } else {
                        "✓ Export završen: ${store.history().length()} završenih noći sa podešavanjima. Sačuvaj JSON fajl."
                    }
                }
                REQUEST_RESTORE -> {
                    if (MonitorService.isActive) {
                        toast("Zaustavi praćenje pre vraćanja podataka.")
                        return
                    }
                    val source = contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("Ne mogu da pročitam fajl.")
                    val content = source.bufferedReader(Charsets.UTF_8).use { reader ->
                        val limited = CharArray(1_000_001)
                        var filled = 0
                        while (filled < limited.size) {
                            val count = reader.read(limited, filled, limited.size - filled)
                            if (count < 0) break
                            filled += count
                        }
                        require(filled <= 1_000_000) { "Prevelika datoteka za import." }
                        String(limited, 0, filled)
                    }
                    AlertDialog.Builder(this)
                        .setTitle("Import statistike?")
                        .setMessage("JSON fajl će dodati nedostajuće završene noći u istoriju. " +
                            "Postojeće sesije se ne dupliraju; čuva se najnovijih 365. " +
                            "Vratiće se i podešavanja alarma i vremena praćenja. " +
                            "WAV zvučni snimci nisu deo ovog importa.")
                        .setNegativeButton("Otkaži", null)
                        .setPositiveButton("Importuj") { _, _ ->
                            try {
                                val imported = store.importBackup(content)
                                exportStatus.text = "✓ Import završen: $imported novih noći; " +
                                    "ukupno ${store.history().length()} sačuvanih."
                                settings = Settings(this)
                                setupProfileControls()
                                updateWindowButtons()
                                findViewById<Switch>(R.id.phoneVibrationSwitch).isChecked =
                                    settings.phoneVibration
                                refresh()
                            } catch (error: Exception) {
                                exportStatus.text = "✗ Uvoz nije uspeo: ${error.message}"
                            }
                        }
                        .show()
                }
            }
        } catch (error: Exception) {
            exportStatus.text = "✗ Operacija nije uspela: ${error.message}"
        }
    }

    private fun setupRecordingAndTimeControls() {
        val startButton = findViewById<Button>(R.id.setStartTimeButton)
        val endButton = findViewById<Button>(R.id.setEndTimeButton)
        startButton.setOnClickListener { chooseTime(true) }
        endButton.setOnClickListener { chooseTime(false) }
        val toggle = findViewById<Switch>(R.id.saveClipsSwitch)
        toggle.isChecked = settings.saveSnoreClips
        toggle.setOnCheckedChangeListener { _, enabled ->
            if (enabled && !settings.saveSnoreClips) {
                AlertDialog.Builder(this)
                    .setTitle("Uključi snimanje hrkanja?")
                    .setMessage("SnoreGuard će lokalno sačuvati kratke WAV isečke " +
                        "kada AI potvrdi ponovljeno hrkanje (do 10 po sesiji, " +
                        "40 ukupno). Snimci mogu sadržati razgovore ili druge osobe " +
                        "u sobi. Ne šalju se automatski nikome. " +
                        "Uključuj snimanje samo uz saglasnost prisutnih.")
                    .setNegativeButton("Ne") { _, _ -> toggle.isChecked = false }
                    .setPositiveButton("Uključi") { _, _ ->
                        settings.saveSnoreClips = true
                        updateRecordingDescription()
                    }.show()
            } else if (!enabled) {
                settings.saveSnoreClips = false
                updateRecordingDescription()
            }
        }
        updateWindowButtons()
        updateRecordingDescription()
    }

    private fun chooseTime(start: Boolean) {
        val original = if (start) settings.windowStartMinutes else settings.windowEndMinutes
        TimePickerDialog(this, { _, hour, minute ->
            if (start) settings.windowStartMinutes = hour * 60 + minute
            else settings.windowEndMinutes = hour * 60 + minute
            updateWindowButtons()
        }, original / 60, original % 60, true).show()
    }

    private fun updateWindowButtons() {
        findViewById<Button>(R.id.setStartTimeButton).text =
            "Početak: " + Settings.timeLabel(settings.windowStartMinutes)
        findViewById<Button>(R.id.setEndTimeButton).text =
            "Kraj: " + Settings.timeLabel(settings.windowEndMinutes)
        findViewById<TextView>(R.id.windowDescription).text =
            "Praćenje se pokreće ručno i automatski staje na kraju intervala. " +
                "Ako su vremena ista, interval traje 24 h. " +
                "Promene tokom rada važe narednim pokretanjem."
    }

    private fun updateRecordingDescription() {
        recordingDescription.text = if (settings.saveSnoreClips) {
            "✓ Čuvanje WAV snimaka uključeno. Radi u sledećoj noćnoj sesiji."
        } else "Čuvanje zvuka je isključeno. Raniji snimci ostaju do brisanja."
    }

    private fun stopPlayback() {
        val old = player
        player = null
        if (old != null) try { old.release() } catch (_: Exception) {}
    }

    private fun showSavedClips() {
        val clips = clipStore.list()
        clipCountText.text = if (clips.isEmpty()) {
            "Nema snimaka. Uključi opciju čuvanja zvuka pre noćnog praćenja."
        } else "Sačuvano: ${clips.size} WAV snimaka. Dodirni za preslušavanje ili deljenje."
        val signature = clips.joinToString("|") { it.filename }
        if (signature == lastClipSignature) return
        lastClipSignature = signature
        clipList.removeAllViews()
        val format = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.forLanguageTag("sr-RS"))
        for (clip in clips.take(40)) {
            val button = Button(this).apply {
                text = format.format(Date(clip.recordedAt)) + "  •  " +
                    (clip.durationMs / 1000) + " s  •  WAV"
                isAllCaps = false
                setTextColor(getColor(R.color.text_primary))
                setOnClickListener { chooseClipAction(clip) }
            }
            clipList.addView(button)
        }
    }

    private fun chooseClipAction(clip: SnoreClip) {
        AlertDialog.Builder(this)
            .setTitle("Snimak hrkanja")
            .setMessage("Trajanje: " + (clip.durationMs / 1000) +
                " s. Sadrži stvarni zvuk iz sobe i može biti osetljiv.")
            .setItems(arrayOf("Preslušaj", "Podeli WAV sa lekarom", "Obriši")) { _, which ->
                val file = clipStore.fileFor(clip.filename)
                if (file == null) {
                    toast("Snimak nije dostupan.")
                    return@setItems
                }
                when (which) {
                    0 -> try {
                        stopPlayback()
                        val current = MediaPlayer()
                        current.setDataSource(file.absolutePath)
                        current.prepare()
                        current.setOnCompletionListener { stopPlayback() }
                        player = current
                        current.start()
                        toast("Reprodukcija snimka je pokrenuta.")
                    } catch (error: Exception) {
                        stopPlayback()
                        toast("Ne može da se pusti snimak: " + error.message)
                    }
                    1 -> try {
                        val uri = FileProvider.getUriForFile(
                            this, packageName + ".files", file)
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "audio/wav"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            clipData = ClipData.newUri(contentResolver, "SnoreGuard WAV", uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(send, "Podeli WAV snimak"))
                    } catch (error: Exception) {
                        toast("Deljenje nije uspelo: " + error.message)
                    }
                    2 -> if (MonitorService.isActive) {
                        toast("Zaustavi praćenje pre brisanja snimka.")
                    } else {
                        AlertDialog.Builder(this)
                            .setTitle("Trajno obriši snimak?")
                            .setNegativeButton("Otkaži", null)
                            .setPositiveButton("Obriši") { _, _ ->
                                stopPlayback()
                                if (!clipStore.delete(clip.filename)) {
                                    toast("Brisanje nije uspelo.")
                                }
                                lastClipSignature = ""
                                showSavedClips()
                            }.show()
                    }
                }
            }.show()
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

    private fun configureTestBurst() {
        if (::manualBurst.isInitialized) manualBurst.cancel()
        val mode = settings.alertMode
        manualBurst = AlarmBurst(
            scheduler = BurstScheduler { delay, action -> handler.postDelayed({ action() }, delay) },
            canContinue = { !isFinishing && !isDestroyed && notificationsEnabled() },
            emit = { step ->
                AlertNotifier.sendWatchAlert(this, test = true, step = step, totalSteps = mode.steps)
                testBurstStatus.text = if (step < mode.steps) {
                    "Test: poslato $step/${mode.steps} • sledeće za ${mode.intervalMillis / 1_000} s"
                } else "Test završen: ${mode.steps} obaveštenja"
                if (step == mode.steps) testBurst.isEnabled = true
            },
            totalAlerts = mode.steps,
            intervalMillis = mode.intervalMillis
        )
        val durationSeconds = (mode.steps - 1) * (mode.intervalMillis / 1_000)
        testBurst.text = "Test alarma ×${mode.steps} (${durationSeconds} sekundi)"
        testBurstStatus.text = "Test radi i tokom dana. GT 2 vibraciju proveri na satu."
    }

    private fun setupProfileControls() {
        sensitivitySpinner = findViewById(R.id.sensitivitySpinner)
        alertModeSpinner = findViewById(R.id.alertModeSpinner)
        val sensitivities = Sensitivity.values()
        sensitivitySpinner.adapter = ArrayAdapter(this,
            android.R.layout.simple_spinner_item, sensitivities.map { it.title }).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        sensitivitySpinner.setSelection(settings.sensitivity.ordinal)
        sensitivitySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?,
                                        position: Int, id: Long) {
                settings.sensitivity = sensitivities[position]
            }
        }
        val modes = AlertMode.values()
        alertModeSpinner.adapter = ArrayAdapter(this,
            android.R.layout.simple_spinner_item, modes.map { it.title }).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        alertModeSpinner.setSelection(settings.alertMode.ordinal)
        alertModeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?,
                                        position: Int, id: Long) {
                if (!::manualBurst.isInitialized || !manualBurst.isRunning) {
                    settings.alertMode = modes[position]
                    configureTestBurst()
                }
            }
        }
    }

    private fun evaluateReadiness(): String {
        val lines = mutableListOf<String>()
        val microphoneOk = checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        lines += if (microphoneOk) "✓ Mikrofon: dozvoljen" else "✗ Mikrofon: potrebna dozvola"
        val notificationsOk = notificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED)
        lines += if (notificationsOk) "✓ Obaveštenja: dozvoljena"
                 else "✗ Obaveštenja: uključiti u podešavanjima"
        val nm = getSystemService(NotificationManager::class.java)
        val channelId = if (settings.phoneVibration) AlertNotifier.VIBRATE_CHANNEL
                        else AlertNotifier.SILENT_CHANNEL
        val channel = nm.getNotificationChannel(channelId)
        lines += if (channel != null && channel.importance != NotificationManager.IMPORTANCE_NONE) {
            "✓ Kanal SnoreGuard alarma: aktivan"
        } else {
            "✗ Kanal alarma: blokiran (proveri podešavanja)"
        }
        val powerManager = getSystemService(PowerManager::class.java)
        lines += if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
            "✓ Android ne optimizuje bateriju za SnoreGuard"
        } else {
            "⚠ Štednja baterije može prekinuti praćenje — proveri podešavanja"
        }
        val hours = Settings.timeLabel(settings.windowStartMinutes) + "–" +
            Settings.timeLabel(settings.windowEndMinutes)
        lines += if (NightWindow.isNight(LocalTime.now(),
                settings.windowStart(), settings.windowEnd())) {
            "✓ Sada je unutar izabranog intervala $hours"
        } else {
            "ℹ Praćenje se pokreće ručno u intervalu $hours"
        }
        lines += if (settings.saveSnoreClips) {
            "ℹ Snimanje kratkih WAV isečaka je uključeno."
        } else "✓ Čuvanje zvuka je isključeno."
        lines += "ℹ Na GT 2 ručno proveri Huawei Health, Bluetooth i režim Ne uznemiravaj."
        return lines.joinToString("\n")
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(updater)
        handler.post(updater)
    }

    override fun onPause() {
        handler.removeCallbacks(updater)
        stopPlayback()
        super.onPause()
    }

    override fun onDestroy() {
        if (::manualBurst.isInitialized) manualBurst.cancel()
        super.onDestroy()
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    companion object {
        private const val REQUEST_PERMISSIONS = 1001
        private const val REQUEST_EXPORT_CSV = 5001
        private const val REQUEST_BACKUP = 5002
        private const val REQUEST_RESTORE = 5003
    }
}
