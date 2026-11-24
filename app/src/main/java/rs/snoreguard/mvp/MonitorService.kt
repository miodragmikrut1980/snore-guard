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
    private val alertBurst = AlarmBurst(
        scheduler = BurstScheduler { delay, action -> mainHandler.postDelayed({ action() }, delay) },
        canContinue = {
            recording && NightWindow.isNight(LocalTime.now()) &&
                lastPositiveRealtime >= 0L &&
                SystemClock.elapsedRealtime() - lastPositiveRealtime <= burstQuietTimeoutMillis
        },
        emit = { step ->
            try {
                AlertNotifier.sendWatchAlert(this, test = false, step = step)
                store.incrementNotifications()
            } catch (e: Exception) {
                store.fail("Upozorenje nije poslato: ${e.message}")
                throw e // AlarmBurst will cancel the rest of this series.
            }
        }
    )

    override fun onCreate() {
        super.onCreate()
        store = SessionStore(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> requestStop()
            ACTION_START -> startMonitor()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startMonitor() {
        if (recording) return
        if (!NightWindow.isNight(LocalTime.now())) {
            store.fail("Praćenje je dozvoljeno samo između 22:00 i 08:00.")
            stopSelf()
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            store.fail("Nedostaje dozvola za mikrofon.")
            stopSelf()
            return
        }
        try {
            AlertNotifier.ensureChannels(this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(AlertNotifier.ONGOING_NOTIFICATION,
                    AlertNotifier.monitorNotification(this),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(AlertNotifier.ONGOING_NOTIFICATION, AlertNotifier.monitorNotification(this))
            }
        } catch (e: Exception) {
            store.fail("Nije moguće započeti praćenje: ${e.message}")
            stopSelf()
            return
        }
        store.recoverInterrupted()
        store.begin()
        lastPositiveRealtime = -1L
        recording = true
        isActive = true
        worker = Thread({ recordLoop() }, "SnoreGuardAudio").apply { start() }
    }

    private fun recordLoop() {
        val engine = SnoreEngine(cooldownMillis = 60_000L)
        var classifier: YamnetSnoreDetector? = null
        var lastScore = 0f
        try {
            classifier = YamnetSnoreDetector(this)
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
            store.update(engine, lastScore, modelActive = true)

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
            while (recording && NightWindow.isNight(LocalTime.now())) {
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

                lastScore = classifier.classify(rolling.samples)
                val snore = lastScore >= Settings.MODEL_THRESHOLD
                if (snore) lastPositiveRealtime = SystemClock.elapsedRealtime()

                // Detector uses AUDIO sample time, preventing slow inference
                // from turning one long positive breath into two separate ones.
                val alert = engine.process(
                    isSnore = snore,
                    mayAlert = NightWindow.isNight(LocalTime.now()),
                    elapsedMillis = audioStartElapsed + sampleTimeMs
                )
                if (alert) {
                    store.update(engine, lastScore, modelActive = true)
                    mainHandler.post { if (recording) alertBurst.start() }
                }
                if (engine.analysedFrames % 20 == 0) {
                    store.update(engine, lastScore, modelActive = true)
                }
            }
        } catch (e: Exception) {
            Log.e("SnoreGuard", "Monitoring failed", e)
            store.fail("Praćenje prekinuto: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            recording = false
            mainHandler.post { alertBurst.cancel() }
            try { recorder?.stop() } catch (_: Exception) { }
            try { recorder?.release() } catch (_: Exception) { }
            recorder = null
            try { classifier?.close() } catch (_: Exception) { }
            try { wakeLock?.let { if (it.isHeld) it.release() } } catch (_: Exception) { }
            wakeLock = null
            store.update(engine, lastScore, modelActive = classifier != null)
            store.finish()
            isActive = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun requestStop() {
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
        private const val SAMPLE_RATE = 16000
        private const val HOP_SAMPLES = 4_000
        private const val WINDOW_HOP_MS = 250L
        @Volatile var isActive: Boolean = false
            private set
    }
}
