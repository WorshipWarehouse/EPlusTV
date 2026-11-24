package com.gameday.tv.epg

import java.util.Calendar
import java.util.TimeZone

/**
 * Parses XMLTV timestamps: `YYYYMMDDHHMMSS +ZZZZ`, optionally without seconds or offset.
 * XMLTV expresses local wall-clock time plus an offset, so epoch = local - offset.
 */
object XmltvTime {

    private val pattern = Regex(
        "^(\\d{4})(\\d{2})(\\d{2})(\\d{2})(\\d{2})(\\d{2})?\\s*(?:([+-])(\\d{2})(\\d{2}))?$",
    )

    fun parse(raw: String?): Long? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        val match = pattern.find(value) ?: return null
        val groups = match.groupValues
        val year = groups[1].toIntOrNull() ?: return null
        val month = groups[2].toIntOrNull() ?: return null
        val day = groups[3].toIntOrNull() ?: return null
        val hour = groups[4].toIntOrNull() ?: return null
        val minute = groups[5].toIntOrNull() ?: return null
        val second = groups[6].ifEmpty { "0" }.toIntOrNull() ?: 0

        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month - 1, day, hour, minute, second)
        }
        val wallClockMs = calendar.timeInMillis

        val sign = groups[7]
        val offsetMs = if (sign.isNotEmpty()) {
            val hours = groups[8].toIntOrNull() ?: 0
            val minutes = groups[9].toIntOrNull() ?: 0
            val magnitude = (hours * 60L + minutes) * 60_000L
            if (sign == "-") -magnitude else magnitude
        } else {
            0L
        }
        return wallClockMs - offsetMs
    }
}
