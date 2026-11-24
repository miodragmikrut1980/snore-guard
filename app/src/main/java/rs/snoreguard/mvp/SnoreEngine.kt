package rs.snoreguard.mvp

import java.util.ArrayDeque

/**
 * Detects repeated short snore events, rather than requiring several consecutive
 * full-second classifications. Overlapping 975 ms YAMNet windows arrive every
 * 250 ms. Multiple positive windows from one inhalation count as ONE event.
 *
 * Two separate events in 15 seconds can trigger a series. Heuristic only:
 * not medically validated and not an apnea detector.
 */
class SnoreEngine(private val cooldownMillis: Long = 60_000L) {
    private val recentSnoreStarts = ArrayDeque<Long>()
    private var lastPositiveAt: Long? = null
    private var lastAlertElapsed: Long? = null

    var analysedFrames: Int = 0
        private set
    var snoreFrames: Int = 0
        private set
    var episodes: Int = 0
        private set
    var alerts: Int = 0
        private set

    /**
     * elapsedMillis is based on consumed AUDIO SAMPLES (not TFLite compute time),
     * so a slow inference cannot manufacture gaps between two overlapping windows.
     * The caller passes a monotonic offset or elapsed-realtime-derived sample time.
     */
    fun process(isSnore: Boolean, mayAlert: Boolean, elapsedMillis: Long): Boolean {
        analysedFrames++
        while (recentSnoreStarts.isNotEmpty() &&
            elapsedMillis - recentSnoreStarts.peekFirst() > PAIR_WINDOW_MS) {
            recentSnoreStarts.removeFirst()
        }

        if (!isSnore) return false

        snoreFrames++
        val previous = lastPositiveAt
        val distinctEvent = previous == null || elapsedMillis - previous >= REARM_GAP_MS
        lastPositiveAt = elapsedMillis
        if (distinctEvent) {
            episodes++
            recentSnoreStarts.addLast(elapsedMillis)
        }

        val last = lastAlertElapsed
        val cooledDown = last == null || elapsedMillis - last >= cooldownMillis
        if (mayAlert && recentSnoreStarts.size >= 2 && cooledDown) {
            lastAlertElapsed = elapsedMillis
            alerts++
            return true
        }
        return false
    }

    companion object {
        /** 4 intervening nonpositive 250 ms hops between separate positive runs. */
        const val REARM_GAP_MS = 1_250L
        const val PAIR_WINDOW_MS = 15_000L
    }
}
