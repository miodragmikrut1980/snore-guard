package rs.snoreguard.mvp

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SnoreClipHeaderTest {
    @Test fun wavHeaderHasCorrectRiffPcmSizes() {
        val samples = SnoreClipStore.SAMPLE_RATE * 12
        val bytes = SnoreClipStore.wavHeader(samples)
        assertEquals(44, bytes.size)
        assertEquals("RIFF", String(bytes.copyOfRange(0, 4), Charsets.US_ASCII))
        assertEquals("WAVE", String(bytes.copyOfRange(8, 12), Charsets.US_ASCII))
        assertEquals("fmt ", String(bytes.copyOfRange(12, 16), Charsets.US_ASCII))
        assertEquals("data", String(bytes.copyOfRange(36, 40), Charsets.US_ASCII))
        val view = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(36 + samples * 2, view.getInt(4))
        assertEquals(1, view.getShort(20).toInt()) // PCM
        assertEquals(1, view.getShort(22).toInt()) // Mono
        assertEquals(16_000, view.getInt(24))
        assertEquals(32_000, view.getInt(28)) // bytes/s
        assertEquals(2, view.getShort(32).toInt())
        assertEquals(16, view.getShort(34).toInt())
        assertEquals(samples * 2, view.getInt(40))
    }
    @Test fun rejectOversizedHeader() {
        try {
            SnoreClipStore.wavHeader(240_001)
            fail("Should reject a more-than-15-sec clip")
        } catch (_: IllegalArgumentException) { }
    }
}
