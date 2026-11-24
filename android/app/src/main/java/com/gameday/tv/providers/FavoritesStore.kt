package com.gameday.tv.providers

import com.gameday.tv.core.KeyValueStore

interface FavoritesStore {
    fun ids(): Set<String>
    fun isFavorite(channelId: String): Boolean
    fun setFavorite(channelId: String, favorite: Boolean)
    fun toggle(channelId: String): Boolean
    fun clear()
}

class StoreFavoritesStore(private val store: KeyValueStore) : FavoritesStore {

    override fun ids(): Set<String> = store.getStringSet(KEY_FAVORITES)

    override fun isFavorite(channelId: String): Boolean = ids().contains(channelId)

    override fun setFavorite(channelId: String, favorite: Boolean) {
        val current = ids().toMutableSet()
        if (favorite) current += channelId else current -= channelId
        store.putStringSet(KEY_FAVORITES, current)
    }

    override fun toggle(channelId: String): Boolean {
        val isFavorite = isFavorite(channelId)
        setFavorite(channelId, !isFavorite)
        return !isFavorite
    }

    override fun clear() {
        store.putStringSet(KEY_FAVORITES, emptySet())
    }

    companion object {
        private const val KEY_FAVORITES = "favorites.v1"
    }
}
