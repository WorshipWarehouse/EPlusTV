package com.gameday.tv.epg

import com.gameday.tv.TestFixtures
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserTest {

    @Test
    fun `m3u parses attributes, numbering and display names`() {
        val playlist = TestFixtures.playlist(
            Triple(
                "tvg-id=\"101.eplustv\" tvg-chno=\"101\" tvg-name=\"NFL Net\" tvg-logo=\"https://logo/101.png\" group-title=\"Football\"",
                "NFL Network",
                "https://cdn.test/101.m3u8",
            ),
            Triple("tvg-id=\"102.eplustv\"", "RedZone", "https://cdn.test/102.m3u8"),
        )

        val channels = M3uParser.parse(playlist, "src-1")

        assertEquals(2, channels.size)
        assertEquals("101.eplustv", channels[0].id)
        assertEquals(101, channels[0].number)
        assertEquals("NFL Network", channels[0].name)
        assertEquals("Football", channels[0].group)
        assertEquals("https://logo/101.png", channels[0].logoUrl)
        assertEquals("https://cdn.test/102.m3u8", channels[1].streamUrl)
        assertEquals("src-1", channels[1].sourceId)
    }

    @Test
    fun `m3u applies vlc options and inline headers`() {
        val playlist = """
            #EXTM3U
            #EXTINF:-1 tvg-id="200.eplustv",Secure Channel
            #EXTVLCOPT:http-user-agent=CustomAgent/1.0
            #EXTVLCOPT:http-referrer=https://provider.test/
            https://cdn.test/200.m3u8|Authorization=Bearer%20abc
        """.trimIndent()

        val channel = M3uParser.parse(playlist, "src").single()

        assertEquals("CustomAgent/1.0", channel.headers["User-Agent"])
        assertEquals("https://provider.test/", channel.headers["Referer"])
        assertEquals("Bearer%20abc", channel.headers["Authorization"])
        assertEquals("https://cdn.test/200.m3u8", channel.streamUrl)
    }

    @Test
    fun `m3u ignores a playlist with no entries`() {
        assertTrue(M3uParser.parse("#EXTM3U\n", "src").isEmpty())
    }

    @Test
    fun `xmltv time handles offsets`() {
        val utc = XmltvTime.parse("20251124180000 +0000")
        val minusFive = XmltvTime.parse("20251124180000 -0500")
        assertNotNull(utc)
        assertNotNull(minusFive)
        assertEquals(5 * 60 * 60 * 1000L, minusFive!! - utc!!)

        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        assertEquals("2025-11-24 18:00", formatter.format(utc))
        assertEquals("2025-11-24 23:00", formatter.format(minusFive))
    }

    @Test
    fun `xmltv time rejects garbage`() {
        assertNull(XmltvTime.parse("not-a-time"))
        assertNull(XmltvTime.parse(""))
        assertNull(XmltvTime.parse(null))
    }

    @Test
    fun `xmltv parser reads programmes and filters unknown channels`() {
        val xml = TestFixtures.xmltv(
            programmes = listOf(
                Triple("101.eplustv", "20251124180000 +0000", "20251124210000 +0000"),
                Triple("999.eplustv", "20251124180000 +0000", "20251124190000 +0000"),
            ),
            channelIds = listOf("101.eplustv", "999.eplustv"),
        )

        val all = XmltvParser.parse(xml, "src")
        assertEquals(2, all.size)
        assertEquals("101.eplustv", all[0].channelId)
        assertEquals("Sports", all[0].category)
        assertEquals("Test event", all[0].description)

        val filtered = XmltvParser.parse(xml, "src", allowedChannelIds = setOf("101.eplustv"))
        assertEquals(1, filtered.size)
        assertEquals("101.eplustv", filtered.single().channelId)
    }

    @Test
    fun `xmltv parser rejects a malformed document`() {
        try {
            XmltvParser.parse("<tv><programme", "src")
            throw AssertionError("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // expected: callers turn this into a per-source error
        }
    }
}
