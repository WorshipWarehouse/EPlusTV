package com.gameday.tv.update

import com.gameday.tv.core.Diag
import com.gameday.tv.core.HttpTransport
import com.gameday.tv.storage.Settings
import java.io.File
import java.io.IOException

class UpdateRepository(
    private val settings: Settings,
    private val transport: HttpTransport,
    private val downloader: ApkDownloader,
    private val inspector: ApkInspector,
    private val installedVersionCode: Long,
    private val deviceSdk: Int,
    private val updateDirectory: File,
) {

    data class CheckResult(val manifest: UpdateManifest?, val ok: Boolean, val message: String)

    sealed class DownloadResult {
        data class Ready(val apk: File, val manifest: UpdateManifest) : DownloadResult()
        data class Failed(val message: String) : DownloadResult()
    }

    fun check(): CheckResult {
        val url = settings.updateManifestUrl.trim()
        if (url.isBlank()) {
            return CheckResult(null, false, "No update source configured")
        }
        if (!url.startsWith("https://")) {
            return CheckResult(null, false, "Update source must be an HTTPS URL")
        }
        settings.lastUpdateCheckMs = System.currentTimeMillis()
        return try {
            val manifest = UpdateManifestParser.parse(transport.getText(url))
            if (!UpdatePolicy.isCompatible(manifest, deviceSdk)) {
                return CheckResult(null, false, "Release ${manifest.versionName} needs API ${manifest.minSdk}; this device runs API $deviceSdk")
            }
            if (!UpdatePolicy.isUpdate(manifest, installedVersionCode)) {
                CheckResult(null, true, "GameDay is up to date (${installedVersionCode})")
            } else {
                CheckResult(manifest, true, "Version ${manifest.versionName.ifBlank { manifest.versionCode.toString() }} is available")
            }
        } catch (e: IOException) {
            val message = "Update check failed: ${Diag.sanitize(e.message ?: "network error")}"
            Diag.warn("Updates", message)
            CheckResult(null, false, message)
        } catch (e: IllegalArgumentException) {
            CheckResult(null, false, e.message ?: "Update manifest rejected")
        }
    }

    fun download(manifest: UpdateManifest, onProgress: (Int) -> Unit = {}): DownloadResult {
        val target = File(updateDirectory, "gameday-${manifest.versionCode}.apk")
        return try {
            updateDirectory.mkdirs()
            if (target.exists()) target.delete()
            downloader.download(manifest.apkUrl, target, onProgress = onProgress)

            val actualDigest = UpdateVerifier.sha256Hex(target)
            val archive = inspector.inspect(target)
            val verification = UpdateVerifier.verify(
                expectedSha256 = manifest.sha256,
                actualSha256 = actualDigest,
                manifestSignerSha256 = manifest.signerSha256,
                installedSignerSha256 = inspector.installedSignerSha256(),
                archiveSignerSha256 = archive.signerSha256,
                archiveVersionCode = archive.versionCode,
                manifestVersionCode = manifest.versionCode,
                manifestMinSdk = manifest.minSdk,
                deviceSdk = deviceSdk,
            )
            when (verification) {
                is Verification.Ok -> DownloadResult.Ready(target, manifest)
                is Verification.Failure -> {
                    target.delete()
                    DownloadResult.Failed(verification.reason)
                }
            }
        } catch (e: IOException) {
            target.delete()
            DownloadResult.Failed("Download failed: ${Diag.sanitize(e.message ?: "network error")}")
        } catch (e: Exception) {
            target.delete()
            DownloadResult.Failed("Update failed: ${e.javaClass.simpleName}")
        }
    }
}
