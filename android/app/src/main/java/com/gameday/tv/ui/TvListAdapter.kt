package com.gameday.tv.ui

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.gameday.tv.R

sealed class TvItem {
    data class Header(val text: String) : TvItem()

    data class Entry(
        val key: String,
        val title: String,
        val subtitle: String = "",
        val meta: String = "",
        val badge: String = "",
        val progress: Int = -1,
        val favorite: Boolean = false,
        val action: () -> Unit,
        val longAction: (() -> Unit)? = null,
    ) : TvItem()
}

/** One vertical, fully D-pad navigable list used by most screens. */
class TvListAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = ArrayList<TvItem>()

    fun submit(newItems: List<TvItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int =
        if (items[position] is TvItem.Header) TYPE_HEADER else TYPE_ENTRY

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val context = parent.context
        return if (viewType == TYPE_HEADER) {
            val view = UiKit.text(context, "", 13f, R.color.text_secondary).apply {
                letterSpacing = 0.12f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(UiKit.dp(context, 20f), UiKit.dp(context, 20f), UiKit.dp(context, 20f), UiKit.dp(context, 6f))
            }
            HeaderHolder(view)
        } else {
            EntryHolder(buildEntryView(context))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is TvItem.Header -> (holder as HeaderHolder).text.text = item.text.uppercase()
            is TvItem.Entry -> (holder as EntryHolder).bind(item)
        }
    }

    private fun buildEntryView(context: Context): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = UiKit.dp(context, 3f)
                bottomMargin = UiKit.dp(context, 3f)
                marginStart = UiKit.dp(context, 16f)
                marginEnd = UiKit.dp(context, 16f)
            }
            setPadding(UiKit.dp(context, 14f), UiKit.dp(context, 12f), UiKit.dp(context, 16f), UiKit.dp(context, 12f))
            background = UiKit.focusBackground(context)
            UiKit.focusable(this)
            clipToOutline = true
            isSoundEffectsEnabled = false
        }

        val badge = UiKit.text(context, "", 14f, R.color.accent, bold = true).apply {
            gravity = Gravity.CENTER
            width = UiKit.dp(context, 54f)
        }
        row.addView(badge)

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val title = UiKit.text(context, "", 17f)
        val subtitle = UiKit.text(context, "", 13f, R.color.text_secondary).apply {
            maxLines = 2
            setPadding(0, UiKit.dp(context, 3f), 0, 0)
        }
        val progressTrack = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UiKit.dp(context, 3f),
            ).apply { topMargin = UiKit.dp(context, 7f) }
            background = UiKit.rounded(UiKit.color(context, R.color.surface_high), UiKit.dp(context, 2f))
        }
        val progressFill = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT)
            background = UiKit.rounded(UiKit.color(context, R.color.accent), UiKit.dp(context, 2f))
        }
        progressTrack.addView(progressFill)
        column.addView(title)
        column.addView(subtitle)
        column.addView(progressTrack)
        row.addView(column)

        val meta = UiKit.text(context, "", 13f, R.color.text_secondary).apply {
            gravity = Gravity.END
            setPadding(UiKit.dp(context, 16f), 0, 0, 0)
        }
        row.addView(meta)

        val star = UiKit.text(context, "", 16f, R.color.accent).apply {
            setPadding(UiKit.dp(context, 10f), 0, 0, 0)
        }
        row.addView(star)

        row.setTag(R.id.tag_row_title, title)
        row.setTag(R.id.tag_row_subtitle, subtitle)
        row.setTag(R.id.tag_row_meta, meta)
        row.setTag(R.id.tag_row_badge, badge)
        row.setTag(R.id.tag_row_progress, progressFill)
        row.setTag(R.id.tag_row_progress_track, progressTrack)
        row.setTag(R.id.tag_row_star, star)
        return row
    }

    private class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view as TextView
    }

    private class EntryHolder(private val row: LinearLayout) : RecyclerView.ViewHolder(row) {
        private val title: TextView = row.getTag(R.id.tag_row_title) as TextView
        private val subtitle: TextView = row.getTag(R.id.tag_row_subtitle) as TextView
        private val meta: TextView = row.getTag(R.id.tag_row_meta) as TextView
        private val badge: TextView = row.getTag(R.id.tag_row_badge) as TextView
        private val star: TextView = row.getTag(R.id.tag_row_star) as TextView
        private val progressFill: View = row.getTag(R.id.tag_row_progress) as View
        private val progressTrack: View = row.getTag(R.id.tag_row_progress_track) as View

        fun bind(item: TvItem.Entry) {
            title.text = item.title
            subtitle.text = item.subtitle
            subtitle.visibility = if (item.subtitle.isBlank()) View.GONE else View.VISIBLE
            meta.text = item.meta
            meta.visibility = if (item.meta.isBlank()) View.GONE else View.VISIBLE
            badge.text = item.badge
            badge.visibility = if (item.badge.isBlank()) View.GONE else View.VISIBLE
            star.text = if (item.favorite) "\u2605" else ""
            star.visibility = if (item.favorite) View.VISIBLE else View.GONE

            if (item.progress in 0..100) {
                progressTrack.visibility = View.VISIBLE
                progressFill.post {
                    val width = (progressTrack.width * (item.progress / 100f)).toInt()
                    progressFill.layoutParams = (progressFill.layoutParams as FrameLayout.LayoutParams).apply {
                        this.width = width
                    }
                    progressFill.requestLayout()
                }
            } else {
                progressTrack.visibility = View.GONE
            }

            row.setOnClickListener { item.action() }
            row.setOnLongClickListener {
                item.longAction?.let { action ->
                    action()
                    true
                } ?: false
            }
            UiKit.scaleOnFocus(row)
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ENTRY = 1
    }
}
