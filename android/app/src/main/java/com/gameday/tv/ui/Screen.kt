package com.gameday.tv.ui

import android.view.View

/** A full-screen section inside [MainActivity]; views are built once and reused. */
abstract class Screen(protected val host: MainActivity) {

    abstract val label: String

    private var root: View? = null

    fun view(): View = root ?: build().also { root = it }

    protected abstract fun build(): View

    /** Called every time the screen becomes visible; refresh data here. */
    open fun onShow() {}

    open fun focusFirst() {}
}
