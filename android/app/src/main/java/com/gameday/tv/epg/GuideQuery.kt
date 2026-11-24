package com.gameday.tv.epg

import com.gameday.tv.model.Channel
import com.gameday.tv.model.ChannelNow
import com.gameday.tv.model.GuideSnapshot
import com.gameday.tv.model.Program
import com.gameday.tv.model.SearchHit

/** Pure guide lookups so every screen and test shares one definition of "now". */
object GuideQuery {

    fun programsByChannel(programs: List<Program>): Map<String, List<Program>> =
        programs.groupBy { it.channelId }
            .mapValues { entry -> entry.value.sortedBy { it.startMs } }

    fun nowOn(
        channels: List<Channel>,
        byChannel: Map<String, List<Program>>,
        nowMs: Long,
    ): List<ChannelNow> = channels.map { channel ->
        val programs = byChannel[channel.id].orEmpty()
        val current = programs.lastOrNull { it.startMs <= nowMs && it.stopMs > nowMs }
        val next = programs.firstOrNull { it.startMs > nowMs }
        ChannelNow(channel = channel, now = current, next = next)
    }

    fun upcoming(
        channels: List<Channel>,
        byChannel: Map<String, List<Program>>,
        fromMs: Long,
        horizonMs: Long,
        limit: Int = 40,
    ): List<Pair<Channel, Program>> {
        val channelIndex = channels.associateBy { it.id }
        val cutoff = fromMs + horizonMs
        return byChannel.entries
            .asSequence()
            .flatMap { (channelId, programs) ->
                val channel = channelIndex[channelId] ?: return@flatMap emptySequence()
                programs.asSequence()
                    .filter { it.startMs in fromMs..cutoff }
                    .map { channel to it }
            }
            .sortedBy { it.second.startMs }
            .take(limit)
            .toList()
    }

    fun favoritesOnly(channels: List<Channel>, favoriteIds: Set<String>): List<Channel> =
        channels.filter { favoriteIds.contains(it.id) }

    fun search(guide: GuideSnapshot, query: String, limit: Int = 60): List<SearchHit> {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty()) return emptyList()
        val channelIndex = guide.channels.associateBy { it.id }
        val hits = ArrayList<SearchHit>()

        guide.channels.forEach { channel ->
            if (channel.name.lowercase().contains(normalized)) {
                hits += SearchHit(channel, null, "channel")
            }
        }

        guide.programs.forEach { program ->
            val channel = channelIndex[program.channelId] ?: return@forEach
            val titleMatch = program.title.lowercase().contains(normalized)
            if (titleMatch) {
                hits += SearchHit(channel, program, "title")
            }
        }

        return hits.sortedByDescending { hit -> hit.program?.startMs ?: Long.MAX_VALUE }
            .distinctBy { hit -> "${hit.channel.id}-${hit.program?.startMs ?: 0}-${hit.program?.title.orEmpty()}" }
            .take(limit)
    }
}
