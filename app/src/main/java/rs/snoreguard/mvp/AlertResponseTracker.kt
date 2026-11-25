package rs.snoreguard.mvp

/**
 * Observational response after the first posted notification, not proof of
 * watch vibration or treatment effect. Monotonic clock; synchronized across
 * the audio and main threads. No raw audio or event timestamps retained.
 */
class AlertResponseTracker(private val clock: () -> Long) {
    data class Snapshot(val started: Int = 0, val quiet: Int = 0,
        val returned: Int = 0, val continued: Int = 0, val inconclusive: Int = 0,
        val pending: Int = 0, val smartSuppressed: Int = 0, val timing: Timing? = null) {
        val settled: Int get() = quiet + returned
        val settledPercent: Double? get() = if (assessed == 0) null else 100.0 * settled / assessed
        val quietPercent: Double? get() = if (assessed == 0) null else 100.0 * quiet / assessed
        val assessed: Int get() = quiet + returned + continued
    }
    data class Timing(val returns: Int = 0, val sumQuietMs: Long = 0,
        val sumAfterAlarmMs: Long = 0, val minQuietMs: Long = 0, val maxQuietMs: Long = 0,
        val noReturn10m: Int = 0, val interrupted: Int = 0, val pending: Int = 0) {
        val meanQuietSeconds: Double? get() = if (returns == 0) null else sumQuietMs / 1000.0 / returns
        val meanAfterAlarmSeconds: Double? get() = if (returns == 0) null else sumAfterAlarmMs / 1000.0 / returns
    }
    private data class Followup(val alarm: Long, val quietStart: Long)
    private var followup: Followup? = null
    private data class Window(val start: Long, var invalid: Boolean,
        var quietSince: Long? = null, var earlyQuiet: Boolean = false,
        var returned: Boolean = false, var firstQuiet: Long? = null, var firstReturn: Long? = null)
    private var window: Window? = null
    private var lastFrame: Long? = null
    private var lastFresh = false
    private var counts = Snapshot(timing = Timing())
    private var closed = false

    @Synchronized fun firstPosted() {
        if (closed) return
        val now = clock()
        window?.let {
            if (now - it.start >= OBSERVE_MS && freshAt(now)) complete(it)
            else discard()
        }
        if (followup != null) endFollowup(interrupted = true)
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
        observeFollowup(now, positive, continuous)
        val w = window ?: return
        if (!continuous) { w.invalid = true; w.quietSince = null }
        if (now - w.start <= OBSERVE_MS) {
            if (positive) {
                if (w.earlyQuiet) {
                    w.returned = true
                    if (w.firstReturn == null) w.firstReturn = now
                }
                w.quietSince = null
            } else if (fresh) {
                if (w.quietSince == null) w.quietSince = now
                if (now - w.quietSince!! >= QUIET_MS && now - w.start <= EARLY_MS) {
                    w.earlyQuiet = true
                    if (w.firstQuiet == null) w.firstQuiet = w.quietSince
                }
            }
        }
        if (now - w.start >= OBSERVE_MS) {
            complete(w)
            if (now - w.start > OBSERVE_MS) observeFollowup(now, positive, continuous)
        }
    }

    /** Optional reduction only, never increase the configured burst or suppress step 1. */
    @Synchronized fun suppressFollowup(): Boolean {
        val now = clock()
        val w = window ?: return false
        if (closed || w.invalid || !freshAt(now) || w.quietSince == null ||
            (lastFrame ?: return false) - w.quietSince!! < QUIET_MS || now - w.start >= OBSERVE_MS) return false
        counts = counts.copy(smartSuppressed = counts.smartSuppressed + 1)
        return true
    }

    @Synchronized fun snapshot(): Snapshot = counts
    @Synchronized fun finish(): Snapshot {
        closed = true
        if (window != null) discard()
        if (followup != null) endFollowup(interrupted = true)
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
        if (w.earlyQuiet) {
            val quiet = w.firstQuiet!!
            val returnedAt = w.firstReturn
            if (returnedAt != null) addReturn(Followup(w.start, quiet), returnedAt)
            else {
                followup = Followup(w.start, quiet)
                counts = counts.copy(timing = counts.timing!!.copy(pending = 1))
            }
        }
        window = null
    }

    private fun addReturn(f: Followup, now: Long) {
        val old = counts.timing!!
        val quietMs = now - f.quietStart
        counts = counts.copy(timing = old.copy(returns = old.returns + 1,
            sumQuietMs = old.sumQuietMs + quietMs,
            sumAfterAlarmMs = old.sumAfterAlarmMs + now - f.alarm,
            minQuietMs = if (old.returns == 0) quietMs else minOf(old.minQuietMs, quietMs),
            maxQuietMs = maxOf(old.maxQuietMs, quietMs), pending = 0))
        followup = null
    }

    private fun endFollowup(interrupted: Boolean) {
        val old = counts.timing!!
        counts = counts.copy(timing = old.copy(pending = 0,
            interrupted = old.interrupted + if (interrupted) 1 else 0,
            noReturn10m = old.noReturn10m + if (interrupted) 0 else 1))
        followup = null
    }

    private fun observeFollowup(now: Long, positive: Boolean, continuous: Boolean) {
        val f = followup ?: return
        if (!continuous) endFollowup(interrupted = true)
        else if (now - f.quietStart >= FOLLOWUP_MS) endFollowup(interrupted = false)
        else if (positive) addReturn(f, now)
    }

    companion object {
        const val FOLLOWUP_MS = 600_000L
        const val OBSERVE_MS = 60_000L
        const val EARLY_MS = 30_000L
        const val QUIET_MS = 10_000L
        const val MAX_GAP_MS = 2_000L
    }
}
