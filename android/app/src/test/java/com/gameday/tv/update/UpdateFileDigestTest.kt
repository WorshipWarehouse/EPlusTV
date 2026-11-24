package com.gameday.tv.update

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateFileDigestTest {

    @Test
    fun `file digest matches the in-memory digest for multi-chunk files`() {
        val file = File(Files.createTempDirectory("gameday-digest").toFile(), "payload.bin")
        // Larger than the 64 KiB read buffer so the streaming loop is exercised.
        val payload = ByteArray(200_000) { index -> (index % 251).toByte() }
        file.writeBytes(payload)

        assertEquals(UpdateVerifier.sha256Hex(payload), UpdateVerifier.sha256Hex(file))
    }

    @Test
    fun `empty file digest is stable`() {
        val file = File(Files.createTempDirectory("gameday-digest").toFile(), "empty.bin")
        file.writeBytes(ByteArray(0))
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            UpdateVerifier.sha256Hex(file),
        )
    }
}
