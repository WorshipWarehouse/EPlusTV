package com.gameday.tv.epg

import com.gameday.tv.core.Diag
import com.gameday.tv.core.HttpTransport
import com.gameday.tv.model.Channel
import com.gameday.tv.model.GuideSnapshot
import com.gameday.tv.model.Program
import com.gameday.tv.model.SourceConfig
import com.gameday.tv.model.SourceKind
import com.gameday.tv.providers.SourceStore
import java.io.IOException

/**
 * Fetches every enabled source, merges channels and programmes, prunes to a bounded size and
 * writes the cache. A failed refresh never destroys the last good guide.
 */
class GuideRepository(
    private val transport: HttpTransport,
    private val sourceStore: SourceStore,
    private val cache: GuideCache,
) {

    data class RefreshResult(
        val ok: Boolean,
        val channelCount: Int,
        val programCount: Int,
        val errors: List<String>,
        val durationMs: Long,
        val message: String,
    )

    fun cached(): GuideSnapshot = cache.read() ?: GuideSnapshot.EMPTY

    fun refresh(nowMs: Long = System.currentTimeMillis()): RefreshResult {
        val startedAt = System.currentTimeMillis()
        val sources = sourceStore.list().filter { it.enabled && it.url.isNotBlank() }
        if (sources.isEmpty()) {
            return RefreshResult(false, 0, 0, listOf("No sources configured"), 0L, "No sources configured")
        }

        val channelsById = LinkedHashMap<String, Channel>()
        val programs = ArrayList<Program>()
        val errors = ArrayList<String>()

        sources.forEach { source ->
            try {
                collectSource(source, channelsById, programs)
            } catch (e: IOException) {
                val reason = "${source.name}: ${Diag.sanitize(e.message ?: "network error")}"
                errors += reason
                Diag.warn("GuideRepository", reason)
            } catch (e: Exception) {
                val reason = "${source.name}: ${Diag.sanitize(e.message ?: e.javaClass.simpleName)}"
                errors += reason
                Diag.warn("GuideRepository", reason)
            }
        }

        if (channelsById.isEmpty()) {
            val message = if (errors.isEmpty()) "No channels found in the configured sources" else errors.first()
            return RefreshResult(
                ok = false,
                channelCount = 0,
                programCount = 0,
                errors = errors,
                durationMs = System.currentTimeMillis() - startedAt,
                message = message,
            )
        }

        val pruned = GuidePruner.prune(channelsById.values.toList(), programs, nowMs)
        val snapshot = GuideSnapshot(
            channels = pruned.channels,
            programs = pruned.programs,
            fetchedAtMs = System.currentTimeMillis(),
        )
        cache.write(snapshot)

        val duration = System.currentTimeMillis() - startedAt
        val message = buildString {
            append("${snapshot.channels.size} channels, ${snapshot.programs.size} programs")
            append(" in ${duration} ms")
            if (errors.isNotEmpty()) append(" (${errors.size} source error(s))")
        }
        Diag.info("GuideRepository", "Guide refreshed: $message")

        return RefreshResult(
            ok = true,
            channelCount = snapshot.channels.size,
            programCount = snapshot.programs.size,
            errors = errors,
            durationMs = duration,
            message = message,
        )
    }

    private fun collectSource(
        source: SourceConfig,
        channelsById: MutableMap<String, Channel>,
        programs: MutableList<Program>,
    ) {
        val headers = sourceStore.headersFor(source).toMutableMap()
        if (source.userAgent.isNotBlank()) headers["User-Agent"] = source.userAgent

        if (source.kind == SourceKind.DIRECT) {
            val channel = Channel(
                id = source.id,
                number = channelsById.size + 1,
                name = source.name,
                group = "Direct",
                streamUrl = source.url,
                headers = headers,
                sourceId = source.id,
            )
            channelsById[channel.id] = channel
            return
        }

        val playlist = transport.getText(source.guideUrl(), headers)
        val parsed = M3uParser.parse(playlist, source.id, headers)
        if (parsed.isEmpty()) throw IOException("playlist contained no channels")
        parsed.forEach { channel -> channelsById[channel.id] = channel }

        val epgUrl = source.epgUrl()
        if (epgUrl.isBlank()) return
        val channelIds = parsed.mapTo(HashSet()) { it.id }
        val xml = transport.getText(epgUrl, headers)
        programs += XmltvParser.parse(xml, source.id, channelIds)
    }
}
