package rs.snoreguard.mvp

import org.junit.Assert.*
import org.junit.Test

class AnalysisHeartbeatTest {
    @Test fun countsOnlyGapsLongerThanFiveSeconds() {
        val heartbeat = AnalysisHeartbeat()
        heartbeat.record(10_000L)
        assertEquals(0L, heartbeat.maxGapMillis)
        heartbeat.record(10_250L)
        heartbeat.record(15_250L)
        assertEquals(5_000L, heartbeat.maxGapMillis)
        assertEquals(0, heartbeat.stalls)
        heartbeat.record(21_000L)
        assertEquals(5_750L, heartbeat.maxGapMillis)
        assertEquals(1, heartbeat.stalls)
        heartbeat.record(27_001L)
        assertEquals(6_001L, heartbeat.maxGapMillis)
        assertEquals(2, heartbeat.stalls)
    }
    @Test fun rejectsBackwardsTime() {
        val heartbeat = AnalysisHeartbeat()
        heartbeat.record(100L)
        try { heartbeat.record(99L); fail("Expected error") }
        catch (_: IllegalArgumentException) {}
    }
}
