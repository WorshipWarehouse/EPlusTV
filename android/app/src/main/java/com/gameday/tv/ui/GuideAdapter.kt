package com.gameday.tv.ui

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.gameday.tv.R
import com.gameday.tv.core.Fmt
import com.gameday.tv.model.Channel
import com.gameday.tv.model.Program

/** One guide row: fixed channel cell plus a horizontally scrollable programme timeline. */
class GuideAdapter(
    private val pxPerMinute: Int,
    private val nowMsProvider: () -> Long,
    private val onPlay: (Channel, Program?) -> Unit,
    private val onToggleFavorite: (Channel) -> Unit,
    private val favoriteIds: () -> Set<String>,
) : RecyclerView.Adapter<GuideAdapter.RowHolder>() {

    data class GuideRow(val channel: Channel, val programs: List<Program>)

    private val rows = ArrayList<GuideRow>()
    private var windowStartMs = 0L

    fun submit(newRows: List<GuideRow>, startMs: Long) {
        rows.clear()
        rows.addAll(newRows)
        windowStartMs = startMs
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = rows.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder =
        RowHolder(buildRow(parent.context))

    override fun onBindViewHolder(holder: RowHolder, position: Int) {
        holder.bind(rows[position])
    }

    private fun buildRow(context: Context): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UiKit.dp(context, 92f),
            ).apply { bottomMargin = UiKit.dp(context, 4f) }
        }

        val channelCell = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(UiKit.dp(context, 12f), 0, UiKit.dp(context, 8f), 0)
            layoutParams = LinearLayout.LayoutParams(UiKit.dp(context, 150f), ViewGroup.LayoutParams.MATCH_PARENT)
            background = UiKit.focusBackground(context, R.color.surface)
            UiKit.focusable(this)
            isSoundEffectsEnabled = false
        }
        val number = UiKit.text(context, "", 15f, R.color.accent, bold = true)
        val name = UiKit.text(context, "", 13f).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, UiKit.dp(context, 3f), 0, 0)
        }
        channelCell.addView(number)
        channelCell.addView(name)

        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        }
        val blocks = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, UiKit.dp(context, 6f), 0, UiKit.dp(context, 6f))
        }
        scroll.addView(blocks)

        row.addView(channelCell)
        row.addView(scroll)

        row.setTag(R.id.tag_guide_channel_cell, channelCell)
        row.setTag(R.id.tag_guide_number, number)
        row.setTag(R.id.tag_guide_name, name)
        row.setTag(R.id.tag_guide_scroll, scroll)
        row.setTag(R.id.tag_guide_blocks, blocks)
        return row
    }

    inner class RowHolder(private val row: LinearLayout) : RecyclerView.ViewHolder(row) {
        private val context: Context = row.context
        private val channelCell: LinearLayout = row.getTag(R.id.tag_guide_channel_cell) as LinearLayout
        private val number: TextView = row.getTag(R.id.tag_guide_number) as TextView
        private val name: TextView = row.getTag(R.id.tag_guide_name) as TextView
        private val scroll: HorizontalScrollView = row.getTag(R.id.tag_guide_scroll) as HorizontalScrollView
        private val blocks: LinearLayout = row.getTag(R.id.tag_guide_blocks) as LinearLayout

        fun bind(guideRow: GuideRow) {
            number.text = guideRow.channel.number.toString()
            name.text = guideRow.channel.name
            channelCell.setOnClickListener { onPlay(guideRow.channel, null) }
            channelCell.setOnLongClickListener {
                onToggleFavorite(guideRow.channel)
                true
            }
            UiKit.scaleOnFocus(channelCell)

            blocks.removeAllViews()
            if (guideRow.programs.isEmpty()) {
                blocks.addView(
                    UiKit.text(context, "No guide data", 13f, R.color.text_secondary).apply {
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(UiKit.dp(context, 14f), 0, 0, 0)
                    },
                )
            } else {
                guideRow.programs.forEach { program -> blocks.addView(buildBlock(guideRow.channel, program)) }
            }

            val offset = (((nowMsProvider() - windowStartMs) / 60_000.0) * pxPerMinute).toInt() -
                UiKit.dp(context, 30f)
            scroll.post { scroll.scrollTo(offset.coerceAtLeast(0), 0) }
        }

        private fun buildBlock(channel: Channel, program: Program): LinearLayout {
            val minutes = ((program.stopMs - program.startMs) / 60_000L).toInt().coerceAtLeast(10)
            val width = (minutes * pxPerMinute).coerceAtLeast(UiKit.dp(context, 96f))
            val block = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(UiKit.dp(context, 10f), UiKit.dp(context, 6f), UiKit.dp(context, 10f), UiKit.dp(context, 6f))
                layoutParams = LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT)
                    .apply { marginEnd = UiKit.dp(context, 4f) }
                background = UiKit.focusBackground(context, R.color.surface_high)
                UiKit.focusable(this)
                isSoundEffectsEnabled = false
            }
            val isFavorite = favoriteIds().contains(channel.id)
            val titleRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            titleRow.addView(
                UiKit.text(context, Fmt.clock(program.startMs), 10.5f, R.color.accent).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                },
            )
            if (isFavorite) {
                titleRow.addView(UiKit.text(context, "\u2605", 11f, R.color.accent))
            }
            val title = UiKit.text(context, program.title, 13f).apply {
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, UiKit.dp(context, 2f), 0, 0)
            }
            block.addView(titleRow)
            block.addView(title)
            block.setOnClickListener { onPlay(channel, program) }
            UiKit.scaleOnFocus(block)
            return block
        }
    }
}
