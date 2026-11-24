package rs.snoreguard.mvp

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class FullBackupArchiveTest {
    @Test fun acceptsOnlyKnownWavFilenamesAndRejectsTraversal() {
        assertTrue(FullBackupArchive.isSafeFilename(
            "snore_20261124_230000_0123456789abcdef0123456789abcdef.wav"))
        for (bad in listOf("../secret.wav", "../../snore_20261124.wav",
                "clips/snore_20261124_230000_0123456789abcdef0123456789abcdef.wav",
                "/absolute.wav", "manifest.json", "snore_bad.wav", "a\\b.wav")) {
            assertFalse(bad, FullBackupArchive.isSafeFilename(bad))
        }
    }

    @Test fun acceptsValidGeneratedWavAndDetectsCorruption() {
        val f = File.createTempFile("snoreguard_wav_", ".wav")
        try {
            val samples = SnoreClipStore.SAMPLE_RATE * 3
            f.outputStream().use {
                it.write(SnoreClipStore.wavHeader(samples))
                it.write(ByteArray(samples * 2))
            }
            FullBackupArchive.validatePcmWav(f)
            val digest = FullBackupArchive.digestFile(f)
            assertEquals(64, digest.length)
            val content = f.readBytes()
            content[24] = 0 // invalid sample rate
            f.writeBytes(content)
            try {
                FullBackupArchive.validatePcmWav(f)
                fail("Expected invalid PCM format")
            } catch (_: IllegalArgumentException) { }
        } finally { f.delete() }
    }

    @Test fun digestUsesSha256() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            FullBackupArchive.digestBytes("abc".toByteArray(Charsets.UTF_8))
        )
    }
}
