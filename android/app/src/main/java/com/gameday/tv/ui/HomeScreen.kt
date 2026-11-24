package com.gameday.tv.ui

import android.view.View
import com.gameday.tv.core.Fmt
import com.gameday.tv.epg.GuideQuery

class HomeScreen(host: MainActivity) : ListScreen(host, "Home") {

    override val label: String = LABEL

    override fun render() {
        val snapshot = host.container.guideRepository.cached()
        val favorites = host.container.favorites
        val nowMs = System.currentTimeMillis()
        val items = ArrayList<TvItem>()

        if (snapshot.isEmpty) {
            subtitleView.text = "GameDay plays channels you are authorised to watch"
            items += TvItem.Header("Get started")
            items += TvItem.Entry(
                key = "add-source",
                title = "Add a stream source",
                subtitle = "M3U playlist, EPlusTV gateway on your LAN, or one HLS channel",
                badge = "+",
                action = { host.navigateTo(SourcesScreen.LABEL) },
            )
            items += TvItem.Entry(
                key = "settings",
                title = "Open settings",
                subtitle = "Refresh, playback and update options",
                badge = "\u2699",
                action = { host.navigateTo(SettingsScreen.LABEL) },
            )
            setItems(items, "No channels yet. Add a source to build your guide.")
            return
        }

        subtitleView.text = buildString {
            append("${snapshot.channels.size} channels")
            if (snapshot.programs.isNotEmpty()) append(" \u00b7 ${snapshot.programs.size} guide entries")
            append(" \u00b7 updated ${Fmt.ago(snapshot.fetchedAtMs, nowMs)}")
        }

        val sortedChannels = snapshot.channels.sortedBy { it.number }
        val byChannel = GuideQuery.programsByChannel(snapshot.programs)
        val now = GuideQuery.nowOn(sortedChannels, byChannel, nowMs)
        val liveNow = now.filter { it.now != null }

        if (liveNow.isNotEmpty()) {
            items += TvItem.Header("On now")
            liveNow.take(10).forEach { entry ->
                items += UiRows.liveEntry(
                    channel = entry.channel,
                    now = entry.now,
                    next = entry.next,
                    favorite = favorites.isFavorite(entry.channel.id),
                    nowMs = nowMs,
                    onPlay = { host.playChannel(entry.channel, entry.now?.title.orEmpty()) },
                    onToggleFavorite = {
                        favorites.toggle(entry.channel.id)
                        render()
                    },
                )
            }
        }

        val upcoming = GuideQuery.upcoming(
            channels = sortedChannels,
            byChannel = byChannel,
            fromMs = nowMs,
            horizonMs = 8 * 60 * 60 * 1000L,
            limit = 12,
        )
        if (upcoming.isNotEmpty()) {
            items += TvItem.Header("Up next (8 hours)")
            upcoming.forEach { (channel, program) ->
                items += UiRows.programEntry(
                    channel = channel,
                    program = program,
                    nowMs = nowMs,
                    onPlay = { host.playChannel(channel, program.title) },
                )
            }
        }

        if (items.none { it is TvItem.Entry }) {
            items += TvItem.Header("Channels")
            sortedChannels.take(20).forEach { channel ->
                items += UiRows.liveEntry(
                    channel = channel,
                    now = null,
                    next = null,
                    favorite = favorites.isFavorite(channel.id),
                    nowMs = nowMs,
                    onPlay = { host.playChannel(channel) },
                    onToggleFavorite = {
                        favorites.toggle(channel.id)
                        render()
                    },
                )
            }
        }

        setItems(items, "No channels in the guide yet.")
    }

    override fun focusFirst() {
        super.focusFirst()
    }

    companion object {
        const val LABEL = "Home"
    }
}
