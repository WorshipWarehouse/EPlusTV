package com.gameday.tv.update

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.io.File

data class ApkInfo(
    val packageName: String?,
    val versionCode: Long?,
    val signerSha256: String?,
)

interface ApkInspector {
    fun inspect(apk: File): ApkInfo
    fun installedSignerSha256(): String?
}

/** Reads version/signature metadata from an APK without installing it. */
class AndroidApkInspector(private val context: Context) : ApkInspector {

    override fun inspect(apk: File): ApkInfo {
        val packageManager = context.packageManager
        val archiveInfo = try {
            @Suppress("DEPRECATION")
            packageManager.getPackageArchiveInfo(apk.absolutePath, signatureFlags())
        } catch (e: Exception) {
            null
        } ?: return ApkInfo(null, null, null)

        val signers = signersOf(archiveInfo)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archiveInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            archiveInfo.versionCode.toLong()
        }
        return ApkInfo(
            packageName = archiveInfo.packageName,
            versionCode = versionCode,
            signerSha256 = signers?.firstOrNull()?.let { UpdateVerifier.sha256Hex(it.toByteArray()) },
        )
    }

    override fun installedSignerSha256(): String? = try {
        val packageManager = context.packageManager
        @Suppress("DEPRECATION")
        val info = packageManager.getPackageInfo(context.packageName, signatureFlags())
        signersOf(info)?.firstOrNull()?.let { UpdateVerifier.sha256Hex(it.toByteArray()) }
    } catch (e: Exception) {
        null
    }

    @Suppress("DEPRECATION")
    private fun signatureFlags(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        PackageManager.GET_SIGNING_CERTIFICATES
    } else {
        PackageManager.GET_SIGNATURES
    }

    @Suppress("DEPRECATION")
    private fun signersOf(info: android.content.pm.PackageInfo): Array<Signature>? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            info.signatures
        }
}
