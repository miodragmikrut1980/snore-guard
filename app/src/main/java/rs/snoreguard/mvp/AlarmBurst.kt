package rs.snoreguard.mvp

/** Platform-independent timer, suitable for deterministic unit tests. */
fun interface BurstScheduler {
    fun schedule(delayMillis: Long, action: () -> Unit)
}

/**
 * Configurable 1-3-notification burst: default T+0, T+16s and T+32s.
 * One instance is owned by the monitoring service; a separate instance handles the manual test.
 * All calls must be made from the same thread (Android main thread in production).
 * Cancellation invalidates already scheduled callbacks via a generation token.
 */
class AlarmBurst(
    private val scheduler: BurstScheduler,
    private val canContinue: () -> Boolean,
    private val emit: (step: Int) -> Unit,
    private val totalAlerts: Int = TOTAL_ALERTS,
    private val intervalMillis: Long = INTERVAL_MILLIS
) {
    init {
        require(totalAlerts in 1..3) { "Supported alert series: 1-3 notifications" }
        require(intervalMillis > 0L)
    }
    var isRunning: Boolean = false
        private set
    private var generation = 0L

    fun start(): Boolean {
        if (isRunning) return false
        isRunning = true
        val token = ++generation
        deliver(token, 1)
        return isRunning || token == generation
    }

    private fun deliver(token: Long, step: Int) {
        if (!isRunning || token != generation) return
        if (!canContinue()) {
            cancel()
            return
        }
        try {
            emit(step)
        } catch (_: Exception) {
            cancel()
            return
        }
        if (step < totalAlerts) {
            scheduler.schedule(intervalMillis) { deliver(token, step + 1) }
        } else {
            isRunning = false
        }
    }

    fun cancel() {
        generation++
        isRunning = false
    }

    companion object {
        const val TOTAL_ALERTS = 3
        const val INTERVAL_MILLIS = 16_000L
    }
}
