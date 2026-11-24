package com.gameday.tv.core

import com.gameday.tv.epg.GuideCache
import com.gameday.tv.model.GuideSnapshot
import com.gameday.tv.model.Program
import com.gameday.tv.TestFixtures
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagTest {

    @Test
    fun `sanitizes query credentials`() {
        val sanitized = Diag.sanitize("https://cdn.test/live.m3u8?token=abc123&user=bob&x=1")
        assertFalse(sanitized.contains("abc123"))
        assertFalse(sanitized.contains("bob"))
        assertTrue(sanitized.contains("x=1"))
        assertTrue(sanitized.contains("<redacted>"))
    }

    @Test
    fun `sanitizes bearer tokens and jwts`() {
        val bearer = Diag.sanitize("Authorization: Bearer eyJhbGciOiJIUzI1NiJ9")
        assertFalse(bearer.contains("eyJhbGciOiJIUzI1NiJ9"))

        val jwt = Diag.sanitize("token eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U")
        assertFalse(jwt.contains("eyJhbGciOiJIUzI1NiJ9"))
        assertTrue(jwt.contains("<redacted"))
    }

    @Test
    fun `sanitizes url user info and cookies`() {
        val userInfo = Diag.sanitize("https://alice:hunter2@cdn.test/stream")
        assertFalse(userInfo.contains("hunter2"))
        assertTrue(userInfo.contains("<redacted>@"))

        val cookie = Diag.sanitize("Cookie: session=abcdef123456")
        assertFalse(cookie.contains("abcdef123456"))
    }

    @Test
    fun `ring buffer is bounded and clearable`() {
        Diag.clear()
        repeat(250) { Diag.info("Test", "line $it") }
        assertEquals(200, Diag.snapshot().size)
        Diag.clear()
        assertTrue(Diag.snapshot().isEmpty())
    }
}

class FmtTest {

    private val base = 1_700_000_000_000L

    @Test
    fun `relative labels`() {
        assertEquals("just now", Fmt.ago(base, base + 20_000))
        assertEquals("5m ago", Fmt.ago(base, base + 5 * 60_000))
        assertEquals("2h ago", Fmt.ago(base, base + 2 * 3_600_000))
        assertEquals("never", Fmt.ago(0))
    }

    @Test
    fun `progress is clamped`() {
        assertEquals(50, Fmt.progressPercent(base - 30 * 60_000, base + 30 * 60_000, base))
        assertEquals(0, Fmt.progressPercent(base, base + 60_000, base - 60_000))
        assertEquals(100, Fmt.progressPercent(base, base + 60_000, base + 120_000))
        assertEquals(0, Fmt.progressPercent(base, base, base))
    }

    @Test
    fun `range covers both ends`() {
        val label = Fmt.range(base, base + 3_600_000)
        assertTrue(label.contains("-"))
    }

    @Test
    fun `day label distinguishes today and tomorrow`() {
        assertEquals("Today", Fmt.dayLabel(base, base))
        assertEquals("Tomorrow", Fmt.dayLabel(base + 86_400_000L, base))
    }

    @Test
    fun `duration label`() {
        assertEquals("45m", Fmt.durationLabel(45))
        assertEquals("2h", Fmt.durationLabel(120))
        assertEquals("1h 30m", Fmt.durationLabel(90))
        assertEquals("0m", Fmt.durationLabel(-5))
    }
}

class GuideCacheTest {

    private fun tempFile(): File =
        File(Files.createTempDirectory("gameday-cache").toFile(), "guide.json")

    @Test
    fun `round trips a snapshot`() {
        val cache = GuideCache(tempFile())
        val snapshot = GuideSnapshot(
            channels = listOf(TestFixtures.channel("c1", 1)),
            programs = listOf(
                Program(channelId = "c1", title = "Game", startMs = 1L, stopMs = 2L),
            ),
            fetchedAtMs = 99L,
        )

        assertTrue(cache.write(snapshot))
        val read = cache.read()
        assertNotNull(read)
        assertEquals(1, read!!.channels.size)
        assertEquals("Game", read.programs.single().title)
        assertEquals(99L, read.fetchedAtMs)
        assertTrue(cache.sizeBytes() > 0)
    }

    @Test
    fun `corrupt cache is discarded instead of crashing`() {
        val file = tempFile()
        file.parentFile?.mkdirs()
        file.writeText("{ truncated")
        val cache = GuideCache(file)

        assertNull(cache.read())
    }

    @Test
    fun `empty cache reports nothing`() {
        val cache = GuideCache(tempFile())
        assertNull(cache.read())
        assertEquals(0L, cache.sizeBytes())
    }

    @Test
    fun `clear removes the cache file`() {
        val cache = GuideCache(tempFile())
        cache.write(GuideSnapshot(channels = listOf(TestFixtures.channel("c1", 1))))
        cache.clear()
        assertNull(cache.read())
    }
}
