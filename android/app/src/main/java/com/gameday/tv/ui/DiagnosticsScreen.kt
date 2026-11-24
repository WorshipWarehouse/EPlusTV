package com.gameday.tv.ui

import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.gameday.tv.BuildConfig
import com.gameday.tv.R
import com.gameday.tv.core.Diag
import com.gameday.tv.core.Fmt

class DiagnosticsScreen(host: MainActivity) : Screen(host) {

    override val label: String = LABEL

    private lateinit var reportView: TextView
    private lateinit var subtitleView: TextView
    private val buttons = ArrayList<TextView>()

    override fun build(): View {
        val root = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(34), dp(26), dp(34), dp(20))
        }

        val header = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleColumn = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleColumn.addView(UiKit.text(host, "Diagnostics", 27f, bold = true))
        subtitleView = UiKit.text(host, "Nothing here leaves the device", 13f, R.color.text_secondary)
            .apply { setPadding(0, dp(5), 0, 0) }
        titleColumn.addView(subtitleView)
        header.addView(titleColumn)

        val actionRow = LinearLayout(host).apply { orientation = LinearLayout.HORIZONTAL }
        buttons += UiKit.button(host, "Refresh guide") { host.refreshGuideNow() }
        buttons += UiKit.button(host, "Clear guide cache") {
            host.container.guideCache.clear()
            host.toast("Guide cache cleared")
            render()
        }
        buttons += UiKit.button(host, "Clear log") {
            Diag.clear()
            render()
        }
        buttons.forEachIndexed { index, button ->
            if (index > 0) {
                (button.layoutParams as? LinearLayout.LayoutParams)?.let { it.marginStart = dp(10) }
                    ?: run {
                        button.layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ).apply { marginStart = dp(10) }
                    }
            }
            actionRow.addView(button)
        }
        header.addView(actionRow)
        root.addView(header)

        val scroll = ScrollView(host).apply {
            setPadding(0, dp(14), 0, 0)
            clipToPadding = false
        }
        reportView = UiKit.text(host, "", 12.5f).apply {
            typeface = android.graphics.Typeface.MONOSPACE
            setLineSpacing(dp(4).toFloat(), 1f)
        }
        scroll.addView(reportView)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    override fun onShow() {
        render()
    }

    override fun focusFirst() {
        buttons.firstOrNull()?.requestFocus()
        render()
    }

    private fun render() {
        if (!::reportView.isInitialized) return
        val container = host.container
        val settings = container.settings
        val snapshot = container.guideRepository.cached()
        val runtime = Runtime.getRuntime()
        val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val maxMb = runtime.maxMemory() / (1024 * 1024)
        val sources = container.sourceStore.list()

        val lines = ArrayList<String>()
        lines += "GameDay ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})"
        lines += "Android API ${Build.VERSION.SDK_INT} \u00b7 ${Build.SUPPORTED_ABIS.joinToString(", ")}"
        lines += "Device: ${Build.MANUFACTURER} ${Build.MODEL}"
        lines += "Heap: ${usedMb} MB used of ${maxMb} MB"
        lines += "Refresh running: ${container.isRefreshing()}"
        lines += ""
        lines += "GUIDE"
        lines += "  Channels: ${snapshot.channels.size}"
        lines += "  Programmes: ${snapshot.programs.size}"
        lines += "  Guide age: ${Fmt.ago(snapshot.fetchedAtMs)}"
        lines += "  Cache file: ${container.guideCache.sizeBytes() / 1024} KB"
        lines += "  Last refresh: ${if (settings.lastRefreshOk) "ok" else "failed"} ${Fmt.ago(settings.lastRefreshMs)}"
        if (settings.lastRefreshMessage.isNotBlank()) {
            lines += "  Detail: ${Diag.sanitize(settings.lastRefreshMessage)}"
        }
        lines += ""
        lines += "SOURCES (${sources.count { it.enabled }} enabled of ${sources.size})"
        if (sources.isEmpty()) lines += "  none configured"
        sources.forEach { source ->
            lines += "  ${source.name} [${source.kind}] ${if (source.enabled) "on" else "off"}"
            lines += "    ${Diag.sanitize(source.url)}"
            if (source.hasHeaders) lines += "    custom headers: encrypted in Keystore"
        }
        lines += ""
        lines += "FAVORITES: ${container.favorites.ids().size}"
        lines += ""
        lines += "RECENT LOG (sanitized, newest last)"
        val log = Diag.snapshot()
        if (log.isEmpty()) {
            lines += "  (empty)"
        } else {
            log.takeLast(25).forEach { lines += "  $it" }
        }

        reportView.text = lines.joinToString("\n")
    }

    private fun dp(value: Int): Int = UiKit.dp(host, value.toFloat())

    companion object {
        const val LABEL = "Diagnostics"
    }
}
