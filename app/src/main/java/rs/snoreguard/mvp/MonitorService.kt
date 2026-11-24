package rs.snoreguard.mvp

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import java.time.LocalTime
import kotlin.math.max

/** One user-initiated, microphone foreground service. No scheduled background microphone starts. */
class MonitorService : Service() {
    @Volatile private var recording = false
    @Volatile private var recorder: AudioRecord? = null
    private var worker: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var store: SessionStore
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var lastPositiveRealtime = -1L
    // Short quiet gaps are normal between inhalations. Do not cancel after a
    // single exhale; stop later burst steps after prolonged silence instead.
    private val burstQuietTimeoutMillis = 20_000L
    private lateinit var alertBurst: AlarmBurst
    private var activeSensitivity = Sensitivity.HIGH
    private var activeMode = AlertMode.STRONG
    @Volatile private var stoppedByUser = false
    private lateinit var clipStore: SnoreClipStore
    private var sessionTesting = false
    private var testStartedElapsed = 0L
    private var activeWindowStart = LocalTime.of(22, 0)
    private var activeWindowEnd = LocalTime.of(8, 0)
    private var saveClips = false
    private var smartFollowups = false
    private var response = AlertResponseTracker { SystemClock.elapsedRealtime() }

    private fun allowedNow(): Boolean =
        if (sessionTesting) SystemClock.elapsedRealtime() - testStartedElapsed < TEST_DURATION_MS
        else NightWindow.isNight(LocalTime.now(), activeWindowStart, activeWindowEnd)

    private fun newBurst(mode: AlertMode) = AlarmBurst(
        scheduler = BurstScheduler { delay, action -> mainHandler.postDelayed({ action() }, delay) },
        canContinue = {
            recording && allowedNow() &&
                lastPositiveRealtime >= 0L &&
                SystemClock.elapsedRealtime() - lastPositiveRealtime <= burstQuietTimeoutMillis
        },
        canDeliverStep = { step -> step == 1 || !smartFollowups || sessionTesting || !response.suppressFollowup() },
        emit = { step ->
            try {
                check(AlertNotifier.canPostWatchAlert(this)) { "Android obaveštenja nisu dozvoljena." }
                AlertNotifier.sendWatchAlert(this, test = sessionTesting, step = step, totalSteps = mode.steps)
                store.incrementNotifications()
                if (step == 1 && !sessionTesting) response.firstPosted()
            } catch (e: Exception) {
                store.fail("Upozorenje nije poslato: ${e.message}")
                throw e // AlarmBurst will cancel the rest of this series.
            }
        },
        totalAlerts = mode.steps,
        intervalMillis = mode.intervalMillis
    )

