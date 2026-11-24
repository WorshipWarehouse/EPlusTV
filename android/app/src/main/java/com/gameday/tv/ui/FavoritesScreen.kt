package com.gameday.tv.ui

import com.gameday.tv.epg.GuideQuery

class FavoritesScreen(host: MainActivity) : ListScreen(host, "Favorites") {

    override val label: String = LABEL

    override fun render() {
        val snapshot = host.container.guideRepository.cached()
        val favorites = host.container.favorites
        val favoriteIds = favorites.ids()
        val nowMs = System.currentTimeMillis()
        val items = ArrayList<TvItem>()

        val channels = snapshot.channels
            .filter { favoriteIds.contains(it.id) }
            .sortedBy { it.number }

        subtitleView.text = if (channels.isEmpty()) {
            "Hold OK on a channel in Live TV to add a favorite"
        } else {
            "${channels.size} favorite channel(s) \u00b7 hold OK to remove"
        }

        if (channels.isEmpty()) {
            items += TvItem.Header("Favorites")
            items += TvItem.Entry(
                key = "browse-live",
                title = "Browse Live TV",
                subtitle = "Find channels to favorite",
                badge = "\u25B6",
                action = { host.navigateTo(LiveScreen.LABEL) },
            )
            setItems(items, "No favorites yet.")
            return
        }

        val byChannel = GuideQuery.programsByChannel(snapshot.programs)
        val now = GuideQuery.nowOn(channels, byChannel, nowMs)
        items += TvItem.Header("Your channels")
        now.forEach { entry ->
            items += UiRows.liveEntry(
                channel = entry.channel,
                now = entry.now,
                next = entry.next,
                favorite = true,
                nowMs = nowMs,
                onPlay = { host.playChannel(entry.channel, entry.now?.title.orEmpty()) },
                onToggleFavorite = {
                    favorites.setFavorite(entry.channel.id, false)
                    render()
                },
            )
        }
        setItems(items, "No favorites yet.")
    }

    companion object {
        const val LABEL = "Favorites"
    }
}
