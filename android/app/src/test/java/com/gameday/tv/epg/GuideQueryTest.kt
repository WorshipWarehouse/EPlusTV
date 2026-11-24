package com.gameday.tv.epg

import com.gameday.tv.TestFixtures
import com.gameday.tv.model.GuideSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideQueryTest {

    private val base = 1_700_000_000_000L
    private val channelOne = TestFixtures.channel("c1", 1, "NFL Network")
    private val channelTwo = TestFixtures.channel("c2", 2, "RedZone")
    private val channels = listOf(channelOne, channelTwo)

    private val programs = listOf(
        TestFixtures.program("c1", "Bills at Texans", base - 3_600_000, base + 3_600_000),
        TestFixtures.program("c1", "Post game", base + 3_600_000, base + 5_400_000),
        TestFixtures.program("c2", "RedZone live", base - 1_800_000, base + 1_800_000),
    )

    @Test
    fun `nowOn resolves the programme covering the current instant`() {
        val byChannel = GuideQuery.programsByChannel(programs)
        val now = GuideQuery.nowOn(channels, byChannel, base)

        assertEquals(2, now.size)
        assertEquals("Bills at Texans", now[0].now?.title)
        assertEquals("Post game", now[0].next?.title)
        assertEquals("RedZone live", now[1].now?.title)
    }

    @Test
    fun `nowOn returns null current when nothing is airing`() {
        val byChannel = GuideQuery.programsByChannel(programs)
        val now = GuideQuery.nowOn(channels, byChannel, base + 20 * 3_600_000L)
        assertTrue(now.all { it.now == null })
    }

    @Test
    fun `upcoming is time ordered and respects the horizon`() {
        val byChannel = GuideQuery.programsByChannel(programs)
        val upcoming = GuideQuery.upcoming(channels, byChannel, base, 2 * 3_600_000L, 10)
        assertEquals(listOf("Post game"), upcoming.map { it.second.title })

        val narrow = GuideQuery.upcoming(channels, byChannel, base, 1_000L, 10)
        assertTrue(narrow.isEmpty())
    }

    @Test
    fun `search matches programme titles and channel names`() {
        val guide = GuideSnapshot(channels = channels, programs = programs, fetchedAtMs = base)

        val titleHits = GuideQuery.search(guide, "redzone")
        assertEquals(2, titleHits.size)
        assertTrue(titleHits.any { it.matchedOn == "channel" })
        assertTrue(titleHits.any { it.matchedOn == "title" })

        val empty = GuideQuery.search(guide, "   ")
        assertTrue(empty.isEmpty())
    }

    @Test
    fun `favoritesOnly filters by id`() {
        val filtered = GuideQuery.favoritesOnly(channels, setOf("c2"))
        assertEquals(listOf(channelTwo), filtered)
    }

    @Test
    fun `pruner drops stale programmes and unknown channels`() {
        val stale = TestFixtures.program("c1", "Old", base - 10 * 3_600_000L, base - 9 * 3_600_000L)
        val orphan = TestFixtures.program("unknown", "Orphan", base, base + 3_600_000L)
        val current = TestFixtures.program("c1", "Current", base, base + 3_600_000L)

        val pruned = GuidePruner.prune(channels, programs + stale + orphan + current, base)

        assertEquals(2, pruned.channels.size)
        assertTrue(pruned.programs.none { it.title == "Old" })
        assertTrue(pruned.programs.none { it.title == "Orphan" })
        assertTrue(pruned.programs.any { it.title == "Current" })
    }

    @Test
    fun `pruner caps programme count`() {
        val many = (0 until 500).map {
            TestFixtures.program("c1", "Event $it", base + it * 60_000L, base + (it + 30) * 60_000L)
        }
        val pruned = GuidePruner.prune(channels, many, base, maxPrograms = 100)
        assertEquals(100, pruned.programs.size)
    }
}
