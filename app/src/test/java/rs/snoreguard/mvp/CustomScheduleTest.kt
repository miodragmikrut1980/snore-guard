package rs.snoreguard.mvp

import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class CustomScheduleTest {
    @Test fun chooseDifferentNightWindow() {
        val start = LocalTime.of(23, 30)
        val end = LocalTime.of(6, 45)
        assertTrue(NightWindow.isNight(LocalTime.of(23, 30), start, end))
        assertTrue(NightWindow.isNight(LocalTime.of(0, 0), start, end))
        assertTrue(NightWindow.isNight(LocalTime.of(6, 44), start, end))
        assertFalse(NightWindow.isNight(LocalTime.of(6, 45), start, end))
        assertFalse(NightWindow.isNight(LocalTime.of(22, 0), start, end))
        assertEquals(listOf("23","00","01","02","03","04","05","06"),
            NightHours.labelsForWindow(23 * 60 + 30, 6 * 60 + 45))
    }
    @Test fun daytimeWindowAndAllDay() {
        val start = LocalTime.of(12, 15)
        val end = LocalTime.of(15, 30)
        assertTrue(NightWindow.isNight(LocalTime.of(13, 0), start, end))
        assertFalse(NightWindow.isNight(LocalTime.of(11, 59), start, end))
        assertEquals(listOf("12", "13", "14", "15"),
            NightHours.labelsForWindow(12 * 60 + 15, 15 * 60 + 30))
        assertEquals(24, NightHours.labelsForWindow(22 * 60, 22 * 60).size)
        assertEquals("22:05", Settings.timeLabel(22 * 60 + 5))
    }
}
