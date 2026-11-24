package com.gameday.tv.ui

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.gameday.tv.BuildConfig
import com.gameday.tv.R
import com.gameday.tv.core.Fmt
import com.gameday.tv.update.ApkInstaller
import com.gameday.tv.update.UpdateManifest
import com.gameday.tv.update.UpdateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UpdatesScreen(host: MainActivity) : Screen(host) {

    override val label: String = LABEL

    private lateinit var statusView: TextView
    private lateinit var detailView: TextView
    private lateinit var installButton: TextView
    private var pending: UpdateManifest? = null
    private var busy = false

    override fun build(): View {
        val root = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(34), dp(26), dp(34), dp(24))
        }

        root.addView(UiKit.text(host, "Updates", 27f, bold = true))
        statusView = UiKit.text(host, "", 14f, R.color.text_secondary).apply {
            setPadding(0, dp(6), 0, dp(16))
        }
        root.addView(statusView)

        val actionRow = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val checkButton = UiKit.button(host, "Check for updates") { check() }
        installButton = UiKit.button(host, "Download and install") { download() }.apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = dp(10) }
        }
        val urlButton = UiKit.button(host, "Set manifest URL") {
            host.promptText(
                heading = "Update manifest URL",
                initial = host.container.settings.updateManifestUrl,
                hint = "Must be https://",
                allowUrlSymbols = true,
            ) { value ->
                if (value.isBlank() || value.startsWith("https://")) {
                    host.container.settings.updateManifestUrl = value
                    render()
                } else {
                    host.toast("Update manifests must use HTTPS")
                }
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = dp(10) }
        }
        val permissionButton = UiKit.button(host, "Install permission") {
            ApkInstaller.openInstallPermissionSettings(host)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = dp(10) }
        }

        actionRow.addView(checkButton)
        actionRow.addView(installButton)
        actionRow.addView(urlButton)
        actionRow.addView(permissionButton)
        root.addView(actionRow)

        detailView = UiKit.text(host, "", 13f, R.color.text_secondary).apply {
            setPadding(0, dp(20), 0, 0)
            setLineSpacing(dp(4).toFloat(), 1f)
        }
        root.addView(
            detailView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        return root
    }

    override fun onShow() {
        render()
        if (host.container.settings.autoCheckUpdates && shouldAutoCheck()) {
            check()
        }
    }

    override fun focusFirst() {
        if (installButton.visibility == View.VISIBLE) {
            installButton.requestFocus()
        } else {
            statusView.rootView.findFocus() ?: statusView.requestFocus()
        }
    }

    private fun shouldAutoCheck(): Boolean {
        val settings = host.container.settings
        if (settings.updateManifestUrl.isBlank()) return false
        val last = settings.lastUpdateCheckMs
        return last == 0L || System.currentTimeMillis() - last > 6 * 60 * 60 * 1000L
    }

    private fun render() {
        if (!::statusView.isInitialized) return
        val settings = host.container.settings
        statusView.text = buildString {
            append("Installed: ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
            append(" \u00b7 last check ${Fmt.ago(settings.lastUpdateCheckMs)}")
        }
        detailView.text = buildString {
            appendLine("Update source")
            appendLine("  " + settings.updateManifestUrl.ifBlank { "Not configured" })
            appendLine()
            appendLine("Releases are installed only through the system package installer, and only if")
            appendLine("the APK matches the manifest SHA-256 and is signed with the same key as this app.")
            appendLine()
            appendLine("If an update fails to install, GameDay keeps running the previous version;")
            appendLine("sources, favorites and settings are preserved.")
            pending?.let { manifest ->
                appendLine()
                appendLine("Available release: ${manifest.versionName.ifBlank { manifest.versionCode.toString() }} (build ${manifest.versionCode})")
                if (manifest.notes.isNotBlank()) {
                    appendLine()
                    appendLine(manifest.notes)
                }
            }
        }
    }

    private fun check() {
        if (busy) return
        busy = true
        statusView.text = "Checking for updates\u2026"
        host.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { host.container.updateRepository.check() }
            busy = false
            pending = result.manifest
            render()
            statusView.text = result.message
            installButton.visibility = if (result.manifest != null) View.VISIBLE else View.GONE
            if (result.manifest != null) installButton.requestFocus()
        }
    }

    private fun download() {
        val manifest = pending ?: return
        if (busy) return
        busy = true
        statusView.text = "Downloading update\u2026"
        host.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                host.container.updateRepository.download(manifest) { percent ->
                    host.runOnUiThread { statusView.text = "Downloading update\u2026 $percent%" }
                }
            }
            busy = false
            when (result) {
                is UpdateRepository.DownloadResult.Failed -> {
                    statusView.text = result.message
                    render()
                }
                is UpdateRepository.DownloadResult.Ready -> {
                    statusView.text = "Verified ${result.apk.name}"
                    when (val install = ApkInstaller.install(host, result.apk)) {
                        is ApkInstaller.Result.Started ->
                            statusView.text = "Confirm the installation in the system dialog"
                        is ApkInstaller.Result.NeedsPermission -> {
                            statusView.text = install.message
                            host.confirm(
                                "Allow installs",
                                install.message,
                                "Open settings",
                            ) {
                                ApkInstaller.openInstallPermissionSettings(host)
                            }
                        }
                        is ApkInstaller.Result.Failed -> statusView.text = install.message
                    }
                }
            }
        }
    }

    private fun dp(value: Int): Int = UiKit.dp(host, value.toFloat())

    companion object {
        const val LABEL = "Updates"
    }
}
