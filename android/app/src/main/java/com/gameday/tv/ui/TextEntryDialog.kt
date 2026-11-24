package com.gameday.tv.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * D-pad text entry. Fire TV has no useful hardware keyboard, so a full on-screen keyboard is
 * provided; nothing in GameDay requires a mouse or a phone.
 */
class TextEntryDialog(
    context: Context,
    private val heading: String,
    initial: String = "",
    hint: String = "",
    private val allowUrlSymbols: Boolean = false,
    private val onResult: (String) -> Unit,
) : Dialog(context, com.gameday.tv.R.style.Theme_GameDay_Dialog) {

    private val value = StringBuilder(initial)
    private lateinit var valueView: TextView
    private val keyViews = ArrayList<TextView>()

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setCancelable(true)
        buildUi(hint)
    }

    private fun buildUi(hint: String) {
        val context = context
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(24), dp(28), dp(24))
            background = UiKit.rounded(UiKit.color(context, com.gameday.tv.R.color.surface), dp(14), UiKit.color(context, com.gameday.tv.R.color.surface_high), dp(2))
        }

        panel.addView(UiKit.text(context, heading, 20f, bold = true))
        if (hint.isNotBlank()) {
            panel.addView(UiKit.text(context, hint, 13f, com.gameday.tv.R.color.text_secondary).apply {
                setPadding(0, dp(6), 0, 0)
            })
        }

        valueView = UiKit.text(context, displayValue(), 18f, com.gameday.tv.R.color.accent).apply {
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = UiKit.rounded(UiKit.color(context, com.gameday.tv.R.color.bg), dp(8))
            maxLines = 2
        }
        panel.addView(valueView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(14)
        })

        val keyboard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(14), 0, 0)
        }

        addRow(keyboard, "1234567890")
        addRow(keyboard, "qwertyuiop")
        addRow(keyboard, "asdfghjkl")
        addRow(keyboard, "zxcvbnm")
        if (allowUrlSymbols) addRow(keyboard, ":/.-_@?&=%")

        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        }
        actions.addView(key("Space", weight = 2f) { append(" ") })
        actions.addView(key("Delete", weight = 1.5f) { deleteLast() })
        actions.addView(key("Clear", weight = 1.5f) { value.setLength(0); refreshValue() })
        actions.addView(key("Cancel", weight = 1.5f) { dismiss() })
        actions.addView(key("Done", weight = 2f) {
            onResult(value.toString().trim())
            dismiss()
        })
        keyboard.addView(actions)

        panel.addView(keyboard)
        setContentView(panel)

        window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setDimAmount(0.75f)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun addRow(parent: LinearLayout, keys: String) {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        keys.forEach { character ->
            row.addView(key(character.toString(), weight = 1f) { append(character.toString()) })
        }
        parent.addView(row)
    }

    private fun key(label: String, weight: Float, onClick: () -> Unit): TextView =
        UiKit.text(context, label, 15f).apply {
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(12), dp(4), dp(12))
            background = UiKit.focusBackground(context, com.gameday.tv.R.color.surface_high)
            UiKit.focusable(this)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight).apply {
                setMargins(dp(3), dp(3), dp(3), dp(3))
            }
            setOnClickListener { onClick() }
            keyViews += this
        }

    private fun append(text: String) {
        if (value.length >= 512) return
        value.append(text)
        refreshValue()
    }

    private fun deleteLast() {
        if (value.isNotEmpty()) value.setLength(value.length - 1)
        refreshValue()
    }

    private fun refreshValue() {
        valueView.text = displayValue()
    }

    private fun displayValue(): String =
        if (value.isEmpty()) "\u2026" else "$value\u258f"

    override fun show() {
        super.show()
        window?.decorView?.post {
            keyViews.firstOrNull { it.text == "q" }?.requestFocus()
                ?: keyViews.firstOrNull()?.requestFocus()
        }
    }

    private fun dp(value: Int): Int = UiKit.dp(context, value.toFloat())
}
