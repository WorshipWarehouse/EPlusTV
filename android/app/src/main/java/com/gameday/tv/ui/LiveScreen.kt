package com.gameday.tv.ui

import android.view.Gravity
import com.gameday.tv.core.Fmt
import com.gameday.tv.epg.GuideQuery

class LiveScreen(host: MainActivity) : ListScreen(host, "Live TV") {

    override val label: String = LABEL

    private val refreshButton = UiKit.button(host, "Refresh") { host.refreshGuideNow() }

    init {
        headerActions.addView(refreshButton)
    }

    override fun render() {
        val snapshot = host.container.guideRepository.cached()
        val favorites = host.container.favorites
        val nowMs = System.currentTimeMillis()
        val items = ArrayList<TvItem>()

        subtitleView.text = if (snapshot.isEmpty) {
            "No channels yet"
        } else {
            "Hold OK on a channel to add or remove a favorite"
        }

        if (snapshot.isEmpty) {
            items += TvItem.Entry(
                key = "add-source",
                title = "Add a stream source",
                subtitle = "Channels come from an M3U playlist or EPlusTV gateway",
                badge = "+",
                action = { host.navigateTo(SourcesScreen.LABEL) },
            )
            setItems(items, "No channels configured.")
            return
        }

        val channels = snapshot.channels.sortedBy { it.number }
        val byChannel = GuideQuery.programsByChannel(snapshot.programs)
        val now = GuideQuery.nowOn(channels, byChannel, nowMs)

        items += TvItem.Header("${channels.size} channels")
        now.forEach { entry ->
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

        setItems(items, "No channels configured.")
    }

    override fun focusFirst() {
        // Prefer the first channel row over the refresh button so D-pad down starts on content.
        super.focusFirst()
    }

    companion object {
        const val LABEL = "Live TV"
    }
}
