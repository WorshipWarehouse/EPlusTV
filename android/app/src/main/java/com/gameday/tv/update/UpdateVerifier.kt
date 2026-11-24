package com.gameday.tv.update

import java.io.File
import java.security.MessageDigest

sealed class Verification {
    object Ok : Verification()
    data class Failure(val reason: String) : Verification()
}

/**
 * Every check that must pass before an update APK is handed to the system installer.
 *
 * The strongest guarantee is the signer comparison: Android will only replace the installed
 * app with an APK signed by the same key, and we assert that here too so a tampered file is
 * rejected before the installer is even opened.
 */
object UpdateVerifier {

    private const val HEX = "0123456789abcdef"

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val output = StringBuilder(digest.size * 2)
        digest.forEach { byte ->
            val value = byte.toInt() and 0xff
            output.append(HEX[value ushr 4]).append(HEX[value and 0x0f])
        }
        return output.toString()
    }

    /** Streams the file so a multi-megabyte APK never lands in the heap at once. */
    fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        val bytes = digest.digest()
        val output = StringBuilder(bytes.size * 2)
        bytes.forEach { byte ->
            val value = byte.toInt() and 0xff
            output.append(HEX[value ushr 4]).append(HEX[value and 0x0f])
        }
        return output.toString()
    }

    fun verify(
        expectedSha256: String,
        actualSha256: String,
        manifestSignerSha256: String,
        installedSignerSha256: String?,
        archiveSignerSha256: String?,
        archiveVersionCode: Long?,
        manifestVersionCode: Long,
        manifestMinSdk: Int,
        deviceSdk: Int,
    ): Verification {
        if (!expectedSha256.equals(actualSha256, ignoreCase = true)) {
            return Verification.Failure("Downloaded file does not match the manifest checksum")
        }
        if (installedSignerSha256.isNullOrBlank()) {
            return Verification.Failure("Cannot read the installed app signing certificate")
        }
        if (archiveSignerSha256.isNullOrBlank()) {
            return Verification.Failure("Downloaded file is not a valid signed APK")
        }
        if (!installedSignerSha256.equals(archiveSignerSha256, ignoreCase = true)) {
            return Verification.Failure("Downloaded APK is signed by a different key")
        }
        if (manifestSignerSha256.isNotBlank() &&
            !manifestSignerSha256.equals(installedSignerSha256, ignoreCase = true)
        ) {
            return Verification.Failure("Manifest signer does not match the installed app")
        }
        if (archiveVersionCode == null || archiveVersionCode != manifestVersionCode) {
            return Verification.Failure("Downloaded APK version does not match the manifest")
        }
        if (manifestMinSdk > deviceSdk) {
            return Verification.Failure("This update requires Android API $manifestMinSdk or newer")
        }
        return Verification.Ok
    }
}
