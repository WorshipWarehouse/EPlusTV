package com.gameday.tv.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.gameday.tv.R

/** TV styling helpers. Every interactive element gets a visible focus state (outline + scale). */
object UiKit {

    fun dp(context: Context, value: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value,
        context.resources.displayMetrics,
    ).toInt()

    fun color(context: Context, colorRes: Int): Int = ContextCompat.getColor(context, colorRes)

    fun rounded(color: Int, radiusPx: Int, strokeColor: Int? = null, strokeWidthPx: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx.toFloat()
            setColor(color)
            if (strokeColor != null && strokeWidthPx > 0) setStroke(strokeWidthPx, strokeColor)
        }

    fun focusBackground(
        context: Context,
        normalColorRes: Int = R.color.surface,
        focusedColorRes: Int = R.color.focus_fill,
        showOutline: Boolean = true,
    ): StateListDrawable {
        val normal = color(context, normalColorRes)
        val focused = color(context, focusedColorRes)
        val accent = color(context, R.color.accent)
        val radius = dp(context, 10f)
        val stroke = if (showOutline) accent else null
        return StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_focused),
                rounded(focused, radius, stroke, dp(context, 2f)),
            )
            addState(
                intArrayOf(android.R.attr.state_pressed),
                rounded(focused, radius, stroke, dp(context, 2f)),
            )
            addState(intArrayOf(), rounded(normal, radius, null, 0))
        }
    }

    fun text(
        context: Context,
        value: String,
        sizeSp: Float = 16f,
        colorRes: Int = R.color.text_primary,
        bold: Boolean = false,
    ): TextView = TextView(context).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color(context, colorRes))
        if (bold) setTypeface(typeface, Typeface.BOLD)
        includeFontPadding = false
    }

    fun focusable(view: View) {
        view.isFocusable = true
        view.isFocusableInTouchMode = false
        view.isClickable = true
    }

    fun scaleOnFocus(view: View) {
        view.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            v.animate()
                .scaleX(if (hasFocus) 1.03f else 1f)
                .scaleY(if (hasFocus) 1.03f else 1f)
                .setDuration(110)
                .start()
        }
    }

    fun button(context: Context, label: String, onClick: () -> Unit): TextView =
        text(context, label, 15f, R.color.text_primary).apply {
            gravity = Gravity.CENTER
            setPadding(dp(context, 20f), dp(context, 11f), dp(context, 20f), dp(context, 11f))
            background = focusBackground(context, R.color.surface_high)
            focusable(this)
            setOnClickListener { onClick() }
            scaleOnFocus(this)
        }
}
