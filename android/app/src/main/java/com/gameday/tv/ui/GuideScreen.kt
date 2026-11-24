package com.gameday.tv.ui

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gameday.tv.R
import com.gameday.tv.core.Fmt
import com.gameday.tv.epg.GuideQuery

class GuideScreen(host: MainActivity) : Screen(host) {

    override val label: String = LABEL

    private val windowMs = 4 * 60 * 60 * 1000L
    private lateinit var adapter: GuideAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var subtitleView: TextView
    private lateinit var emptyView: TextView
    private lateinit var addButton: TextView
    private var windowStartMs = 0L

    override fun build(): View {
        val root = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(24), dp(28), 0)
        }

        val header = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleColumn = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleColumn.addView(UiKit.text(host, "Guide", 27f, bold = true))
        subtitleView = UiKit.text(host, "", 13f, R.color.text_secondary).apply {
            setPadding(0, dp(5), 0, 0)
        }
        titleColumn.addView(subtitleView)
        header.addView(titleColumn)
        header.addView(UiKit.button(host, "Refresh") { host.refreshGuideNow() })
        root.addView(header)

        adapter = GuideAdapter(
            pxPerMinute = dp(3),
            nowMsProvider = { System.currentTimeMillis() },
            onPlay = { channel, program -> host.playChannel(channel, program?.title.orEmpty()) },
            onToggleFavorite = { channel ->
                host.container.favorites.toggle(channel.id)
                render()
            },
            favoriteIds = { host.container.favorites.ids() },
        )

        recycler = RecyclerView(host).apply {
            layoutManager = LinearLayoutManager(host)
            adapter = this@GuideScreen.adapter
            setPadding(0, dp(12), 0, dp(20))
            clipToPadding = false
            isFocusable = false
        }
        root.addView(recycler, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        addButton = UiKit.button(host, "Add a source") { host.navigateTo(SourcesScreen.LABEL) }
        emptyView = UiKit.text(
            host,
            "No guide data yet.\nAdd an M3U playlist or an EPlusTV gateway to fill the guide.",
            15f,
            R.color.text_secondary,
        ).apply {
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        root.addView(emptyView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    override fun onShow() {
        render()
    }

    override fun focusFirst() {
        recycler.postDelayed({
            focusFirstBlock()
        }, 120L)
    }

    private fun focusFirstBlock() {
        for (rowIndex in 0 until recycler.childCount) {
            val rowView = recycler.getChildAt(rowIndex) ?: continue
            val blocks = rowView.getTag(R.id.tag_guide_blocks) as? ViewGroup ?: continue
            for (blockIndex in 0 until blocks.childCount) {
                val block = blocks.getChildAt(blockIndex)
                if (block.isFocusable) {
                    block.requestFocus()
                    return
                }
            }
        }
        addButton.requestFocus()
    }

    private fun render() {
        val snapshot = host.container.guideRepository.cached()
        val nowMs = System.currentTimeMillis()
        windowStartMs = Fmt.startOfDay(nowMs) + ((nowMs - Fmt.startOfDay(nowMs)) / windowMs) * windowMs
        val windowEndMs = windowStartMs + windowMs

        if (snapshot.channels.isEmpty()) {
            recycler.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
            subtitleView.text = "Guide needs a source"
            emptyView.text = "No guide data yet.\nAdd an M3U playlist or an EPlusTV gateway."
            return
        }

        recycler.visibility = View.VISIBLE
        emptyView.visibility = View.GONE
        subtitleView.text = buildString {
            append("${Fmt.clock(windowStartMs)} - ${Fmt.clock(windowEndMs)}")
            append("  \u00b7  left/right moves through programmes, OK watches, hold OK favorites")
        }

        val byChannel = GuideQuery.programsByChannel(snapshot.programs)
        val rows = snapshot.channels
            .sortedBy { it.number }
            .map { channel ->
                GuideAdapter.GuideRow(
                    channel = channel,
                    programs = byChannel[channel.id].orEmpty().filter {
                        it.stopMs > windowStartMs && it.startMs < windowEndMs
                    },
                )
            }
        adapter.submit(rows, windowStartMs)
    }

    private fun dp(value: Int): Int = UiKit.dp(host, value.toFloat())

    companion object {
        const val LABEL = "Guide"
    }
}
