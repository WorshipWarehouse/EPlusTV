package com.gameday.tv.epg

import com.gameday.tv.FakeTransport
import com.gameday.tv.TestFixtures
import com.gameday.tv.core.MemoryKeyValueStore
import com.gameday.tv.model.SourceConfig
import com.gameday.tv.model.SourceKind
import com.gameday.tv.providers.JsonSourceStore
import com.gameday.tv.storage.MemorySecretStore
import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GuideRepositoryTest {

    private lateinit var cacheFile: File
    private lateinit var cache: GuideCache
    private lateinit var sourceStore: JsonSourceStore
    private lateinit var transport: FakeTransport

    private val playlistUrl = "https://provider.test/channels.m3u"
    private val epgUrl = "https://provider.test/xmltv.xml"

    @Before
    fun setUp() {
        cacheFile = File(Files.createTempDirectory("gameday-guide").toFile(), "guide.json")
        cache = GuideCache(cacheFile)
        sourceStore = JsonSourceStore(MemoryKeyValueStore(), MemorySecretStore())
        transport = FakeTransport()
    }

    private fun repository() = GuideRepository(transport, sourceStore, cache)

    private fun addM3uSource() {
        sourceStore.save(
            SourceConfig(
                id = "src",
                name = "Test provider",
                kind = SourceKind.M3U,
                url = playlistUrl,
                xmltvUrl = epgUrl,
            ),
        )
    }

    @Test
    fun `refresh merges playlist and epg into the cache`() {
        val now = System.currentTimeMillis()
        addM3uSource()
        transport.respond(
            playlistUrl,
            TestFixtures.playlist(
                Triple("tvg-id=\"101.eplustv\" tvg-chno=\"101\"", "NFL", "https://cdn.test/101.m3u8"),
                Triple("tvg-id=\"102.eplustv\" tvg-chno=\"102\"", "RedZone", "https://cdn.test/102.m3u8"),
            ),
        )
        transport.respond(
            epgUrl,
            TestFixtures.xmltv(
                programmes = listOf(
                    Triple(
                        "101.eplustv",
                        TestFixtures.xmltvTime(now - 3_600_000L),
                        TestFixtures.xmltvTime(now + 2 * 3_600_000L),
                    ),
                    Triple(
                        "999.eplustv",
                        TestFixtures.xmltvTime(now - 3_600_000L),
                        TestFixtures.xmltvTime(now + 3_600_000L),
                    ),
                ),
            ),
        )

        val result = repository().refresh(now)

        assertTrue(result.ok)
        assertEquals(2, result.channelCount)
        assertEquals(1, result.programCount)
        assertTrue(result.durationMs >= 0)
        val cached = cache.read()
        assertNotNull(cached)
        assertEquals(2, cached!!.channels.size)
        // 999.eplustv is not in the playlist, so its programme is dropped.
        assertEquals("101.eplustv", cached.programs.single().channelId)
    }

    @Test
    fun `a failed refresh keeps the previous guide`() {
        addM3uSource()
        transport.respond(
            playlistUrl,
            TestFixtures.playlist(Triple("tvg-id=\"1\"", "One", "https://cdn.test/1.m3u8")),
        )
        assertTrue(repository().refresh().ok)

        val failingTransport = FakeTransport().apply { fail(playlistUrl, IOException("network down")) }
        val failingRepository = GuideRepository(failingTransport, sourceStore, cache)
        val result = failingRepository.refresh()

        assertFalse(result.ok)
        assertTrue(result.errors.isNotEmpty())
        val cached = cache.read()
        assertNotNull(cached)
        assertEquals(1, cached!!.channels.size)
    }

    @Test
    fun `direct source produces one channel without epg`() {
        sourceStore.save(
            SourceConfig(
                id = "direct",
                name = "Manual",
                kind = SourceKind.DIRECT,
                url = "https://cdn.test/live.m3u8",
            ),
        )

        val result = repository().refresh()

        assertTrue(result.ok)
        assertEquals(1, result.channelCount)
        assertEquals(0, result.programCount)
        assertEquals("https://cdn.test/live.m3u8", cache.read()!!.channels.single().streamUrl)
    }

    @Test
    fun `refresh without sources reports a clear message`() {
        val result = repository().refresh()
        assertFalse(result.ok)
        assertEquals("No sources configured", result.message)
    }

    @Test
    fun `source headers are sent with guide requests`() {
        addM3uSource()
        sourceStore.saveHeaders("src", mapOf("Authorization" to "Bearer test-token"))
        transport.respond(
            playlistUrl,
            TestFixtures.playlist(Triple("tvg-id=\"1\"", "One", "https://cdn.test/1.m3u8")),
        )

        repository().refresh()

        assertEquals("Bearer test-token", transport.lastHeaders["Authorization"])
    }

    @Test
    fun `eplustv source derives endpoints from the base url`() {
        val now = System.currentTimeMillis()
        val base = "http://192.168.1.20:3000"
        sourceStore.save(
            SourceConfig(id = "gateway", name = "EPlusTV", kind = SourceKind.EPLUSTV, url = base),
        )
        transport.respond(
            "$base/channels.m3u",
            TestFixtures.playlist(Triple("tvg-id=\"1\"", "One", "http://192.168.1.20:3000/channels/1.m3u8")),
        )
        transport.respond(
            "$base/xmltv.xml",
            TestFixtures.xmltv(
                programmes = listOf(
                    Triple(
                        "1",
                        TestFixtures.xmltvTime(now - 1_800_000L),
                        TestFixtures.xmltvTime(now + 3_600_000L),
                    ),
                ),
            ),
        )

        val result = repository().refresh(now)

        assertTrue(result.ok)
        assertEquals(1, result.channelCount)
        assertEquals(1, result.programCount)
    }
}
