package com.gameday.tv.ui

import com.gameday.tv.core.Fmt
import com.gameday.tv.model.Channel
import com.gameday.tv.model.Program

object UiRows {

    fun liveEntry(
        channel: Channel,
        now: Program?,
        next: Program?,
        favorite: Boolean,
        nowMs: Long,
        onPlay: () -> Unit,
        onToggleFavorite: () -> Unit,
    ): TvItem.Entry {
        val subtitle = when {
            now != null -> now.title
            next != null -> "Next: ${next.title} at ${Fmt.clock(next.startMs)}"
            else -> "No guide data"
        }
        val meta = now?.let { Fmt.range(it.startMs, it.stopMs) }.orEmpty()
        val progress = now?.let { Fmt.progressPercent(it.startMs, it.stopMs, nowMs) } ?: -1
        return TvItem.Entry(
            key = channel.id,
            title = channel.name,
            subtitle = subtitle,
            meta = meta,
            badge = channel.number.toString(),
            progress = progress,
            favorite = favorite,
            action = onPlay,
            longAction = onToggleFavorite,
        )
    }

    fun programEntry(
        channel: Channel,
        program: Program,
        nowMs: Long,
        onPlay: () -> Unit,
    ): TvItem.Entry = TvItem.Entry(
        key = "${channel.id}-${program.startMs}",
        title = program.title,
        subtitle = "${channel.name} \u00b7 ${Fmt.dayLabel(program.startMs, nowMs)}",
        meta = Fmt.range(program.startMs, program.stopMs),
        badge = channel.number.toString(),
        progress = Fmt.progressPercent(program.startMs, program.stopMs, nowMs),
        action = onPlay,
    )
}
