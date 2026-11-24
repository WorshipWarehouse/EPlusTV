package com.gameday.tv.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.gameday.tv.core.Diag
import java.io.File

/**
 * Installation always goes through the system package installer, so the platform shows its own
 * confirmation prompt and enforces signature continuity. GameDay never installs silently.
 */
object ApkInstaller {

    sealed class Result {
        object Started : Result()
        data class NeedsPermission(val message: String) : Result()
        data class Failed(val message: String) : Result()
    }

    fun canInstall(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData(android.net.Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Diag.warn("Installer", "No install-permission settings activity")
            }
        }
    }

    fun install(context: Context, apk: File): Result {
        if (!canInstall(context)) {
            return Result.NeedsPermission(
                "Allow GameDay to install unknown apps in Settings, then press Install again.",
            )
        }
        return try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Result.Started
        } catch (e: ActivityNotFoundException) {
            Result.Failed("No package installer is available on this device")
        } catch (e: Exception) {
            Diag.error("Installer", "Install intent failed: ${e.javaClass.simpleName}")
            Result.Failed("Could not start the package installer")
        }
    }
}
