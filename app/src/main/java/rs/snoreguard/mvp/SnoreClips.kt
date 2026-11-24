package rs.snoreguard.mvp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date

/** Private on-device WAV snippets; never public until shared by the user. */
data class SnoreClip(val filename: String, val recordedAt: Long, val durationMs: Long, val bytes: Long)

class SnoreClipStore(context: Context) {
    private val lock = Any()
    private val prefs = context.getSharedPreferences("snoreguard_audio_clips", Context.MODE_PRIVATE)
    private val dir = File(context.filesDir, "snore_clips")
    private val validFile = Regex("^snore_[0-9]{8}_[0-9]{6}_[a-f0-9]{32}\\.wav$")

    fun list(): List<SnoreClip> = synchronized(lock) {
        val array = try { JSONArray(prefs.getString("clips", "[]")) } catch (_: Exception) { JSONArray() }
        val clips = ArrayList<SnoreClip>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val name = item.optString("filename")
            if (!validFile.matches(name) || !File(dir, name).isFile) continue
            clips.add(
                SnoreClip(name, item.optLong("recordedAt"),
                    item.optLong("durationMs"), item.optLong("bytes"))
            )
        }
        clips.sortedByDescending { it.recordedAt }
    }

    /** Reject arbitrary paths and identifiers that are not present in local metadata. */
    fun fileFor(filename: String): File? = synchronized(lock) {
        if (!validFile.matches(filename)) return@synchronized null
        if (list().none { it.filename == filename }) return@synchronized null
        File(dir, filename).takeIf { it.isFile }
    }

    fun save(pcm: ShortArray, length: Int, recordedAt: Long): SnoreClip = synchronized(lock) {
        require(length in (SAMPLE_RATE * 2)..pcm.size) { "WAV snippet too short or invalid" }
        require(length <= SAMPLE_RATE * 15) { "WAV snippet exceeds the maximum" }
        check(dir.isDirectory || dir.mkdirs()) { "Cannot create private audio folder" }
        val datePart = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date(recordedAt))
        val fileName = "snore_" + datePart + "_" +
            UUID.randomUUID().toString().replace("-", "") + ".wav"
        val dest = File(dir, fileName)
        val temp = File(dir, fileName + ".tmp")
        try {
            FileOutputStream(temp).use { out ->
                out.write(wavHeader(length))
                val buffer = ByteArray(4096)
                var index = 0
                while (index < length) {
                    val n = minOf(buffer.size / 2, length - index)
                    for (i in 0 until n) {
                        val sample = pcm[index + i].toInt()
                        buffer[2 * i] = (sample and 0xFF).toByte()
                        buffer[2 * i + 1] = ((sample ushr 8) and 0xFF).toByte()
                    }
                    out.write(buffer, 0, n * 2)
                    index += n
                }
                out.fd.sync()
            }
            check(temp.renameTo(dest)) { "Cannot finalize WAV file" }
            val newClip = SnoreClip(fileName, recordedAt, length * 1000L / SAMPLE_RATE, dest.length())
            val previous = list()
            val retained = (listOf(newClip) + previous).take(MAX_STORED_CLIPS)
            val array = JSONArray()
            for (clip in retained) {
                array.put(JSONObject().put("filename", clip.filename)
                    .put("recordedAt", clip.recordedAt)
                    .put("durationMs", clip.durationMs)
                    .put("bytes", clip.bytes))
            }
            check(prefs.edit().putString("clips", array.toString()).commit()) {
                "Cannot save clip metadata"
            }
            val keep = retained.map { it.filename }.toHashSet()
            for (old in previous) {
                if (old.filename !in keep) File(dir, old.filename).delete()
            }
            newClip
        } catch (e: Exception) {
            temp.delete()
            dest.delete()
            throw e
        }
    }

    fun delete(filename: String): Boolean = synchronized(lock) {
        if (!validFile.matches(filename)) return@synchronized false
        val existing = list()
        if (existing.none { it.filename == filename }) return@synchronized false
        val updated = JSONArray()
        for (clip in existing.filterNot { it.filename == filename }) {
            updated.put(JSONObject().put("filename", clip.filename)
                .put("recordedAt", clip.recordedAt)
                .put("durationMs", clip.durationMs)
                .put("bytes", clip.bytes))
        }
        if (!prefs.edit().putString("clips", updated.toString()).commit()) return@synchronized false
        !File(dir, filename).exists() || File(dir, filename).delete()
    }

    fun deleteAll(): Boolean = synchronized(lock) {
        val existing = list()
        if (!prefs.edit().putString("clips", "[]").commit()) return@synchronized false
        existing.forEach { File(dir, it.filename).delete() }
        true
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val MAX_STORED_CLIPS = 40

        /** 16-bit signed mono PCM, 16 kHz, little endian RIFF/WAVE. */
        fun wavHeader(samples: Int): ByteArray {
            require(samples in 0..240_000)
            val dataBytes = samples * 2
            val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            h.put("RIFF".toByteArray(Charsets.US_ASCII))
            h.putInt(36 + dataBytes)
            h.put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
            h.putInt(16)
            h.putShort(1.toShort())
            h.putShort(1.toShort())
            h.putInt(SAMPLE_RATE)
            h.putInt(SAMPLE_RATE * 2)
            h.putShort(2.toShort())
            h.putShort(16.toShort())
            h.put("data".toByteArray(Charsets.US_ASCII))
            h.putInt(dataBytes)
            return h.array()
        }
    }
}

