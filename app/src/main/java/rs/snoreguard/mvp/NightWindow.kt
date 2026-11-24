package rs.snoreguard.mvp

import java.time.LocalTime

/** Cross-midnight night interval: [22:00, 08:00), in phone's local timezone. */
object NightWindow {
    fun isNight(time: LocalTime, start: LocalTime = LocalTime.of(22, 0), end: LocalTime = LocalTime.of(8, 0)): Boolean {
        if (start == end) return true
        return if (start.isBefore(end)) {
            !time.isBefore(start) && time.isBefore(end)
        } else {
            !time.isBefore(start) || time.isBefore(end)
        }
    }
}
