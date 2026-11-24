package rs.snoreguard.mvp

import org.junit.Assert.*
import org.junit.Test

class SnoreEngineTest {
    @Test fun mustObserveFiveFramesBeforeAlert() {
        val engine = SnoreEngine()
        for (i in 0..3) assertFalse(engine.process(true, true, i * 1_000L))
        assertTrue(engine.process(true, true, 4_000L))
        assertEquals(1, engine.alerts)
        assertEquals(1, engine.episodes)
    }
    @Test fun cooldownIsEnforced() {
        val engine = SnoreEngine()
        for (i in 0..65) {
            val result = engine.process(true, true, i * 1_000L)
            if (i == 4 || i == 64) assertTrue(result)
            else assertFalse(result)
        }
        assertEquals(2, engine.alerts)
    }
    @Test fun noAlarmOutsideNightButDetectsSnore() {
        val engine = SnoreEngine()
        repeat(10) { assertFalse(engine.process(true, false, it * 1000L)) }
        assertEquals(0, engine.alerts)
        assertEquals(1, engine.episodes)
        assertTrue(engine.process(true, true, 10_000L))
    }
    @Test fun shortNoiseDoesNotAlert() {
        val engine = SnoreEngine()
        listOf(true, false, false, false, false, false, true).forEachIndexed { index, value ->
            assertFalse(engine.process(value, true, index * 1_000L))
        }
    }
    @Test fun silenceEndsEpisode() {
        val engine = SnoreEngine()
        repeat(5) { engine.process(true, true, it * 1_000L) }
        repeat(3) { engine.process(false, true, (it + 5) * 1_000L) }
        repeat(5) { engine.process(true, true, (it + 8) * 1_000L) }
        assertEquals(2, engine.episodes)
        assertEquals(10, engine.snoreFrames)
    }
}
