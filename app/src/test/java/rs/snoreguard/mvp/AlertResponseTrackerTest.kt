package rs.snoreguard.mvp

import org.junit.Assert.*
import org.junit.Test

class AlertResponseTrackerTest {
    private class Fixture {
        var now = 0L
        val t = AlertResponseTracker { now }
        fun start() { t.frame(true, true); t.firstPosted() }
        fun until(end: Long, positive: (Long) -> Boolean = { false }) {
            while (now < end) { now += 250; t.frame(positive(now), true) }
        }
    }
    @Test fun sustainedQuiet() {
        val f = Fixture(); f.start(); f.until(60_000)
        assertEquals(1, f.t.snapshot().quiet)
        assertEquals(1, f.t.snapshot().assessed)
    }
    @Test fun returnedAfterQuiet() {
        val f = Fixture(); f.start(); f.until(60_000) { it == 25_000L }
        assertEquals(1, f.t.snapshot().returned)
    }
    @Test fun continuedPositive() {
        val f = Fixture(); f.start(); f.until(60_000) { it % 2_000 == 0L }
        assertEquals(1, f.t.snapshot().continued)
    }
    @Test fun missingFramesCannotBeCalledQuiet() {
        val f = Fixture(); f.start(); f.until(5_000); f.now = 8_000
        f.t.frame(false, true); f.until(60_000)
        assertEquals(1, f.t.snapshot().inconclusive)
        assertEquals(0, f.t.snapshot().quiet)
    }
    @Test fun staleAudioCannotBeCalledQuietOrSuppress() {
        val f = Fixture(); f.start(); f.until(12_000)
        f.now += 250; f.t.frame(false, false)
        assertFalse(f.t.suppressFollowup()); f.until(60_000)
        assertEquals(1, f.t.snapshot().inconclusive)
    }
    @Test fun stoppingOrOverlappingMarksIncomplete() {
        val f = Fixture(); f.start(); f.until(15_000); f.t.firstPosted()
        assertEquals(1, f.t.snapshot().inconclusive)
        assertEquals(2, f.t.finish().inconclusive)
        assertEquals(0, f.t.snapshot().pending)
    }
    @Test fun smartSuppressesOnlyAfterObservedQuiet() {
        val f = Fixture(); f.start(); f.until(5_000)
        assertFalse(f.t.suppressFollowup()); f.until(16_000)
        assertTrue(f.t.suppressFollowup())
        assertEquals(1, f.t.snapshot().smartSuppressed)
    }
    @Test fun smartDoesNotSuppressWhenPositiveReturns() {
        val f = Fixture(); f.start(); f.until(16_000) { it == 16_000L }
        assertFalse(f.t.suppressFollowup())
    }
}
