package rs.snoreguard.mvp

import org.junit.Assert.*
import org.junit.Test

class SleepTimelineTest {
    @Test fun limitsAndValidatesOffsets() {
        assertEquals("1,2", SleepTimeline.append("1",2,2))
        assertEquals("1,2", SleepTimeline.append("1,2",3,2))
        assertEquals("2", SleepTimeline.append("2",1,2))
        assertNull(SleepTimeline.parse("2,1",2))
        assertNull(SleepTimeline.parse("1,2,3",2))
        assertNull(SleepTimeline.parse("-1",2))
    }
    @Test fun boundaryAndMissingStageRemainUnknown() {
        val s = listOf(SleepTimeline.Stage(0,2000,SleepTimeline.Kind.LIGHT),
            SleepTimeline.Stage(2000,4000,SleepTimeline.Kind.DEEP))
        assertEquals(SleepTimeline.Kind.LIGHT,SleepTimeline.kindAt(1000,s))
        assertEquals(SleepTimeline.Kind.UNKNOWN,SleepTimeline.kindAt(1500,s))
        assertEquals(SleepTimeline.Kind.DEEP,SleepTimeline.kindAt(2000,s))
        assertEquals(SleepTimeline.Kind.UNKNOWN,SleepTimeline.kindAt(4000,s))
    }
    @Test fun gapsAreNotAssumedToBeSleep() {
        val n = SleepTimeline.Night(0,5000,"test",listOf(SleepTimeline.Stage(1000,3000,SleepTimeline.Kind.REM)))
        assertEquals(2000L,SleepTimeline.durationByKind(n)[SleepTimeline.Kind.REM])
        assertEquals(3000L,SleepTimeline.durationByKind(n)[SleepTimeline.Kind.UNKNOWN])
        assertEquals(1000L,SleepTimeline.overlap(4000,6000,n))
    }
}