    override fun onCreate() {
        super.onCreate()
        store = SessionStore(this)
        clipStore = SnoreClipStore(this)
        alertBurst = newBurst(AlertMode.STRONG)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> requestStop()
            ACTION_START -> startMonitor(false)
            ACTION_TEST -> startMonitor(true)
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startMonitor(test: Boolean) {
        if (recording || isActive) return
        val preferences = Settings(this)
        val start = preferences.windowStart()
        val end = preferences.windowEnd()
        if (!test && !NightWindow.isNight(LocalTime.now(), start, end)) {
            store.fail("Praćenje je dozvoljeno samo u izabranom intervalu " +
                Settings.timeLabel(preferences.windowStartMinutes) + "–" +
                Settings.timeLabel(preferences.windowEndMinutes) + ".")
            stopSelf()
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            store.fail("Nedostaje dozvola za mikrofon.")
            stopSelf()
            return
        }
        sessionTesting = test
        activeWindowStart = start
        activeWindowEnd = end
        testStartedElapsed = SystemClock.elapsedRealtime()
        try {
            AlertNotifier.ensureChannels(this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(AlertNotifier.ONGOING_NOTIFICATION,
                    AlertNotifier.monitorNotification(this, testing = sessionTesting),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(AlertNotifier.ONGOING_NOTIFICATION, AlertNotifier.monitorNotification(this, testing = sessionTesting))
            }
        } catch (e: Exception) {
            store.fail("Nije moguće započeti praćenje: ${e.message}")
            stopSelf()
            return
        }
        activeSensitivity = preferences.sensitivity
        activeMode = preferences.alertMode
        saveClips = preferences.saveSnoreClips && !test
        smartFollowups = preferences.smartFollowups && !test
        response = AlertResponseTracker { SystemClock.elapsedRealtime() }
        stoppedByUser = false
        alertBurst = newBurst(activeMode)
        store.recoverInterrupted()
        store.begin(activeSensitivity, activeMode,
            preferences.windowStartMinutes, preferences.windowEndMinutes, isTest = sessionTesting)
        lastPositiveRealtime = -1L
        recording = true
        isActive = true
        isTestSession = sessionTesting
        worker = Thread({ recordLoop() }, "SnoreGuardAudio").apply { start() }
    }

    private fun recordLoop() {
        val engine = SnoreEngine(cooldownMillis = 60_000L)
        var classifier: YamnetSnoreDetector? = null
        var clipCapture: SnoreClipCapture? = null
        var lastScore = 0f
        val analysisGaps = AnalysisHeartbeat()
        var finishReason = if (sessionTesting) "Test je završen" else "Izabrani interval je završen"
        try {
            classifier = YamnetSnoreDetector(this)
            clipCapture = SnoreClipCapture(clipStore, saveClips) { message -> store.fail(message) }
            val minBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            check(minBytes > 0) { "Telefon ne podržava mikrofon na 16 kHz" }
            val bytes = max(minBytes, YamnetSnoreDetector.SAMPLES * 2 * 2)
            val audio = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build())
                .setBufferSizeInBytes(bytes)
                .build()
            check(audio.state == AudioRecord.STATE_INITIALIZED) { "Mikrofon nije inicijalizovan" }
            recorder = audio

            val manager = getSystemService(PowerManager::class.java)
            wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SnoreGuard:NightAudio").apply {
                acquire(11L * 60 * 60 * 1000) // Timeout safety cap; recommend charging phone.
            }
            audio.startRecording()
            check(audio.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Mikrofon ne snima" }
            store.update(engine, lastScore, modelActive = true,
                maxInferenceGapMs = analysisGaps.maxGapMillis,
                inferenceGaps5s = analysisGaps.stalls, responseSnapshot = response.snapshot())

            // YAMNet requires 15,600 samples (~975 ms). Re-analyse every
            // 4,000 NEW samples (250 ms), retaining the previous 11,600.
            // This catches short inhalations at a finer resolution without
            // treating overlapped positive classifications as new breaths.
            val rolling = SlidingAudioWindow(
                frameSamples = YamnetSnoreDetector.SAMPLES,
                hopSamples = HOP_SAMPLES
            )
            val audioStartElapsed = SystemClock.elapsedRealtime()
            var sampleTimeMs = 975L
            var firstFrame = true
            while (recording && allowedNow()) {
                val offset: Int
                val count: Int
                if (firstFrame) {
                    offset = 0
                    count = rolling.frameSamples
                } else {
                    rolling.shiftForNextHop()
                    offset = rolling.tailOffset
                    count = rolling.hopSamples
                    sampleTimeMs += WINDOW_HOP_MS
                }
                var filled = 0
                while (filled < count && recording) {
                    val received = audio.read(
                        rolling.samples, offset + filled, count - filled, AudioRecord.READ_BLOCKING
                    )
                    if (received <= 0) {
                        if (recording) throw IllegalStateException("AudioRecord greška: $received")
                        break
                    }
                    filled += received
                }
                if (!recording || filled < count) break
                firstFrame = false
                // Only NEW audio samples enter the recording buffer; overlapping
                // 975 ms analysis frames must never duplicate the WAV waveform.
                clipCapture.accept(rolling.samples, offset, count)

                lastScore = classifier.classify(rolling.samples)
                val completedAt = SystemClock.elapsedRealtime()
                analysisGaps.record(completedAt)
                val snore = lastScore >= activeSensitivity.modelThreshold
                if (!sessionTesting) response.frame(snore,
                    kotlin.math.abs(completedAt - (audioStartElapsed + sampleTimeMs)) <= AlertResponseTracker.MAX_GAP_MS)
                if (snore) lastPositiveRealtime = SystemClock.elapsedRealtime()

                // Detector uses AUDIO sample time, preventing slow inference
                // from turning one long positive breath into two separate ones.
                val beforeEvents = engine.episodes
                val alert = engine.process(
                    isSnore = snore,
                    mayAlert = allowedNow(),
                    elapsedMillis = audioStartElapsed + sampleTimeMs
                )
                if (engine.episodes > beforeEvents) {
                    // Distinct-event timestamp, never the overlapping window count.
                    store.recordSnoreAt(System.currentTimeMillis())
                }
                if (alert) {
                    clipCapture.trigger()
                    store.update(engine, lastScore, modelActive = true,
                maxInferenceGapMs = analysisGaps.maxGapMillis,
                inferenceGaps5s = analysisGaps.stalls, responseSnapshot = response.snapshot())
                    mainHandler.post { if (recording) alertBurst.start() }
                }
                if (engine.analysedFrames % 20 == 0) {
                    store.update(engine, lastScore, modelActive = true,
                maxInferenceGapMs = analysisGaps.maxGapMillis,
                inferenceGaps5s = analysisGaps.stalls, responseSnapshot = response.snapshot())
                }
            }
        } catch (e: Exception) {
            finishReason = "Prekinuto zbog greške"
            Log.e("SnoreGuard", "Monitoring failed", e)
            store.fail("Praćenje prekinuto: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            recording = false
            mainHandler.post { alertBurst.cancel() }
            try { clipCapture?.close() } catch (e: Exception) {
                store.fail("Snimanje isečka je prekinuto: " + (e.message ?: "greška"))
            }
            try { recorder?.stop() } catch (_: Exception) { }
            try { recorder?.release() } catch (_: Exception) { }
            recorder = null
            try { classifier?.close() } catch (_: Exception) { }
            try { wakeLock?.let { if (it.isHeld) it.release() } } catch (_: Exception) { }
            wakeLock = null
            store.update(engine, lastScore, modelActive = classifier != null,
                maxInferenceGapMs = analysisGaps.maxGapMillis,
                inferenceGaps5s = analysisGaps.stalls, responseSnapshot = response.snapshot())
            store.saveFinalResponse(response.finish())
            store.finish(if (stoppedByUser) "Ručno zaustavljeno" else finishReason)
            isActive = false
            isTestSession = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun requestStop() {
        stoppedByUser = true
        recording = false
        mainHandler.post { alertBurst.cancel() }
        try { recorder?.stop() } catch (_: Exception) { }
        if (!isActive) stopSelf()
    }

    override fun onDestroy() {
        recording = false
        mainHandler.post { alertBurst.cancel() }
        try { recorder?.stop() } catch (_: Exception) { }
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "rs.snoreguard.mvp.START"
        const val ACTION_STOP = "rs.snoreguard.mvp.STOP"
        const val ACTION_TEST = "rs.snoreguard.mvp.TEST"
        const val TEST_DURATION_MS = 90_000L
        private const val SAMPLE_RATE = 16000
        private const val HOP_SAMPLES = 4_000
        private const val WINDOW_HOP_MS = 250L
        @Volatile var isActive: Boolean = false
            private set
        @Volatile var isTestSession: Boolean = false
            private set
    }
}
