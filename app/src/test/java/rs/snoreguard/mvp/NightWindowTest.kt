package rs.snoreguard.mvp

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalTime

class NightWindowTest {
    @Test fun crossMidnight() {
        assertTrue(NightWindow.isNight(LocalTime.of(22, 0)))
        assertTrue(NightWindow.isNight(LocalTime.of(0, 0)))
        assertTrue(NightWindow.isNight(LocalTime.of(7, 59)))
        assertFalse(NightWindow.isNight(LocalTime.of(8, 0)))
        assertFalse(NightWindow.isNight(LocalTime.of(21, 59)))
    }
    @Test fun dayWindowAndWholeDay() {
        assertTrue(NightWindow.isNight(LocalTime.NOON, LocalTime.of(8, 0), LocalTime.of(18, 0)))
        assertFalse(NightWindow.isNight(LocalTime.of(19, 0), LocalTime.of(8, 0), LocalTime.of(18, 0)))
        assertTrue(NightWindow.isNight(LocalTime.NOON, LocalTime.of(8, 0), LocalTime.of(8, 0)))
    }
}
