package com.gameday.tv.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManifestTest {

    private val validJson = """
        {
          "versionCode": 7,
          "apkUrl": "https://example.test/gameday-7.apk",
          "sha256": "${"a".repeat(64)}",
          "versionName": "1.7.0",
          "minSdk": 25,
          "signerSha256": "",
          "notes": "Guide fixes"
        }
    """.trimIndent()

    @Test
    fun `parses a valid manifest`() {
        val manifest = UpdateManifestParser.parse(validJson)
        assertEquals(7L, manifest.versionCode)
        assertEquals("1.7.0", manifest.versionName)
        assertEquals(25, manifest.minSdk)
        assertEquals("a".repeat(64), manifest.sha256)
    }

    @Test
    fun `rejects non https download urls`() {
        val insecure = validJson.replace("https://example.test", "http://example.test")
        try {
            UpdateManifestParser.parse(insecure)
            throw AssertionError("Expected rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("HTTPS"))
        }
    }

    @Test
    fun `rejects an invalid checksum`() {
        val broken = validJson.replace("a".repeat(64), "abc123")
        assertNull(UpdateManifestParser.parseOrNull(broken))
    }

    @Test
    fun `rejects malformed json`() {
        assertNull(UpdateManifestParser.parseOrNull("{ nope }"))
    }

    @Test
    fun `only a strictly newer build is an update`() {
        val manifest = UpdateManifestParser.parse(validJson)
        assertTrue(UpdatePolicy.isUpdate(manifest, installedVersionCode = 6))
        assertFalse(UpdatePolicy.isUpdate(manifest, installedVersionCode = 7))
        assertFalse(UpdatePolicy.isUpdate(manifest, installedVersionCode = 8))
    }

    @Test
    fun `compatibility compares min sdk with the device`() {
        val manifest = UpdateManifestParser.parse(validJson)
        assertTrue(UpdatePolicy.isCompatible(manifest, deviceSdk = 25))
        assertTrue(UpdatePolicy.isCompatible(manifest, deviceSdk = 33))
        assertFalse(UpdatePolicy.isCompatible(manifest, deviceSdk = 24))
    }
}

class UpdateVerifierTest {

    private val digest = "b".repeat(64)
    private val signer = "c".repeat(64)

    private fun verify(
        expectedSha256: String = digest,
        actualSha256: String = digest,
        manifestSignerSha256: String = "",
        installedSignerSha256: String? = signer,
        archiveSignerSha256: String? = signer,
        archiveVersionCode: Long? = 7L,
        manifestVersionCode: Long = 7L,
        manifestMinSdk: Int = 25,
        deviceSdk: Int = 25,
    ) = UpdateVerifier.verify(
        expectedSha256 = expectedSha256,
        actualSha256 = actualSha256,
        manifestSignerSha256 = manifestSignerSha256,
        installedSignerSha256 = installedSignerSha256,
        archiveSignerSha256 = archiveSignerSha256,
        archiveVersionCode = archiveVersionCode,
        manifestVersionCode = manifestVersionCode,
        manifestMinSdk = manifestMinSdk,
        deviceSdk = deviceSdk,
    )

    @Test
    fun `accepts a matching signed apk`() {
        assertTrue(verify() is Verification.Ok)
        assertTrue(verify(manifestSignerSha256 = signer) is Verification.Ok)
    }

    @Test
    fun `rejects a checksum mismatch`() {
        val result = verify(actualSha256 = "d".repeat(64))
        assertTrue(result is Verification.Failure)
        assertTrue((result as Verification.Failure).reason.contains("checksum"))
    }

    @Test
    fun `rejects an apk signed by a different key`() {
        val result = verify(archiveSignerSha256 = "e".repeat(64))
        assertTrue(result is Verification.Failure)
        assertTrue((result as Verification.Failure).reason.contains("different key"))
    }

    @Test
    fun `rejects a manifest signer that does not match the installed app`() {
        val result = verify(manifestSignerSha256 = "f".repeat(64))
        assertTrue(result is Verification.Failure)
    }

    @Test
    fun `rejects a version mismatch between manifest and archive`() {
        val result = verify(archiveVersionCode = 6L)
        assertTrue(result is Verification.Failure)
    }

    @Test
    fun `rejects an update that needs a newer platform`() {
        val result = verify(manifestMinSdk = 26, deviceSdk = 25)
        assertTrue(result is Verification.Failure)
        assertTrue((result as Verification.Failure).reason.contains("API 26"))
    }

    @Test
    fun `rejects when the installed signer cannot be read`() {
        val result = verify(installedSignerSha256 = null)
        assertTrue(result is Verification.Failure)
    }

    @Test
    fun `sha256 hex matches a known digest`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            UpdateVerifier.sha256Hex("abc".toByteArray()),
        )
        assertNotNull(UpdateVerifier.sha256Hex(ByteArray(0)))
    }
}
