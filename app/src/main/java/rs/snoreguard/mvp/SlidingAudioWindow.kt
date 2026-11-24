package rs.snoreguard.mvp

/**
 * Keep a YAMNet-size audio frame while advancing only 250 ms of NEW PCM.
 * The caller fills [samples] once and then invokes [shiftForNextHop] before
 * reading exactly [hopSamples] new samples into the returned tail offset.
 */
class SlidingAudioWindow(val frameSamples: Int, val hopSamples: Int) {
    init {
        require(frameSamples > 0 && hopSamples in 1 until frameSamples)
    }

    val samples = ShortArray(frameSamples)
    val tailOffset: Int get() = frameSamples - hopSamples

    fun shiftForNextHop() {
        System.arraycopy(samples, hopSamples, samples, 0, tailOffset)
    }
}
