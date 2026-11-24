package com.gameday.tv.epg

import com.gameday.tv.model.Channel
import com.gameday.tv.model.Program

/**
 * Bounds the in-memory and on-disk guide so a huge XMLTV feed cannot exhaust the
 * 1.5 GB available on the minimum target device.
 */
object GuidePruner {

    data class Result(val channels: List<Channel>, val programs: List<Program>)

    fun prune(
        channels: List<Channel>,
        programs: List<Program>,
        nowMs: Long,
        keepPastMs: Long = 3 * 60 * 60 * 1000L,
        futureHorizonMs: Long = 7 * 24 * 60 * 60 * 1000L,
        maxChannels: Int = 1_000,
        maxPrograms: Int = 20_000,
    ): Result {
        val retainedChannels = channels
            .distinctBy { it.id }
            .sortedBy { it.number }
            .take(maxChannels)
        val channelIds = retainedChannels.mapTo(HashSet()) { it.id }

        val earliest = nowMs - keepPastMs
        val latest = nowMs + futureHorizonMs

        val retainedPrograms = programs.asSequence()
            .filter { channelIds.contains(it.channelId) }
            .filter { it.stopMs > earliest && it.startMs < latest }
            .sortedBy { it.startMs }
            .take(maxPrograms)
            .toList()

        return Result(retainedChannels, retainedPrograms)
    }
}
