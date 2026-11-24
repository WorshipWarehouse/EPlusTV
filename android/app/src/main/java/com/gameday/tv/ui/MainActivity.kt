package com.gameday.tv.ui

import android.content.ActivityNotFoundException
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.gameday.tv.AppContainer
import com.gameday.tv.BuildConfig
import com.gameday.tv.GameDayApp
import com.gameday.tv.R
import com.gameday.tv.core.Fmt
import com.gameday.tv.model.Channel
import com.gameday.tv.playback.PlaybackRequest

class MainActivity : AppCompatActivity() {

    lateinit var container: AppContainer
        private set

    private lateinit var screens: List<Screen>
    private lateinit var navItems: List<TextView>
    private lateinit var contentHost: FrameLayout
    private lateinit var statusView: TextView
    private var currentIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container = GameDayApp.container(this)
        screens = listOf(
            HomeScreen(this),
            LiveScreen(this),
            GuideScreen(this),
            FavoritesScreen(this),
            SearchScreen(this),
            SourcesScreen(this),
            SettingsScreen(this),
            DiagnosticsScreen(this),
            UpdatesScreen(this),
        )
        setContentView(buildShell())
        select(savedInstanceState?.getInt(STATE_SCREEN, 0) ?: 0, moveFocus = true)
        installBackHandler()
        if (container.settings.autoRefresh && container.guideStale()) {
            refreshGuideNow()
        }
    }

    override fun onResume() {
        super.onResume()
        if (container.settings.autoRefresh && container.guideStale(10 * 60 * 1000L)) {
            refreshGuideNow()
        }
        updateStatus()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_SCREEN, currentIndex)
    }

    private fun buildShell(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(UiKit.color(this@MainActivity, R.color.bg))
        }

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(26), dp(18), dp(18))
            setBackgroundColor(UiKit.color(this@MainActivity, R.color.surface))
            layoutParams = LinearLayout.LayoutParams(dp(214), ViewGroup.LayoutParams.MATCH_PARENT)
        }
        nav.addView(UiKit.text(this, "GameDay", 27f, R.color.accent, bold = true))
        nav.addView(
            UiKit.text(this, "Live sports television", 12f, R.color.text_secondary).apply {
                setPadding(0, dp(4), 0, dp(20))
            },
        )

        val items = ArrayList<TextView>()
        screens.forEachIndexed { index, screen ->
            val item = UiKit.text(this, screen.label, 16f).apply {
                setPadding(dp(14), dp(11), dp(14), dp(11))
                background = UiKit.focusBackground(this@MainActivity, R.color.surface)
                UiKit.focusable(this)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { bottomMargin = dp(2) }
                setOnClickListener { select(index, moveFocus = true) }
                UiKit.scaleOnFocus(this)
            }
            items += item
            nav.addView(item)
        }
        navItems = items

        nav.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(1, 0, 1f) })
        statusView = UiKit.text(this, "", 11f, R.color.text_secondary).apply {
            maxLines = 5
        }
        nav.addView(statusView)
        nav.addView(
            UiKit.text(this, "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", 11f, R.color.text_secondary)
                .apply { setPadding(0, dp(8), 0, 0) },
        )

        root.addView(nav)
        contentHost = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        }
        root.addView(contentHost)
        return root
    }

    private fun installBackHandler() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val focus = currentFocus
                    val navFocused = focus != null && navItems.contains(focus)
                    if (navFocused) {
                        if (currentIndex != 0) {
                            select(0, moveFocus = true)
                        } else {
                            finish()
                        }
                    } else {
                        navItems.getOrNull(currentIndex)?.requestFocus()
                    }
                }
            },
        )
    }

    private fun select(index: Int, moveFocus: Boolean) {
        val bounded = index.coerceIn(0, screens.lastIndex)
        currentIndex = bounded
        val screen = screens[bounded]
        contentHost.removeAllViews()
        contentHost.addView(
            screen.view(),
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        screen.onShow()
        if (moveFocus) screen.focusFirst()
        updateStatus()
    }

    fun navigateTo(label: String) {
        val index = screens.indexOfFirst { it.label == label }
        if (index >= 0) select(index, moveFocus = true)
    }

    fun refreshCurrentScreen() {
        screens.getOrNull(currentIndex)?.onShow()
        updateStatus()
    }

    fun refreshGuideNow() {
        if (container.isRefreshing()) return
        updateStatus()
        container.refreshGuide { result ->
            updateStatus()
            screens.getOrNull(currentIndex)?.onShow()
            if (!result.ok) toast(result.errors.firstOrNull() ?: result.message)
        }
    }

    fun updateStatus() {
        if (!::statusView.isInitialized) return
        val settings = container.settings
        statusView.text = when {
            container.isRefreshing() -> "Refreshing guide\u2026"
            settings.lastRefreshMs == 0L -> "Guide not loaded yet"
            else -> buildString {
                append(if (settings.lastRefreshOk) "Guide updated " else "Guide error ")
                append(Fmt.ago(settings.lastRefreshMs))
                if (!settings.lastRefreshOk && settings.lastRefreshMessage.isNotBlank()) {
                    append("\n")
                    append(settings.lastRefreshMessage.take(120))
                }
            }
        }
    }

    fun playChannel(channel: Channel, subtitle: String = "") {
        try {
            startActivity(PlaybackRequest.intent(this, channel, subtitle))
        } catch (e: ActivityNotFoundException) {
            toast("No video player available")
        }
    }

    fun showItemMenu(title: String, items: List<String>, onSelected: (Int) -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(items.toTypedArray()) { _, which -> onSelected(which) }
            .show()
    }

    fun confirm(title: String, message: String, confirmLabel: String, onConfirm: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(confirmLabel) { _, _ -> onConfirm() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun promptText(
        heading: String,
        initial: String = "",
        hint: String = "",
        allowUrlSymbols: Boolean = false,
        onResult: (String) -> Unit,
    ) {
        TextEntryDialog(this, heading, initial, hint, allowUrlSymbols, onResult).show()
    }

    fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun dp(value: Int): Int = UiKit.dp(this, value.toFloat())

    companion object {
        private const val STATE_SCREEN = "screen"
        const val TAG_ROW_ACTION = "row-action"
    }
}