/**
 * Single-threaded 3 s pre-roll + 9 s post-roll.
 * Only allocated if the user opted in to recording.
 */
class SnoreClipCapture(
    private val store: SnoreClipStore,
    private val enabled: Boolean,
    private val error: (String) -> Unit
) {
    private val ring = ShortArray(if (enabled) PRE_SAMPLES else 0)
    private var ringWrite = 0
    private var ringFilled = 0
    private var capture: ShortArray? = null
    private var captureLength = 0
    private var afterSamples = 0
    private var recordedAt = 0L
    private var savedThisSession = 0
    private var lastCaptureAt = -1L

    fun accept(samples: ShortArray, offset: Int, length: Int) {
        if (!enabled) return
        require(offset >= 0 && length >= 0 && offset + length <= samples.size)
        for (i in offset until offset + length) {
            val sample = samples[i]
            ring[ringWrite] = sample
            ringWrite = (ringWrite + 1) % ring.size
            if (ringFilled < ring.size) ringFilled++
            val active = capture
            if (active != null && afterSamples < POST_SAMPLES) {
                active[captureLength++] = sample
                afterSamples++
                if (afterSamples >= POST_SAMPLES) finish()
            }
        }
    }

    /** Called after two distinct snore events confirm an alert. */
    fun trigger(timestamp: Long = System.currentTimeMillis()) {
        if (!enabled || capture != null || savedThisSession >= MAX_PER_SESSION) return
        if (lastCaptureAt > 0L && timestamp - lastCaptureAt < MIN_CLIP_INTERVAL_MS) return
        lastCaptureAt = timestamp
        val buf = ShortArray(PRE_SAMPLES + POST_SAMPLES)
        val first = if (ringFilled == ring.size) ringWrite else 0
        for (i in 0 until ringFilled) buf[i] = ring[(first + i) % ring.size]
        captureLength = ringFilled
        afterSamples = 0
        recordedAt = timestamp
        capture = buf
    }

    fun close() {
        if (capture != null) finish()
    }

    private fun finish() {
        val buf = capture ?: return
        capture = null
        if (captureLength < SnoreClipStore.SAMPLE_RATE * 2) return
        try {
            store.save(buf, captureLength, recordedAt)
            savedThisSession++
        } catch (ex: Exception) {
            error("Snimak nije sačuvan: " + (ex.message ?: "greška čuvanja"))
        }
    }

    companion object {
        const val PRE_SAMPLES = 3 * SnoreClipStore.SAMPLE_RATE
        const val POST_SAMPLES = 9 * SnoreClipStore.SAMPLE_RATE
        const val MAX_PER_SESSION = 10
        const val MIN_CLIP_INTERVAL_MS = 20 * 60 * 1000L
    }
}
