package rs.snoreguard.mvp

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class FullRestoreResult(
    val nightsImported: Int,
    val clipsImported: Int,
    val clipsAlreadyPresent: Int,
    val clipsSkippedAtCapacity: Int
)

/** ZIP is unencrypted: keep it private and share only intentionally. */
class FullBackupArchive(context: Context) {
    private val app = context.applicationContext
    private val sessions = SessionStore(app)
    private val clips = SnoreClipStore(app)

    fun exportTo(uri: Uri): Pair<Int, Int> {
        val stats = sessions.exportBackup().toByteArray(Charsets.UTF_8)
        require(stats.size <= MAX_STATS_BYTES) { "Statistika je prevelika za ZIP kopiju." }
        val selected = clips.list()
        require(selected.size <= SnoreClipStore.MAX_STORED_CLIPS)
        val listed = JSONArray()
        val files = ArrayList<Pair<SnoreClip, File>>()
        for (clip in selected) {
            val f = clips.fileFor(clip.filename)
                ?: throw IllegalStateException("Snimak nije dostupan: " + clip.filename)
            require(isSafeFilename(clip.filename) && f.length() in MIN_WAV_BYTES..MAX_WAV_BYTES) {
                "Neispravan WAV u arhivi: " + clip.filename
            }
            validatePcmWav(f)
            listed.put(JSONObject()
                .put("filename", clip.filename)
                .put("recordedAt", clip.recordedAt)
                .put("durationMs", clip.durationMs)
                .put("bytes", f.length())
                .put("label", clip.label.name)
                .put("note", clip.note)
                .put("sha256", digestFile(f)))
            files.add(clip to f)
        }
        val manifest = JSONObject()
            .put("format", FORMAT)
            .put("schema", SCHEMA)
            .put("createdAt", System.currentTimeMillis())
            .put("statisticsSha256", digestBytes(stats))
            .put("clips", listed)
            .toString(2).toByteArray(Charsets.UTF_8)
        require(manifest.size <= MAX_MANIFEST_BYTES)
        val inputBytes = manifest.size.toLong() + stats.size + files.sumOf { it.second.length() }
        require(inputBytes <= MAX_TOTAL_BYTES) { "Rezervna kopija je prevelika." }

        val stream = app.contentResolver.openOutputStream(uri, "w")
            ?: throw IllegalStateException("Izabrani fajl nije dostupan za pisanje.")
        ZipOutputStream(BufferedOutputStream(stream)).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(manifest)
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(STATISTICS))
            zip.write(stats)
            zip.closeEntry()
            for ((clip, f) in files) {
                zip.putNextEntry(ZipEntry("clips/" + clip.filename))
                f.inputStream().use { input -> input.copyTo(zip, 8_192) }
                zip.closeEntry()
            }
        }
        return sessions.history().length() to files.size
    }

    fun restoreFrom(uri: Uri): FullRestoreResult {
        val staging = File(app.cacheDir, "snoreguard_restore_" + UUID.randomUUID())
        check(staging.mkdirs()) { "Nije moguce pripremiti privremeni folder." }
        try {
            val found = HashSet<String>()
            var total = 0L
            var manifestBytes: ByteArray? = null
            var statisticsBytes: ByteArray? = null
            val savedFiles = HashMap<String, File>()
            val source = app.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Izabrana kopija ne moze da se procita.")
            ZipInputStream(BufferedInputStream(source)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(!entry.isDirectory && found.size < MAX_ENTRIES) {
                        "ZIP ima previse stavki ili foldere."
                    }
                    val name = entry.name
                    require(found.add(name)) { "Duplirana stavka u ZIP arhivi." }
                    val limit = when {
                        name == MANIFEST -> MAX_MANIFEST_BYTES.toLong()
                        name == STATISTICS -> MAX_STATS_BYTES.toLong()
                        name.startsWith("clips/") && isSafeFilename(name.removePrefix("clips/")) ->
                            MAX_WAV_BYTES
                        else -> throw IllegalArgumentException("Nepoznata ili nebezbedna ZIP putanja.")
                    }
                    val bytes = ByteArrayOutputStream()
                    val wav = if (name.startsWith("clips/"))
                        File(staging, name.removePrefix("clips/")) else null
                    val destination = if (wav != null) FileOutputStream(wav) else bytes
                    var entrySize = 0L
                    destination.use { output ->
                        val chunk = ByteArray(8_192)
                        while (true) {
                            val n = zip.read(chunk)
                            if (n < 0) break
                            entrySize += n
                            total += n
                            require(entrySize <= limit && total <= MAX_TOTAL_BYTES) {
                                "ZIP sadrzi prevelike stavke."
                            }
                            output.write(chunk, 0, n)
                        }
                    }
                    zip.closeEntry() // Also checks ZIP CRC where available.
                    when (name) {
                        MANIFEST -> manifestBytes = bytes.toByteArray()
                        STATISTICS -> statisticsBytes = bytes.toByteArray()
                        else -> savedFiles[name.removePrefix("clips/")] = wav!!
                    }
                }
            }
            val rawManifest = manifestBytes ?: throw IllegalArgumentException("Nedostaje manifest.")
            val rawStats = statisticsBytes ?: throw IllegalArgumentException("Nedostaje statistika.")
            val metadata = JSONObject(String(rawManifest, Charsets.UTF_8))
            require(metadata.optString("format") == FORMAT && metadata.optInt("schema") == SCHEMA) {
                "Nepodrzan SnoreGuard ZIP format."
            }
            require(digestBytes(rawStats).equals(metadata.optString("statisticsSha256"), true)) {
                "Statistika nije prosla proveru integriteta."
            }
            val stats = String(rawStats, Charsets.UTF_8)
            sessions.validateImportBackup(stats) // No mutation before every entry validates.
            val manifestClips = metadata.optJSONArray("clips")
                ?: throw IllegalArgumentException("Nedostaju podaci o snimcima.")
            require(manifestClips.length() <= SnoreClipStore.MAX_STORED_CLIPS &&
                manifestClips.length() == savedFiles.size) {
                "Neslaganje WAV fajlova i liste snimaka."
            }
            val staged = ArrayList<StagedSnoreClip>()
            val unique = HashSet<String>()
            for (i in 0 until manifestClips.length()) {
                val item = manifestClips.optJSONObject(i)
                    ?: throw IllegalArgumentException("Neispravan zapis o snimku.")
                val filename = item.optString("filename")
                require(isSafeFilename(filename) && unique.add(filename)) {
                    "Neispravno ime ili duplikat WAV snimka."
                }
                val file = savedFiles[filename]
                    ?: throw IllegalArgumentException("Nedostaje WAV snimak iz kopije.")
                require(file.length() in MIN_WAV_BYTES..MAX_WAV_BYTES &&
                    file.length() == item.optLong("bytes")) {
                    "Velicina WAV snimka se ne poklapa."
                }
                validatePcmWav(file)
                require(digestFile(file).equals(item.optString("sha256"), true)) {
                    "WAV snimak nije prosao SHA-256 proveru."
                }
                val labelName = item.optString("label", ClipLabel.UNREVIEWED.name)
                require(ClipLabel.values().any { it.name == labelName }) {
                    "Nepoznata oznaka u kopiji."
                }
                val at = item.optLong("recordedAt")
                require(at > 0L) { "Neispravan datum snimka." }
                val duration = (file.length() - 44) * 1000L / (SnoreClipStore.SAMPLE_RATE * 2)
                require(kotlin.math.abs(duration - item.optLong("durationMs")) <= 1L) {
                    "Neispravno trajanje WAV snimka."
                }
                val note = item.optString("note")
                require(note.length <= 160) { "Predugacka beleska." }
                staged.add(StagedSnoreClip(SnoreClip(filename, at, duration, file.length(),
                    ClipLabel.valueOf(labelName), note), file))
            }
            // Never overwrite existing WAVs: use only free slots.
            val clipResult = clips.importStaged(staged)
            val nights = sessions.importBackup(stats)
            return FullRestoreResult(nights, clipResult.imported,
                clipResult.alreadyPresent, clipResult.skippedDueToLimit)
        } finally {
            staging.deleteRecursively()
        }
    }

    companion object {
        private const val FORMAT = "SnoreGuard-FullBackup"
        private const val SCHEMA = 1
        private const val MANIFEST = "manifest.json"
        private const val STATISTICS = "statistics.json"
        private const val MAX_MANIFEST_BYTES = 64 * 1024
        private const val MAX_STATS_BYTES = 2_000_000
        private const val MIN_WAV_BYTES = 44L + 2L * SnoreClipStore.SAMPLE_RATE * 2
        private const val MAX_WAV_BYTES = 44L + 2L * SnoreClipStore.SAMPLE_RATE * 15
        private const val MAX_TOTAL_BYTES = 25L * 1024 * 1024
        private const val MAX_ENTRIES = 2 + SnoreClipStore.MAX_STORED_CLIPS
        private val filePattern = Regex("^snore_[0-9]{8}_[0-9]{6}_[a-f0-9]{32}\\.wav$")

        fun isSafeFilename(name: String): Boolean = filePattern.matches(name)

        fun validatePcmWav(file: File) {
            require(file.length() in MIN_WAV_BYTES..MAX_WAV_BYTES) {
                "WAV je prevelik ili prekratak."
            }
            val header = ByteArray(44)
            DataInputStream(file.inputStream()).use { it.readFully(header) }
            val b = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            fun ascii(start: Int, count: Int) =
                String(header, start, count, Charsets.US_ASCII)
            require(ascii(0, 4) == "RIFF" && ascii(8, 4) == "WAVE" &&
                ascii(12, 4) == "fmt " && ascii(36, 4) == "data" &&
                b.getInt(4).toLong() == file.length() - 8L &&
                b.getInt(16) == 16 &&
                b.getShort(20).toInt() == 1 &&
                b.getShort(22).toInt() == 1 &&
                b.getInt(24) == SnoreClipStore.SAMPLE_RATE &&
                b.getInt(28) == 2 * SnoreClipStore.SAMPLE_RATE &&
                b.getShort(32).toInt() == 2 &&
                b.getShort(34).toInt() == 16 &&
                b.getInt(40).toLong() == file.length() - 44L) {
                "Neispravan PCM WAV format."
            }
        }

        fun digestBytes(raw: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(raw)
                .joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) }

        fun digestFile(file: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { stream ->
                val chunk = ByteArray(8_192)
                while (true) {
                    val n = stream.read(chunk)
                    if (n < 0) break
                    md.update(chunk, 0, n)
                }
            }
            return md.digest().joinToString("") {
                "%02x".format(Locale.ROOT, it.toInt() and 0xff)
            }
        }
    }
}
