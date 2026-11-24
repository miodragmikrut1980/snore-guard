package rs.snoreguard.mvp

import java.io.File
import kotlin.math.abs

/** Bounded local PCM preview of our canonical 44-byte, 16-bit mono WAV files. */
object WaveformPeaks {
    fun read(file: File, bins: Int = 96): IntArray {
        require(file.length() in 46..480044 && bins in 1..512)
        val bytes = file.readBytes()
        require(bytes.size in 46..480044)
        return decode(bytes, bins)
    }
    fun decode(bytes: ByteArray, bins: Int): IntArray {
        require(bins in 1..512 && bytes.size in 46..480044 && (bytes.size - 44) % 2 == 0)
        fun tag(at: Int) = String(bytes, at, 4, Charsets.US_ASCII)
        require(tag(0) == "RIFF" && tag(8) == "WAVE" && tag(12) == "fmt " && tag(36) == "data")
        val samples = (bytes.size - 44) / 2
        val result = IntArray(bins)
        for (i in 0 until samples) {
            val at = 44 + i * 2
            val value = ((bytes[at].toInt() and 255) or (bytes[at + 1].toInt() shl 8)).toShort().toInt()
            val bin = i * bins / samples
            result[bin] = maxOf(result[bin], abs(value))
        }
        return result
    }
}
