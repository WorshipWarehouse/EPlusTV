package com.gameday.tv.ui

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gameday.tv.R

abstract class ListScreen(host: MainActivity, private val heading: String) : Screen(host) {

    protected val adapter = TvListAdapter()
    protected val headerActions = LinearLayout(host)
    protected lateinit var subtitleView: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView

    override fun build(): View {
        val root = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(34), dp(26), dp(34), 0)
        }

        val header = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleColumn = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleColumn.addView(UiKit.text(host, heading, 27f, bold = true))
        subtitleView = UiKit.text(host, "", 13f, R.color.text_secondary).apply {
            setPadding(0, dp(5), 0, 0)
        }
        titleColumn.addView(subtitleView)
        header.addView(titleColumn)

        headerActions.orientation = LinearLayout.HORIZONTAL
        headerActions.gravity = Gravity.CENTER_VERTICAL
        header.addView(headerActions)
        root.addView(header)

        recyclerView = RecyclerView(host).apply {
            layoutManager = LinearLayoutManager(host)
            adapter = this@ListScreen.adapter
            setPadding(0, dp(10), 0, dp(26))
            clipToPadding = false
            isFocusable = false
            isNestedScrollingEnabled = false
        }
        root.addView(
            recyclerView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )

        emptyView = UiKit.text(host, "", 15f, R.color.text_secondary).apply {
            setPadding(dp(20), dp(24), dp(20), dp(20))
            visibility = View.GONE
        }
        root.addView(emptyView)
        return root
    }

    protected fun setItems(items: List<TvItem>, emptyMessage: String = "Nothing to show yet.") {
        adapter.submit(items)
        val isEmpty = items.none { it is TvItem.Entry }
        recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
        emptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        emptyView.text = emptyMessage
    }

    override fun onShow() {
        render()
    }

    protected abstract fun render()

    protected fun dp(value: Int): Int = UiKit.dp(host, value.toFloat())

    override fun focusFirst() {
        recyclerView.postDelayed({
            var target: View? = null
            for (index in 0 until recyclerView.childCount) {
                val child = recyclerView.getChildAt(index)
                if (child.isFocusable) {
                    target = child
                    break
                }
            }
            if (target != null) {
                target.requestFocus()
            } else if (headerActions.childCount > 0) {
                headerActions.getChildAt(0)?.requestFocus()
            }
        }, 60L)
    }
}
