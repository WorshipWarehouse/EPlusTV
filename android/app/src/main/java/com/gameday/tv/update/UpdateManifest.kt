package com.gameday.tv.update

import com.gameday.tv.core.JsonCodec
import kotlinx.serialization.Serializable

@Serializable
data class UpdateManifest(
    val versionCode: Long,
    val apkUrl: String,
    val sha256: String,
    val versionName: String = "",
    val minSdk: Int = 25,
    val signerSha256: String = "",
    val notes: String = "",
    val publishedAtMs: Long = 0L,
)

/**
 * Parses and validates update manifests. The URL must be HTTPS and the checksum must be a
 * full SHA-256 hex digest; anything else is rejected before a download is attempted.
 */
object UpdateManifestParser {

    private val sha256Pattern = Regex("^[a-fA-F0-9]{64}$")

    fun parse(text: String): UpdateManifest {
        val manifest = try {
            JsonCodec.json.decodeFromString<UpdateManifest>(text)
        } catch (e: Exception) {
            throw IllegalArgumentException("Malformed update manifest: ${e.message}")
        }
        require(manifest.versionCode > 0) { "Manifest has no valid versionCode" }
        require(sha256Pattern.matches(manifest.sha256.trim())) { "Manifest sha256 is not a SHA-256 digest" }
        require(manifest.apkUrl.startsWith("https://")) { "Update downloads must use HTTPS" }
        require(manifest.minSdk >= 21) { "Manifest minSdk is invalid" }
        return manifest.copy(sha256 = manifest.sha256.trim().lowercase(), apkUrl = manifest.apkUrl.trim())
    }

    fun parseOrNull(text: String): UpdateManifest? = try {
        parse(text)
    } catch (e: Exception) {
        null
    }
}

object UpdatePolicy {

    /** A manifest is an update only when it is strictly newer than what is installed. */
    fun isUpdate(manifest: UpdateManifest, installedVersionCode: Long): Boolean =
        manifest.versionCode > installedVersionCode

    fun isCompatible(manifest: UpdateManifest, deviceSdk: Int): Boolean = manifest.minSdk <= deviceSdk
}
