package rs.snoreguard.mvp

import org.junit.Assert.*
import org.junit.Test

class ProfilesTest {
    @Test fun thresholdsIncreaseAsSensitivityDecreases() {
        assertTrue(Sensitivity.HIGH.modelThreshold < Sensitivity.NORMAL.modelThreshold)
        assertTrue(Sensitivity.NORMAL.modelThreshold < Sensitivity.LOW.modelThreshold)
    }

    @Test fun oldInstallsKeepHighAndIntense() {
        assertEquals(Sensitivity.HIGH, Sensitivity.fromStored(null))
        assertEquals(AlertMode.STRONG, AlertMode.fromStored(""))
        assertEquals(Sensitivity.HIGH, Sensitivity.fromStored("UNKNOWN"))
    }

    @Test fun modesHaveValidStepCounts() {
        assertEquals(1, AlertMode.GENTLE.steps)
        assertEquals(2, AlertMode.NORMAL.steps)
        assertEquals(3, AlertMode.STRONG.steps)
        AlertMode.values().forEach { assertEquals(16_000L, it.intervalMillis) }
    }
}
