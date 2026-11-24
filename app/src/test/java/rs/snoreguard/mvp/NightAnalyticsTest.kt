package rs.snoreguard.mvp

import org.junit.Assert.*
import org.junit.Test

class NightAnalyticsTest {
    @Test fun normalizesByAnalysedTime() {
        val r = NightAnalytics.calculate(14400, 2880, 60, 250, 3600000)!!
        assertEquals(1.0, r.analysedHours, 0.001)
        assertEquals(60.0, r.eventsPerHour, 0.001)
        assertEquals(20, r.score)
    }
    @Test fun insufficientCoverageOrDurationSuppressesScore() {
        assertNull(NightAnalytics.calculate(14400, 100, 20, 250, 7200000)!!.score)
        assertNull(NightAnalytics.calculate(400, 20, 5, 250, 100000)!!.score)
    }
    @Test fun rejectsInvalidAndLegacyCounters() {
        assertNull(NightAnalytics.calculate(0, 0, 0, 250, 100))
        assertNull(NightAnalytics.calculate(20, 21, 0, 250, 5000))
        assertNull(NightAnalytics.calculate(20, 0, 0, 975, 20000))
        assertNull(NightAnalytics.calculate(200, 0, 0, 250, 1000))
    }
    @Test fun feedbackExcludesUncertainAndUnreviewed() {
        val r = NightAnalytics.reviews(listOf(ClipLabel.SNORE, ClipLabel.FALSE_ALARM,
            ClipLabel.UNCERTAIN, ClipLabel.UNREVIEWED))
        assertEquals(2, r.reviewed)
        assertEquals(50, r.confirmedPercent)
        assertNull(NightAnalytics.reviews(listOf(ClipLabel.UNREVIEWED)).confirmedPercent)
    }
}
