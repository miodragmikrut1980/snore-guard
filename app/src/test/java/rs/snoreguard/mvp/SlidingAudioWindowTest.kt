package rs.snoreguard.mvp

import org.junit.Assert.*
import org.junit.Test

class SlidingAudioWindowTest {
    @Test fun windowsOverlapBy11600SamplesAndAdvance4000() {
        val window = SlidingAudioWindow(15_600, 4_000)
        for (i in window.samples.indices) window.samples[i] = (i % 10_000).toShort()
        window.shiftForNextHop()
        for (i in 0 until 11_600) {
            assertEquals(((i + 4_000) % 10_000).toShort(), window.samples[i])
        }
        assertEquals(11_600, window.tailOffset)
        assertEquals(4_000, window.hopSamples)
    }

    @Test fun rejectsInvalidSizes() {
        try {
            SlidingAudioWindow(4_000, 4_000)
            fail("Must reject nonoverlapping window")
        } catch (_: IllegalArgumentException) {
        }
    }
}
