package com.gameday.tv.core

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** TV-friendly time formatting. Avoids java.time so no desugaring is required on API 25. */
object Fmt {

    private fun formatter(pattern: String, tz: TimeZone = TimeZone.getDefault()): SimpleDateFormat =
        SimpleDateFormat(pattern, Locale.US).apply { timeZone = tz }

    fun clock(epochMs: Long): String = formatter("h:mm a").format(Date(epochMs))

    fun clock24(epochMs: Long): String = formatter("HH:mm").format(Date(epochMs))

    fun dateTime(epochMs: Long): String = formatter("EEE, MMM d h:mm a").format(Date(epochMs))

    fun hourLabel(epochMs: Long): String = formatter("h a").format(Date(epochMs))

    fun range(startMs: Long, stopMs: Long): String =
        if (stopMs > startMs) "${clock(startMs)} - ${clock(stopMs)}" else clock(startMs)

    fun dayLabel(epochMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        val day = startOfDay(epochMs)
        val today = startOfDay(nowMs)
        return when {
            day == today -> "Today"
            day == today + 86_400_000L -> "Tomorrow"
            else -> formatter("EEE, MMM d").format(Date(epochMs))
        }
    }

    fun startOfDay(epochMs: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = epochMs
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    fun durationLabel(minutes: Int): String {
        val safe = minutes.coerceAtLeast(0)
        val hours = safe / 60
        val mins = safe % 60
        return when {
            hours > 0 && mins > 0 -> "${hours}h ${mins}m"
            hours > 0 -> "${hours}h"
            else -> "${mins}m"
        }
    }

    fun relative(epochMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        val delta = epochMs - nowMs
        if (delta <= 0) return "now"
        val minutes = delta / 60_000L
        return when {
            minutes < 1 -> "in <1 min"
            minutes < 60 -> "in $minutes min"
            else -> "in ${durationLabel(minutes.toInt())}"
        }
    }

    fun ago(epochMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        if (epochMs <= 0L) return "never"
        val deltaMinutes = ((nowMs - epochMs) / 60_000L).coerceAtLeast(0L)
        return when {
            deltaMinutes < 1 -> "just now"
            deltaMinutes < 60 -> "${deltaMinutes}m ago"
            deltaMinutes < 60 * 24 -> "${deltaMinutes / 60}h ago"
            else -> "${deltaMinutes / (60 * 24)}d ago"
        }
    }

    fun progressPercent(startMs: Long, stopMs: Long, nowMs: Long): Int {
        if (stopMs <= startMs) return 0
        val percent = ((nowMs - startMs).toDouble() / (stopMs - startMs).toDouble() * 100).toInt()
        return percent.coerceIn(0, 100)
    }
}
