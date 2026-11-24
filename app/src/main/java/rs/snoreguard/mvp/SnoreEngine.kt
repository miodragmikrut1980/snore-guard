package rs.snoreguard.mvp

import java.util.ArrayDeque

/**
 * Consumes ~0.975-second YAMNet frames. Requires five classification frames
 * with at least three positive frames (including the latest positive frame).
 * This is NOT medical apnea detection and cannot guarantee user changed position.
 */
class SnoreEngine(private val cooldownMillis: Long = 60_000L) {
    private val recent = ArrayDeque<Boolean>()
    private var quietFrames = 0
    private var inEpisode = false
    private var lastAlertElapsed: Long? = null

    var analysedFrames: Int = 0
        private set
    var snoreFrames: Int = 0
        private set
    var episodes: Int = 0
        private set
    var alerts: Int = 0
        private set

    fun process(isSnore: Boolean, mayAlert: Boolean, elapsedMillis: Long): Boolean {
        analysedFrames++
        recent.addLast(isSnore)
        if (recent.size > 5) recent.removeFirst()

        if (isSnore) {
            snoreFrames++
            quietFrames = 0
            if (!inEpisode) {
                episodes++
                inEpisode = true
            }
        } else if (inEpisode) {
            quietFrames++
            if (quietFrames >= 3) {
                inEpisode = false
                recent.clear()
                quietFrames = 0
            }
        }

        val confirmed = isSnore && recent.size == 5 && recent.count { it } >= 3
        val cooledDown = lastAlertElapsed == null || elapsedMillis - lastAlertElapsed!! >= cooldownMillis
        if (mayAlert && confirmed && cooledDown) {
            lastAlertElapsed = elapsedMillis
            alerts++
            return true
        }
        return false
    }
}
