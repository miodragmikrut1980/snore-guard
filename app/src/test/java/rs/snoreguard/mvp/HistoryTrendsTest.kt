package rs.snoreguard.mvp

import org.junit.Assert.*
import org.junit.Test

class HistoryTrendsTest {
    @Test fun weightedByAnalysedHoursAndFiltersIncompatible() {
        val rows = listOf(HistoryTrends.Night(10,"HIGH",10,1.0,1.0),
            HistoryTrends.Night(20,"HIGH",90,3.0,0.9),
            HistoryTrends.Night(30,"LOW",900,1.0,1.0),
            HistoryTrends.Night(40,"HIGH",900,1.0,0.5),
            HistoryTrends.Night(0,"HIGH",900,1.0,1.0))
        val t = HistoryTrends.summarize(rows,1,40,"HIGH")
        assertEquals(2,t.sessions)
        assertEquals(25.0,t.eventsPerHour!!,0.001)
        assertNull(HistoryTrends.summarize(rows,1,5,"HIGH").eventsPerHour)
    }
    @Test fun waveformReadsSignedPcmAndSilence() {
        val wav = SnoreClipStore.wavHeader(4) + byteArrayOf(0,0,0,0,0,-128,-1,127)
        val peaks = WaveformPeaks.decode(wav,2)
        assertArrayEquals(intArrayOf(0,32768),peaks)
    }
    @Test(expected = IllegalArgumentException::class) fun invalidWavRejected() {
        WaveformPeaks.decode(ByteArray(46),2)
    }
}
