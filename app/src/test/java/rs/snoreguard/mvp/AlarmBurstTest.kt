package rs.snoreguard.mvp

import org.junit.Assert.*
import org.junit.Test

class AlarmBurstTest {
    @Test fun gentleModeEmitsExactlyOneNotificationImmediately() {
        val sent = mutableListOf<Int>()
        val burst = AlarmBurst(
            scheduler = BurstScheduler { _, _ -> error("Should not schedule a follow-up") },
            canContinue = { true },
            emit = { sent.add(it) },
            totalAlerts = 1
        )
        assertTrue(burst.start())
        assertEquals(listOf(1), sent)
        assertFalse(burst.isRunning)
    }

    @Test fun normalModeStopsAtSecondNotification() {
        val sent = mutableListOf<Int>()
        val actions = mutableListOf<() -> Unit>()
        val burst = AlarmBurst(
            scheduler = BurstScheduler { _, action -> actions.add(action) },
            canContinue = { true },
            emit = { sent.add(it) },
            totalAlerts = 2
        )
        assertTrue(burst.start())
        assertEquals(1, actions.size)
        actions.removeAt(0).invoke()
        assertEquals(listOf(1, 2), sent)
        assertFalse(burst.isRunning)
        assertTrue(actions.isEmpty())
    }


    private class VirtualTimer : BurstScheduler {
        var now = 0L
        val events = mutableListOf<Pair<Long, () -> Unit>>()
        override fun schedule(delayMillis: Long, action: () -> Unit) {
            events.add(now + delayMillis to action)
        }
        fun advanceTo(time: Long) {
            require(time >= now)
            while (true) {
                val next = events.withIndex().filter { it.value.first <= time }.minByOrNull { it.value.first } ?: break
                events.removeAt(next.index)
                now = next.value.first
                next.value.second()
            }
            now = time
        }
    }
    @Test fun threeUniqueStepsAtSixteenSecondIntervals() {
        val timer = VirtualTimer()
        val emitted = mutableListOf<Pair<Int, Long>>()
        val burst = AlarmBurst(timer, { true }) { step -> emitted.add(step to timer.now) }
        assertTrue(burst.start())
        assertFalse(burst.start()) // no duplicate overlapping series
        timer.advanceTo(15_999L)
        assertEquals(listOf(1 to 0L), emitted)
        timer.advanceTo(16_000L)
        timer.advanceTo(31_999L)
        assertEquals(listOf(1 to 0L, 2 to 16_000L), emitted)
        timer.advanceTo(32_000L)
        assertEquals(listOf(1 to 0L, 2 to 16_000L, 3 to 32_000L), emitted)
        assertFalse(burst.isRunning)
    }
    @Test fun stoppedSnoringCancelsRestOfBurst() {
        val timer = VirtualTimer()
        val events = mutableListOf<Int>()
        val burst = AlarmBurst(timer, { true }) { events.add(it) }
        burst.start()
        timer.advanceTo(4_000L)
        burst.cancel()
        timer.advanceTo(40_000L)
        assertEquals(listOf(1), events)
    }
    @Test fun permissionOrNightWindowBecomingFalseStopsFutureNotifications() {
        val timer = VirtualTimer()
        var allowed = true
        val events = mutableListOf<Int>()
        val burst = AlarmBurst(timer, { allowed }) { events.add(it) }
        burst.start()
        allowed = false
        timer.advanceTo(20_000L)
        assertEquals(listOf(1), events)
        assertFalse(burst.isRunning)
    }
    @Test fun canRestartAfterFinishWithoutStaleCallbacks() {
        val timer = VirtualTimer()
        val events = mutableListOf<Int>()
        val burst = AlarmBurst(timer, { true }) { events.add(it) }
        burst.start()
        timer.advanceTo(32_000L)
        assertTrue(burst.start())
        timer.advanceTo(64_000L)
        assertEquals(listOf(1, 2, 3, 1, 2, 3), events)
    }
    @Test fun exceptionStopsAdditionalNotifications() {
        val timer = VirtualTimer()
        var called = 0
        val burst = AlarmBurst(timer, { true }) { called++; error("notifications disabled") }
        assertFalse(burst.start())
        timer.advanceTo(20_000L)
        assertEquals(1, called)
    }
}
