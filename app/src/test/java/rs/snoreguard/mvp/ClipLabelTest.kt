package rs.snoreguard.mvp

import org.junit.Assert.*
import org.junit.Test

class ClipLabelTest {
    @Test fun oldRecordingsDefaultToUnreviewed() {
        assertEquals(ClipLabel.UNREVIEWED, ClipLabel.fromStored(null))
        assertEquals(ClipLabel.UNREVIEWED, ClipLabel.fromStored(""))
        assertEquals(ClipLabel.UNREVIEWED, ClipLabel.fromStored("UNRECOGNIZED"))
    }

    @Test fun eachValidFeedbackLabelRoundTrips() {
        ClipLabel.values().forEach { label ->
            assertEquals(label, ClipLabel.fromStored(label.name))
            assertTrue(label.display.isNotBlank())
        }
    }

    @Test fun oldClipConstructorRemainsCompatible() {
        val legacy = SnoreClip("snore_20261124_235959_0123456789abcdef0123456789abcdef.wav",
            0L, 1000L, 2048L)
        assertEquals(ClipLabel.UNREVIEWED, legacy.label)
        assertEquals("", legacy.note)
    }
}
