package com.gameday.tv.model

import kotlinx.serialization.Serializable

@Serializable
data class Channel(
    val id: String,
    val number: Int,
    val name: String,
    val group: String = "",
    val logoUrl: String = "",
    val streamUrl: String,
    val headers: Map<String, String> = emptyMap(),
    val sourceId: String = "",
)

@Serializable
data class Program(
    val channelId: String,
    val title: String,
    val description: String = "",
    val startMs: Long,
    val stopMs: Long,
    val category: String = "",
    val iconUrl: String = "",
) {
    val durationMs: Long get() = (stopMs - startMs).coerceAtLeast(0)
}

@Serializable
data class GuideSnapshot(
    val channels: List<Channel> = emptyList(),
    val programs: List<Program> = emptyList(),
    val fetchedAtMs: Long = 0L,
    val schemaVersion: Int = CURRENT_SCHEMA,
) {
    val isEmpty: Boolean get() = channels.isEmpty()

    companion object {
        const val CURRENT_SCHEMA = 1
        val EMPTY = GuideSnapshot()
    }
}

@Serializable
enum class SourceKind {
    /** Plain M3U playlist of HLS channels. */
    M3U,

    /** An EPlusTV instance on the LAN; guide comes from /channels.m3u and /xmltv.xml. */
    EPLUSTV,

    /** One authorized HLS manifest entered by hand. */
    DIRECT,
}

@Serializable
data class SourceConfig(
    val id: String,
    val name: String,
    val kind: SourceKind,
    val url: String,
    val xmltvUrl: String = "",
    val enabled: Boolean = true,
    val userAgent: String = "",
    val hasHeaders: Boolean = false,
) {
    fun guideUrl(): String = when (kind) {
        SourceKind.EPLUSTV -> joinUrl(url, "/channels.m3u")
        else -> url
    }

    fun epgUrl(): String = when (kind) {
        SourceKind.EPLUSTV -> if (xmltvUrl.isBlank()) joinUrl(url, "/xmltv.xml") else xmltvUrl
        else -> xmltvUrl
    }

    private fun joinUrl(base: String, path: String): String {
        val trimmed = base.trimEnd('/')
        return if (trimmed.endsWith(path)) trimmed else trimmed + path
    }
}

/** A channel with its currently-airing and next program resolved. */
data class ChannelNow(
    val channel: Channel,
    val now: Program?,
    val next: Program?,
)

data class SearchHit(
    val channel: Channel,
    val program: Program?,
    val matchedOn: String,
)
