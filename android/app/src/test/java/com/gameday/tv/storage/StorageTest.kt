package com.gameday.tv.storage

import com.gameday.tv.core.MemoryKeyValueStore
import com.gameday.tv.model.SourceConfig
import com.gameday.tv.model.SourceKind
import com.gameday.tv.providers.HeaderParser
import com.gameday.tv.providers.JsonSourceStore
import com.gameday.tv.providers.StoreFavoritesStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTest {

    @Test
    fun `migration seeds defaults and records the schema version`() {
        val store = MemoryKeyValueStore()
        val settings = Settings(store)

        val result = settings.migrate()

        assertTrue(result.changed)
        assertEquals(0, result.fromVersion)
        assertEquals(Settings.SCHEMA_VERSION, result.toVersion)
        assertEquals(Settings.DEFAULT_REFRESH_HOURS, settings.refreshIntervalHours)
        assertTrue(settings.autoRefresh)
        assertTrue(settings.updateManifestUrl.isEmpty())
    }

    @Test
    fun `migration preserves values a user already has`() {
        val store = MemoryKeyValueStore(
            mapOf(
                "settings.refreshHours" to 12L,
                "settings.autoRefresh" to false,
                "settings.updateManifestUrl" to "https://example.test/manifest.json",
                "settings.subtitles" to true,
            ),
        )
        val settings = Settings(store)

        settings.migrate()

        assertEquals(12, settings.refreshIntervalHours)
        assertFalse(settings.autoRefresh)
        assertEquals("https://example.test/manifest.json", settings.updateManifestUrl)
        assertTrue(settings.subtitlesEnabled)
    }

    @Test
    fun `migration is idempotent`() {
        val settings = Settings(MemoryKeyValueStore())
        settings.migrate()

        val second = settings.migrate()

        assertFalse(second.changed)
        assertEquals(Settings.SCHEMA_VERSION, second.toVersion)
    }

    @Test
    fun `refresh interval is clamped to a sane range`() {
        val settings = Settings(MemoryKeyValueStore())

        settings.refreshIntervalHours = 1_000
        assertEquals(48, settings.refreshIntervalHours)

        settings.refreshIntervalHours = 0
        assertEquals(1, settings.refreshIntervalHours)
    }

    @Test
    fun `settings round trip through the store`() {
        val store = MemoryKeyValueStore()
        val settings = Settings(store)
        settings.migrate()
        settings.preferredAudioLanguage = "spa"
        settings.dataSaver = true
        settings.lastRefreshMs = 1_234L

        val reopened = Settings(store)

        assertEquals("spa", reopened.preferredAudioLanguage)
        assertTrue(reopened.dataSaver)
        assertEquals(1_234L, reopened.lastRefreshMs)
    }
}

class SourceStoreTest {

    private val secrets = MemorySecretStore()

    private fun newStore(prefs: MemoryKeyValueStore = MemoryKeyValueStore()) =
        JsonSourceStore(prefs, secrets)

    private fun source(id: String = "src-1") = SourceConfig(
        id = id,
        name = "Provider",
        kind = SourceKind.M3U,
        url = "https://provider.test/channels.m3u",
    )

    @Test
    fun `sources are saved, replaced and listed`() {
        val store = newStore()
        store.save(source())
        assertEquals(1, store.list().size)

        store.save(source().copy(name = "Renamed"))
        assertEquals(1, store.list().size)
        assertEquals("Renamed", store.get("src-1")?.name)
    }

    @Test
    fun `headers are stored encrypted and flagged on the source`() {
        val prefs = MemoryKeyValueStore()
        val store = newStore(prefs)
        store.save(source())
        store.saveHeaders("src-1", mapOf("Authorization" to "Bearer super-secret-token"))

        assertEquals("Bearer super-secret-token", store.headersFor(source())["Authorization"])
        assertTrue(store.get("src-1")?.hasHeaders == true)

        val plainPreferences = prefs.getString("sources.v1").orEmpty()
        assertFalse(plainPreferences.contains("super-secret-token"))
        assertEquals("Bearer super-secret-token", secrets.get("headers.src-1").let {
            com.gameday.tv.core.JsonCodec.json.decodeFromString<Map<String, String>>(it!!)["Authorization"]
        })
    }

    @Test
    fun `deleting a source removes its stored headers`() {
        val store = newStore()
        store.save(source())
        store.saveHeaders("src-1", mapOf("Cookie" to "session=1"))

        store.delete("src-1")

        assertTrue(store.list().isEmpty())
        assertNull(secrets.get("headers.src-1"))
        assertTrue(store.headersFor(source()).isEmpty())
    }

    @Test
    fun `corrupt source json recovers as an empty list`() {
        val prefs = MemoryKeyValueStore()
        prefs.putString("sources.v1", "{not-valid-json")
        assertTrue(newStore(prefs).list().isEmpty())
    }

    @Test
    fun `header parser accepts equals, colon and comma separators`() {
        val headers = HeaderParser.parse("User-Agent=Agent/1.0; Referer: https://x.test/, X-Token=abc")
        assertEquals("Agent/1.0", headers["User-Agent"])
        assertEquals("https://x.test/", headers["Referer"])
        assertEquals("abc", headers["X-Token"])
        assertEquals("User-Agent=Agent/1.0; Referer=https://x.test/", HeaderParser.format(mapOf("User-Agent" to "Agent/1.0", "Referer" to "https://x.test/")))
    }
}

class FavoritesTest {

    @Test
    fun `favorites toggle and persist`() {
        val store = MemoryKeyValueStore()
        val favorites = StoreFavoritesStore(store)

        assertTrue(favorites.toggle("101.eplustv"))
        assertTrue(favorites.isFavorite("101.eplustv"))
        assertFalse(favorites.toggle("101.eplustv"))

        favorites.setFavorite("102.eplustv", true)
        assertEquals(setOf("102.eplustv"), favorites.ids())
        assertEquals(setOf("102.eplustv"), StoreFavoritesStore(store).ids())

        favorites.clear()
        assertTrue(favorites.ids().isEmpty())
    }
}
