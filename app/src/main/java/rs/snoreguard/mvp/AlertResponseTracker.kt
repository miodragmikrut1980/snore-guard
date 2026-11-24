package rs.snoreguard.mvp

/**
 * Observational response after the first posted notification, not proof of
 * watch vibration or treatment effect. Monotonic clock; synchronized across
 * the audio and main threads. No raw audio or event timestamps retained.
 */
class AlertResponseTracker(private val clock: () -> Long) {
    data class Snapshot(val started: Int = 0, val quiet: Int = 0,
        val returned: Int = 0, val continued: Int = 0, val inconclusive: Int = 0,
        val pending: Int = 0, val smartSuppressed: Int = 0) {
        val assessed: Int get() = quiet + returned + continued
    }
    private data class Window(val start: Long, var invalid: Boolean,
        var quietSince: Long? = null, var earlyQuiet: Boolean = false,
        var returned: Boolean = false)
    private var window: Window? = null
    private var lastFrame: Long? = null
    private var lastFresh = false
    private var counts = Snapshot()
    private var closed = false

    @Synchronized fun firstPosted() {
        if (closed) return
        val now = clock()
        window?.let {
            if (now - it.start >= OBSERVE_MS && freshAt(now)) complete(it)
            else discard()
        }
        window = Window(now, !freshAt(now))
        counts = counts.copy(started = counts.started + 1, pending = 1)
    }

    @Synchronized fun frame(positive: Boolean, fresh: Boolean) {
        if (closed) return
        val now = clock()
        val previous = lastFrame
        val continuous = fresh && lastFresh && previous != null &&
            now - previous in 0..MAX_GAP_MS
        lastFrame = now
        lastFresh = fresh
        val w = window ?: return
        if (!continuous) { w.invalid = true; w.quietSince = null }
        if (now - w.start <= OBSERVE_MS) {
            if (positive) {
                if (w.earlyQuiet) w.returned = true
                w.quietSince = null
            } else if (fresh) {
                if (w.quietSince == null) w.quietSince = now
                if (now - w.quietSince!! >= QUIET_MS && now - w.start <= EARLY_MS)
                    w.earlyQuiet = true
            }
        }
        if (now - w.start >= OBSERVE_MS) complete(w)
    }

    /** Optional reduction only, never increase the configured burst or suppress step 1. */
    @Synchronized fun suppressFollowup(): Boolean {
        val now = clock()
        val w = window ?: return false
        if (closed || w.invalid || !freshAt(now) || w.quietSince == null ||
            now - w.quietSince!! < QUIET_MS || now - w.start >= OBSERVE_MS) return false
        counts = counts.copy(smartSuppressed = counts.smartSuppressed + 1)
        return true
    }

    @Synchronized fun snapshot(): Snapshot = counts
    @Synchronized fun finish(): Snapshot {
        closed = true
        if (window != null) discard()
        return counts
    }
    private fun freshAt(now: Long): Boolean =
        lastFresh && lastFrame?.let { now - it in 0..MAX_GAP_MS } == true
    private fun discard() {
        counts = counts.copy(inconclusive = counts.inconclusive + 1, pending = 0)
        window = null
    }
    private fun complete(w: Window) {
        if (w.invalid) { discard(); return }
        counts = when {
            !w.earlyQuiet -> counts.copy(continued = counts.continued + 1, pending = 0)
            w.returned -> counts.copy(returned = counts.returned + 1, pending = 0)
            else -> counts.copy(quiet = counts.quiet + 1, pending = 0)
        }
        window = null
    }
    companion object {
        const val OBSERVE_MS = 60_000L
        const val EARLY_MS = 30_000L
        const val QUIET_MS = 10_000L
        const val MAX_GAP_MS = 2_000L
    }
}
