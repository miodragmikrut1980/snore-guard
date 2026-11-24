package rs.snoreguard.mvp

import org.junit.Assert.*
import org.junit.Test

class SnoreEngineTest {
    private fun tick(engine: SnoreEngine, ms: Long, snore: Boolean = false, night: Boolean = true) =
        engine.process(snore, night, ms)

    @Test fun oneShortInhaleDoesNotCountMultipleTimesThroughOverlappingWindows() {
        val engine = SnoreEngine()
        for (n in 0 until 8) {
            assertFalse(tick(engine, n * 250L, snore = true))
        }
        assertEquals(1, engine.episodes)
        assertEquals(0, engine.alerts)
        assertEquals(8, engine.snoreFrames)
    }

    @Test fun twoSeparatedShortSnoresWithin15SecondsTriggerOneAlarm() {
        val engine = SnoreEngine()
        assertFalse(tick(engine, 0L, snore = true))
        for (n in 1..4) assertFalse(tick(engine, n * 250L))
        assertTrue(tick(engine, 1_250L, snore = true))
        assertEquals(2, engine.episodes)
        assertEquals(1, engine.alerts)
    }

    @Test fun positiveWindowsWithoutEnoughQuietGapRemainOneEvent() {
        val engine = SnoreEngine()
        assertFalse(tick(engine, 0L, snore = true))
        for (n in 1..3) tick(engine, n * 250L)
        assertFalse(tick(engine, 1_000L, snore = true))
        assertEquals(1, engine.episodes)
        assertEquals(0, engine.alerts)
    }

    @Test fun snoresMoreThan15SecondsApartDoNotTrigger() {
        val engine = SnoreEngine()
        tick(engine, 0L, snore = true)
        assertFalse(tick(engine, 15_250L, snore = true))
        assertEquals(2, engine.episodes)
        assertEquals(0, engine.alerts)
    }

    @Test fun twoSnoresExactlyAtBoundaryDoTrigger() {
        val engine = SnoreEngine()
        tick(engine, 0L, snore = true)
        assertTrue(tick(engine, 15_000L, snore = true))
        assertEquals(1, engine.alerts)
    }

    @Test fun sixtySecondCooldownStillAppliesAcrossMultipleBreaths() {
        val engine = SnoreEngine()
        tick(engine, 0L, snore = true)
        assertTrue(tick(engine, 2_000L, snore = true))
        assertFalse(tick(engine, 4_000L, snore = true))
        assertFalse(tick(engine, 61_000L, snore = true))
        assertTrue(tick(engine, 63_000L, snore = true))
        assertEquals(2, engine.alerts)
    }

    @Test fun daytimeCountsEventsButNeverNotifies() {
        val engine = SnoreEngine()
        tick(engine, 0L, snore = true, night = false)
        assertFalse(tick(engine, 2_000L, snore = true, night = false))
        assertEquals(2, engine.episodes)
        assertEquals(0, engine.alerts)
    }

    @Test fun silenceAndOneNoiseBurstDoNotTrigger() {
        val engine = SnoreEngine()
        for (n in 0..100) {
            val positive = n in 9..12
            assertFalse(tick(engine, n * 250L, snore = positive))
        }
        assertEquals(1, engine.episodes)
        assertEquals(0, engine.alerts)
    }

    @Test fun continuousPositiveAudioIsOneEventNotMany() {
        val engine = SnoreEngine()
        for (n in 0..160) assertFalse(tick(engine, n * 250L, snore = true))
        assertEquals(1, engine.episodes)
        assertEquals(0, engine.alerts)
    }
}
