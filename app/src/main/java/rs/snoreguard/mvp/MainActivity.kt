package rs.snoreguard.mvp

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.ClipData
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
import android.widget.EditText
import android.text.InputFilter
import android.text.InputType
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
    private lateinit var fullBackupStatus: TextView
    @Volatile private var fullBackupBusy = false
    private lateinit var clipStore: SnoreClipStore
    private lateinit var clipList: LinearLayout
    private lateinit var clipCountText: TextView
    private lateinit var recordingDescription: TextView
    private lateinit var snoreTestButton: Button
    private lateinit var snoreTestStatus: TextView
    private var testStopRequested = false
    private var clipPlayer: ClipPlayer? = null
    private var currentTab = 0
    private var selectedNightStart: Long? = null
    private var selectedNightEnd: Long? = null
    private var lastHistorySignature = ""
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
        currentTab = savedInstanceState?.getInt("tab", 0)?.coerceIn(0, 3) ?: 0
        selectedNightStart = savedInstanceState?.getLong("selectedStart", 0)?.takeIf { it > 0 }
        selectedNightEnd = savedInstanceState?.getLong("selectedEnd", 0)?.takeIf { it > 0 }
        val root = findViewById<android.view.View>(R.id.appRoot)
        root.setOnApplyWindowInsetsListener { v, insets ->
            @Suppress("DEPRECATION")
            v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        root.requestApplyInsets()
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
        fullBackupStatus = findViewById(R.id.fullBackupStatus)
        findViewById<Button>(R.id.fullExportButton).setOnClickListener {
            if (MonitorService.isActive || fullBackupBusy) {
                toast("Zaustavi praćenje pre potpune rezervne kopije.")
            } else {
                AlertDialog.Builder(this)
                    .setTitle("Izvezi privatne snimke i statistiku?")
                    .setMessage("ZIP sadrži stvarne WAV snimke, ocene, beleške i istoriju. " +
                        "Nije šifrovan; čuvaj ga na privatnoj lokaciji i ne deli javno. " +
                        "Izvoz radi samo kada praćenje nije aktivno.")
                    .setNegativeButton("Otkaži", null)
                    .setPositiveButton("Nastavi") { _, _ ->
                        val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
                        createDocument("application/zip", "SnoreGuard-kompletna-kopija-" +
                            date + ".zip", REQUEST_FULL_EXPORT)
                    }.show()
            }
        }
        findViewById<Button>(R.id.fullImportButton).setOnClickListener {
            if (MonitorService.isActive || fullBackupBusy) {
                toast("Zaustavi praćenje pre importa podataka.")
            } else {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                        "application/zip", "application/x-zip-compressed",
                        "application/octet-stream"))
                }
                startActivityForResult(intent, REQUEST_FULL_IMPORT)
            }
        }
        clipList = findViewById(R.id.clipList)
        clipCountText = findViewById(R.id.clipCountText)
        recordingDescription = findViewById(R.id.recordingDescription)
        snoreTestButton = findViewById(R.id.snoreTestButton)
        snoreTestStatus = findViewById(R.id.snoreTestStatus)
        setupRecordingAndTimeControls()
        findViewById<Button>(R.id.doctorPdfButton).setOnClickListener {
            if (MonitorService.isActive || fullBackupBusy || store.history().length() == 0) {
                toast("Za PDF je potrebna završena sesija i zaustavljeno praćenje.")
            } else {
                AlertDialog.Builder(this).setTitle("Izveštaj za lekara")
                    .setMessage("PDF sadrži do sedam završenih sesija, statistiku i broj ocena. Ne sadrži WAV snimke ni beleške. Sačuvaj ga privatno.")
                    .setNegativeButton("Otkaži", null)
                    .setPositiveButton("Izvezi PDF") { _, _ ->
                        createDocument("application/pdf", "SnoreGuard-izvestaj.pdf", REQUEST_DOCTOR_PDF)
                    }.show()
            }
        }
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
        findViewById<Button>(R.id.exportClipReviewsButton).setOnClickListener {
            if (clipStore.list().isEmpty()) {
                toast("Nema snimaka za ocenjivanje.")
            } else {
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
                createDocument("text/csv", "SnoreGuard-ocene-snimaka-$date.csv",
                    REQUEST_EXPORT_CLIP_REVIEWS)
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

        val smart = findViewById<Switch>(R.id.smartFollowupsSwitch)
        smart.isChecked = settings.smartFollowups
        smart.setOnCheckedChangeListener { _, checked -> settings.smartFollowups = checked }

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
        listOf(R.id.tabMonitor, R.id.tabStats, R.id.tabClips, R.id.tabSettings).forEachIndexed { i, id ->
            findViewById<Button>(id).setOnClickListener { selectTab(i) }
        }
        findViewById<Button>(R.id.openMorningStats).setOnClickListener {
            selectedNightStart = null; selectedNightEnd = null; selectTab(1)
        }
        findViewById<Button>(R.id.latestNightButton).setOnClickListener {
            selectedNightStart = null; selectedNightEnd = null; refresh()
        }
        findViewById<Button>(R.id.pickNightButton).setOnClickListener { pickNight() }
        selectTab(currentTab)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("tab", currentTab)
        selectedNightStart?.let { outState.putLong("selectedStart", it) }
        selectedNightEnd?.let { outState.putLong("selectedEnd", it) }
        super.onSaveInstanceState(outState)
    }

    private fun selectTab(index: Int) {
        if (index != 2) stopPlayback()
        currentTab = index
        val pages = listOf(R.id.pageMonitor, R.id.pageStats, R.id.pageClips, R.id.pageSettings)
        val tabs = listOf(R.id.tabMonitor, R.id.tabStats, R.id.tabClips, R.id.tabSettings)
        pages.forEachIndexed { i, id -> findViewById<android.view.View>(id).visibility =
            if (i == index) android.view.View.VISIBLE else android.view.View.GONE }
        tabs.forEachIndexed { i, id ->
            findViewById<Button>(id).apply {
                isSelected = i == index
                setBackgroundResource(if (i == index) R.drawable.bg_primary else R.drawable.bg_secondary)
                setTextColor(getColor(if (i == index) R.color.button_text else R.color.text_primary))
                contentDescription = text.toString() + if (i == index) ", izabrano" else ""
            }
        }
        findViewById<android.widget.ScrollView>(R.id.pageScroll).scrollTo(0, 0)
        refresh()
    }

    private fun records(): List<JSONObject> {
        val history = store.history()
        return (0 until history.length()).mapNotNull { history.optJSONObject(it) }
    }

    private fun pickNight() {
        val rows = records()
        if (rows.isEmpty()) { toast("Nema sačuvanih sesija."); return }
        val zone = java.time.ZoneId.systemDefault()
        fun day(item: JSONObject) = java.time.Instant.ofEpochMilli(item.optLong("start")).atZone(zone).toLocalDate()
        val chosen = rows.firstOrNull { it.optLong("start") == selectedNightStart && it.optLong("end") == selectedNightEnd } ?: rows.first()
        val initial = day(chosen)
        val dialog = android.app.DatePickerDialog(this, { _, year, month, dayOfMonth ->
            val date = java.time.LocalDate.of(year, month + 1, dayOfMonth)
            val matches = rows.filter { day(it) == date }
            if (matches.isEmpty()) toast("Nema sesije započete tog datuma.")
            else {
                fun choose(item: JSONObject) {
                    selectedNightStart = item.optLong("start"); selectedNightEnd = item.optLong("end"); refresh()
                }
                if (matches.size == 1) choose(matches.first())
                else AlertDialog.Builder(this).setTitle("Izaberi sesiju")
                    .setItems(matches.map { dateFormat.format(Date(it.optLong("start"))) + " • " + it.optInt("episodes") + " događaja" }.toTypedArray()) { _, which -> choose(matches[which]) }.show()
            }
        }, initial.year, initial.monthValue - 1, initial.dayOfMonth)
        dialog.datePicker.minDate = rows.minOf { day(it) }.atStartOfDay(zone).toInstant().toEpochMilli()
        dialog.datePicker.maxDate = rows.maxOf { day(it) }.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        dialog.show()
    }

    private fun sessionSummary(item: JSONObject?): String {
        if (item == null) return "Još nema završene sesije."
        val start = item.optLong("start")
        val end = item.optLong("end")
        val result = NightReport.metrics(item)
        return dateFormat.format(Date(start)) + " – " + dateFormat.format(Date(end)) +
            "\nPraćenje: " + NightReport.decimal((end - start).coerceAtLeast(0) / 3600000.0) + " h" +
            "\nDetektovani događaji: " + item.optInt("episodes") +
            "\nIndeks hrkanja: " + (result?.score?.toString()?.plus(" / 100") ?: "nema dovoljno podataka") +
            "\nSerije alarma: " + item.optInt("alerts") + " • obaveštenja: " +
            if (item.has("notifications")) item.optInt("notifications").toString() else "nepoznato"
    }

    private fun showArchive(rows: List<JSONObject>, item: JSONObject?) {
        findViewById<TextView>(R.id.morningSummary).text = "POSLEDNJA ZAVRŠENA SESIJA\n\n" + sessionSummary(rows.firstOrNull())
        findViewById<Button>(R.id.openMorningStats).isEnabled = rows.isNotEmpty()
        findViewById<TextView>(R.id.selectedNightSummary).text = sessionSummary(item)
        val alarms = item?.optJSONObject("hourlyNotifications")
        val hourly = item?.optJSONObject("hourly")
        findViewById<TextView>(R.id.alertTimeline).text = if (item == null || alarms == null || hourly == null)
            "Vremenska linija alarma: dostupna za sesije od v0.9.0. Satno hrkanje ostaje u grafikonu."
        else {
            val hours = NightHours.labelsForWindow(item.optInt("windowStart",1320).coerceIn(0,1439),
                item.optInt("windowEnd",480).coerceIn(0,1439))
            "VREMENSKA LINIJA PO SATIMA\nHrkanja / poslata obaveštenja\n" +
                hours.joinToString("\n") { hour -> "$hour:00   ${hourly.optInt(hour)}  /  ${alarms.optInt(hour)}" } +
                "\nNula označava odsustvo zapisa, ne dokaz kontinuiranog praćenja ili odsustva hrkanja."
        }
        val signature = rows.joinToString("|") { "${it.optLong("start")}:${it.optLong("end")}:${it.optInt("episodes")}:${it.optInt("analysedFrames")}:${it.optString("sensitivity")}" } +
            "|" + (item?.optString("sensitivity") ?: "") + "|" + java.time.LocalDate.now()
        if (signature == lastHistorySignature) return
        lastHistorySignature = signature
        val sensitivity = item?.optString("sensitivity").orEmpty()
        val nights = rows.mapNotNull { row ->
            val r = NightReport.metrics(row) ?: return@mapNotNull null
            HistoryTrends.Night(row.optLong("start"), row.optString("sensitivity"), row.optInt("episodes"), r.analysedHours, r.coverage)
        }
        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.now(zone)
        fun trend(days: Long): String {
            val from = today.minusDays(days - 1).atStartOfDay(zone).toInstant().toEpochMilli()
            val summary = HistoryTrends.summarize(nights, from, System.currentTimeMillis(), sensitivity)
            return "$days dana: " + (summary.eventsPerHour?.let { NightReport.decimal(it) + " događaja/h" } ?: "nema podataka") +
                " • ${summary.sessions} sesija • " + NightReport.decimal(summary.hours) + " h analize"
        }
        findViewById<TextView>(R.id.trendSummary).text = trend(7) + "\n\n" + trend(30) +
            "\n\nIsta osetljivost kao izabrana sesija; minimum 30 min i 80% pokrivenosti. Dani bez merenja nisu nula."
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
        startStop.isEnabled = !fullBackupBusy
        findViewById<Button>(R.id.fullExportButton).isEnabled =
            !fullBackupBusy && !running
        findViewById<Button>(R.id.fullImportButton).isEnabled =
            !fullBackupBusy && !running
        status.text = when {
            testing -> "● Test prepoznavanja hrkanja je aktivan"
            running -> "● Praćenje je aktivno"
            else -> "● Praćenje nije aktivno"
        }
        if (!running) testStopRequested = false
        snoreTestButton.isEnabled = (!running || (testing && !testStopRequested)) &&
            !fullBackupBusy
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
        val rows = records()
        val latest = active ?: rows.firstOrNull()
        val selected = rows.firstOrNull { it.optLong("start") == selectedNightStart && it.optLong("end") == selectedNightEnd }
            ?: rows.firstOrNull()
        findViewById<TextView>(R.id.globalStatus).text = status.text
        if (active != null) {
            confidence.text = if (active.optBoolean("modelActive")) {
                "YAMNet lokalno • Snoring skor: ${"%.2f".format(Locale.US, active.optDouble("lastScore"))} (prag ${"%.2f".format(Locale.US, active.optDouble("threshold", 0.07))})"
            } else "AI model se učitava…"
        } else {
            confidence.text = "YAMNet • lokalna obrada; WAV snimci samo ako je opcija uključena"
        }
        showStats(latest, active != null)
        showDiagnostics(latest, active != null)
        showHistory()
        showArchive(rows, selected)
        if (currentTab == 1) {
            showCharts(selected)
            showAdvancedAnalytics(selected, false)
            showAlertResponse(selected)
        }
        if (currentTab == 2) showSavedClips()
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
        val aiGaps = if (item.has("inferenceGaps5s")) {
            "Zastoji AI analize duži od 5 s: ${item.optInt("inferenceGaps5s")} • " +
                "najduži razmak: ${item.optLong("maxInferenceGapMs") / 1000.0} s. " +
                "To nije potvrda da je GT 2 vibrirao."
        } else "Dijagnostika zastoja nije dostupna za ovu stariju sesiju."
        diagnostics.text = "Obrađeno prozora: $frames • prag: ${"%.2f".format(Locale.US, threshold)}\n$aiGaps\n$freshness"
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
            val metric = NightReport.metrics(item) ?: continue
            if (metric.score == null) continue
            if (latest != null && item.optString("sensitivity") != latest.optString("sensitivity")) continue
            if (recent.isNotEmpty() && item.optString("sensitivity") != recent.first().optString("sensitivity")) continue
            recent.add(item)
            if (recent.size == 7) break
        }
        val sorted = recent.reversed()
        val smallDate = SimpleDateFormat("dd.MM", Locale.forLanguageTag("sr-RS"))
        comparisonChart.showSeries(
            sorted.map { smallDate.format(Date(it.optLong("start"))) },
            sorted.map { kotlin.math.round(NightReport.metrics(it)!!.eventsPerHour).toInt() },
            sorted.isNotEmpty(), unit = "događaja po analiziranom satu"
        )
        comparisonDetails.text = if (recent.isEmpty()) {
            "Još nema uporedivih završenih noći. Praćenje je potrebno završiti da bi se pojavilo ovde."
        } else {
            val newest = NightReport.metrics(recent[0])!!.eventsPerHour
            val delta = if (recent.size >= 2) {
                val previous = NightReport.metrics(recent[1])!!.eventsPerHour
                "Poslednja noć: " + NightReport.decimal(newest) +
                    " događaja/h; razlika: " + NightReport.decimal(newest - previous) + " događaja/h."
            } else "Jedna uporediva noć: " + NightReport.decimal(newest) + " događaja/h."
            "$delta Grafikon: zaokruženi događaji po analiziranom satu, ista osetljivost, najmanje 30 min i 80% pokrivenosti."
        }
    }

    private fun showAlertResponse(item: JSONObject?) {
        val target = findViewById<TextView>(R.id.alertResponseDetails)
        val r = AlertResponseRecord.read(item)
        target.text = when {
            item?.optBoolean("isTest") == true -> "Probni režim ne beleži reakcije tokom noći."
            r == null -> "Dostupno za nove sesije od v0.8.1."
            else -> "Započete serije: ${r.started} • procenjene: ${r.assessed}\n" +
                "Smirilo se: ${r.quiet} • vratilo se: ${r.returned} • nastavilo se: ${r.continued}\n" +
                "Nepouzdano: ${r.inconclusive} • u toku: ${r.pending}\n" +
                "Smart režim: serije sa preskočenim nastavkom ${r.smartSuppressed}.\n" +
                "Smirivanje: 10 s bez detekcije u prvih 30 s; posmatranje ukupno 60 s. " +
                "Ovo ne potvrđuje vibraciju sata niti njen efekat."
        }
    }

    private fun showAdvancedAnalytics(item: JSONObject?, active: Boolean) {
        val view = findViewById<TextView>(R.id.advancedAnalytics)
        val result = item?.let { NightReport.metrics(it, if (active) System.currentTimeMillis() else it.optLong("end")) }
        val reviews = NightAnalytics.reviews(clipStore.list().map { it.label })
        val details = if (result == null) "Nema kompatibilnih podataka za naprednu analizu."
        else "Indeks hrkanja: " + (result.score?.let { "$it / 100" } ?: "nedovoljno podataka") +
            "\nDogađaji po analiziranom satu: " + NightReport.decimal(result.eventsPerHour) +
            "\nAnalizirano: " + NightReport.decimal(result.analysedHours) + " h • pokrivenost: " +
            NightReport.decimal(result.coverage * 100) + "%" +
            "\nPozitivni AI prozori: " + NightReport.decimal(result.positivePercent) + "%"
        view.text = details + "\n\nOcene svih sačuvanih isečaka: " +
            "${reviews.snore} hrkanje • ${reviews.falseAlarm} lažni • ${reviews.uncertain} nejasni • ${reviews.unreviewed} neocenjeni" +
            (reviews.confirmedPercent?.let { "\nKorisnik potvrdio $it% jasno ocenjenih isečaka (n=${reviews.reviewed})." } ?: "") +
            "\nOvo nije klinička tačnost AI modela. Indeks meri udeo pozitivnih prozora, ne jačinu zvuka ni apneju."
        findViewById<Button>(R.id.doctorPdfButton).isEnabled =
            !MonitorService.isActive && !fullBackupBusy && store.history().length() > 0
    }

    private fun createDocument(mime: String, title: String, requestCode: Int) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mime
            putExtra(Intent.EXTRA_TITLE, title)
        }
        startActivityForResult(intent, requestCode)
    }

    /**
     * Runs ZIP archiving off the UI thread. Prevents starting recording while
     * files and history are being moved, and handles failure visibly.
     */
    private fun runFullBackupTask(statusMessage: String, work: () -> String) {
        if (fullBackupBusy || MonitorService.isActive) {
            toast("Zaustavi praćenje ili sačekaj završetak prethodne operacije.")
            return
        }
        fullBackupBusy = true
        fullBackupStatus.text = statusMessage
        refresh()
        Thread({
            val result = try {
                if (MonitorService.isActive) {
                    "✗ Praćenje je aktivno. Prekini ga pre rezervne kopije."
                } else work()
            } catch (error: Exception) {
                "✗ Potpuna kopija nije uspela: " +
                    (error.message ?: error.javaClass.simpleName)
            }
            runOnUiThread {
                fullBackupBusy = false
                if (!isDestroyed && !isFinishing) {
                    fullBackupStatus.text = result
                    settings = Settings(this)
                    updateWindowButtons()
                    setupProfileControls()
                    findViewById<Switch>(R.id.phoneVibrationSwitch).isChecked =
                        settings.phoneVibration
                    findViewById<Switch>(R.id.smartFollowupsSwitch).isChecked = settings.smartFollowups
                    lastClipSignature = ""
                    refresh()
                }
            }
        }, "SnoreGuardFullBackup").start()
    }

    @Deprecated("Uses platform SAF callbacks to support Android 8 without AndroidX")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            when (requestCode) {
                REQUEST_DOCTOR_PDF -> {
                    check(!MonitorService.isActive && !fullBackupBusy) { "Zaustavi praćenje." }
                    val saved = store.history()
                    val nights = (0 until minOf(7, saved.length())).mapNotNull { saved.optJSONObject(it) }
                    val target = contentResolver.openOutputStream(uri, "w")
                        ?: throw IllegalStateException("Ne mogu da otvorim PDF odredište.")
                    target.use { NightReport.write(it, nights, clipStore.list()) }
                    exportStatus.text = "✓ PDF izveštaj je sačuvan."
                    toast("PDF izveštaj je sačuvan.")
                }
                REQUEST_FULL_EXPORT -> {
                    val archive = FullBackupArchive(applicationContext)
                    runFullBackupTask("Pravim kompletnu ZIP rezervnu kopiju…") {
                        val (nights, audio) = archive.exportTo(uri)
                        "✓ ZIP sačuvan: $nights završenih noći i $audio WAV snimaka. " +
                            "Sačuvaj datoteku na bezbednom mestu."
                    }
                }
                REQUEST_FULL_IMPORT -> {
                    if (MonitorService.isActive || fullBackupBusy) {
                        toast("Zaustavi praćenje pre importa.")
                        return
                    }
                    AlertDialog.Builder(this)
                        .setTitle("Import kompletne ZIP kopije?")
                        .setMessage("ZIP može da sadrži osetljive WAV snimke i beleške. " +
                            "Aplikacija će proveriti integritet i spojiti istoriju i " +
                            "snimke. Postojeći WAV snimci se ne prepisuju. " +
                            "Moguće je uvesti samo onoliko novih snimaka koliko ima " +
                            "slobodnih mesta do ograničenja od 40. " +
                            "Podešavanja iz kopije će biti vraćena.")
                        .setNegativeButton("Otkaži", null)
                        .setPositiveButton("Proveri i importuj") { _, _ ->
                            val archive = FullBackupArchive(applicationContext)
                            runFullBackupTask("Proveravam ZIP, SHA-256 i WAV snimke…") {
                                val r = archive.restoreFrom(uri)
                                "✓ Import završen: ${r.nightsImported} novih noći, " +
                                    "${r.clipsImported} WAV snimaka. " +
                                    "Postojećih snimaka: ${r.clipsAlreadyPresent}, " +
                                    "preskočeno zbog ograničenja: ${r.clipsSkippedAtCapacity}."
                            }
                        }.show()
                }
                REQUEST_EXPORT_CSV, REQUEST_BACKUP, REQUEST_EXPORT_CLIP_REVIEWS -> {
                    val content = when (requestCode) {
                        REQUEST_EXPORT_CSV -> store.exportCsv()
                        REQUEST_EXPORT_CLIP_REVIEWS -> clipStore.exportReviewsCsv()
                        else -> store.exportBackup()
                    }
                    val target = contentResolver.openOutputStream(uri, "w")
                        ?: throw IllegalStateException("Ne mogu da otvorim odredište.")
                    target.bufferedWriter(Charsets.UTF_8).use { it.write(content) }
                    if (requestCode == REQUEST_EXPORT_CLIP_REVIEWS) {
                        findViewById<TextView>(R.id.clipReviewStatus).text =
                            "✓ Ocene snimaka i beleške su sačuvane kao CSV. WAV se izvozi posebno."
                    } else {
                        exportStatus.text = if (requestCode == REQUEST_EXPORT_CSV) {
                            "✓ CSV statistika je sačuvana u izabranom folderu."
                        } else {
                            "✓ Export završen: ${store.history().length()} završenih noći sa podešavanjima. Sačuvaj JSON fajl."
                        }
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
                                findViewById<Switch>(R.id.smartFollowupsSwitch).isChecked = settings.smartFollowups
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
        val old = clipPlayer
        clipPlayer = null
        old?.close()
    }

    private fun showSavedClips() {
        val clips = clipStore.list()
        clipCountText.text = if (clips.isEmpty()) {
            "Nema snimaka. Uključi opciju čuvanja zvuka pre noćnog praćenja."
        } else {
            val reviewed = clips.count { it.label != ClipLabel.UNREVIEWED }
            "Sačuvano: ${clips.size} WAV snimaka • ocenjeno: $reviewed. " +
                "Dodirni snimak za slušanje, ocenu ili belešku."
        }
        val signature = clips.joinToString("|") { it.filename + ":" + it.label.name + ":" + it.note }
        if (signature == lastClipSignature) return
        lastClipSignature = signature
        clipList.removeAllViews()
        val format = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.forLanguageTag("sr-RS"))
        for (clip in clips.take(40)) {
            val button = Button(this).apply {
                text = format.format(Date(clip.recordedAt)) + "  •  " +
                    (clip.durationMs / 1000) + " s • " + clip.label.display +
                    if (clip.note.isBlank()) "" else " • Beleška: " + clip.note.take(45)
                isAllCaps = false
                setTextColor(getColor(R.color.text_primary))
                setOnClickListener { chooseClipAction(clip) }
            }
            clipList.addView(button)
        }
    }

    private fun editClipLabel(clip: SnoreClip) {
        val labels = ClipLabel.values()
        var selected = clip.label.ordinal
        AlertDialog.Builder(this)
            .setTitle("Ocenjivanje snimka")
            .setMessage("Ovo je tvoja subjektivna procena, ne automatska dijagnoza.")
            .setSingleChoiceItems(labels.map { it.display }.toTypedArray(), selected) { _, which ->
                selected = which
            }
            .setNegativeButton("Otkaži", null)
            .setPositiveButton("Sačuvaj") { _, _ ->
                if (clipStore.setReview(clip.filename, labels[selected], clip.note)) {
                    lastClipSignature = ""
                    showSavedClips()
                } else toast("Ocena nije sačuvana.")
            }.show()
    }

    private fun editClipNote(clip: SnoreClip) {
        val input = EditText(this).apply {
            setText(clip.note)
            hint = "Npr. spavao sam na leđima"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            filters = arrayOf(InputFilter.LengthFilter(160))
            minLines = 2
            maxLines = 4
            setSelection(text.length)
        }
        val padding = (16 * resources.displayMetrics.density).toInt()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle("Beleška uz snimak")
            .setView(container)
            .setNegativeButton("Otkaži", null)
            .setPositiveButton("Sačuvaj") { _, _ ->
                if (clipStore.setReview(clip.filename, clip.label, input.text.toString())) {
                    lastClipSignature = ""
                    showSavedClips()
                } else toast("Beleška nije sačuvana.")
            }.show()
    }

    private fun chooseClipAction(clip: SnoreClip) {
        AlertDialog.Builder(this)
            .setTitle("Zvučni snimak • " + clip.label.display)

            .setItems(arrayOf("Preslušaj", "Označi: hrkanje / lažni alarm",
                "Dodaj ili izmeni belešku", "Podeli WAV sa lekarom", "Obriši")) { _, which ->
                val file = clipStore.fileFor(clip.filename)
                if (file == null) {
                    toast("Snimak nije dostupan.")
                    return@setItems
                }
                when (which) {
                    0 -> if (MonitorService.isActive) {
                        toast("Zaustavi praćenje pre preslušavanja da zvuk ne utiče na detekciju.")
                    } else try {
                        stopPlayback()
                        clipPlayer = ClipPlayer(this, file)
                    } catch (error: Exception) {
                        stopPlayback()
                        toast("Ne može da se pusti snimak: " + error.message)
                    }
                    1 -> editClipLabel(clip)
                    2 -> editClipNote(clip)
                    3 -> try {
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
                    4 -> if (MonitorService.isActive) {
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
        private const val REQUEST_EXPORT_CLIP_REVIEWS = 5004
        private const val REQUEST_DOCTOR_PDF = 5005
        private const val REQUEST_FULL_EXPORT = 6001
        private const val REQUEST_FULL_IMPORT = 6002
    }
}
