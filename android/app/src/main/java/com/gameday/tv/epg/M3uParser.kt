package com.gameday.tv.epg

import com.gameday.tv.model.Channel

/**
 * Tolerant M3U/M3U8 playlist parser for channel lists.
 *
 * Handles the attributes EPlusTV and common playlist exporters emit, including
 * `#EXTGRP`, `#EXTVLCOPT` user-agent/referrer hints and the `url|Header=Value` suffix.
 */
object M3uParser {

    private val quotedAttr = Regex("([A-Za-z0-9_-]+)=\"([^\"]*)\"")
    private val bareAttr = Regex("([A-Za-z0-9_-]+)=([^\\s\"]+)")

    fun parse(
        text: String,
        sourceId: String,
        defaultHeaders: Map<String, String> = emptyMap(),
    ): List<Channel> {
        val channels = ArrayList<Channel>()
        var pending: Pending? = null
        var entryIndex = 0

        text.removePrefix("\uFEFF").lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTM3U", ignoreCase = true) -> Unit
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    entryIndex++
                    val comma = line.indexOf(',')
                    val attributePart = if (comma >= 0) line.substring(0, comma) else line
                    val displayName = if (comma >= 0) line.substring(comma + 1).trim() else ""
                    pending = Pending(displayName, parseAttributes(attributePart))
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                    pending?.group = line.substringAfter(':').trim()
                }
                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val option = line.substringAfter(':').trim()
                    val target = pending ?: return@forEach
                    when {
                        option.startsWith("http-user-agent=", true) ->
                            target.headers["User-Agent"] = option.substringAfter('=').trim()
                        option.startsWith("http-referrer=", true) ||
                            option.startsWith("http-referer=", true) ->
                            target.headers["Referer"] = option.substringAfter('=').trim()
                        option.startsWith("http-origin=", true) ->
                            target.headers["Origin"] = option.substringAfter('=').trim()
                    }
                }
                line.startsWith("#") -> Unit
                else -> {
                    val entry = pending ?: return@forEach
                    val (url, inlineHeaders) = splitInlineHeaders(line)
                    if (url.isBlank()) return@forEach

                    val headers = LinkedHashMap<String, String>()
                    headers.putAll(defaultHeaders)
                    headers.putAll(entry.headers)
                    headers.putAll(inlineHeaders)

                    val number = entry.attrs["tvg-chno"]?.toIntOrNull()
                        ?: entry.attrs["channel-number"]?.toIntOrNull()
                        ?: entry.attrs["tvg-id"]?.substringBefore('.')?.toIntOrNull()
                        ?: (channels.size + 1)

                    val id = entry.attrs["tvg-id"]?.takeIf { it.isNotBlank() }
                        ?: "$sourceId-$number"

                    val name = entry.title.ifBlank {
                        entry.attrs["tvg-name"]?.takeIf { it.isNotBlank() } ?: "Channel $number"
                    }

                    channels += Channel(
                        id = id,
                        number = number,
                        name = name,
                        group = entry.group ?: entry.attrs["group-title"].orEmpty(),
                        logoUrl = entry.attrs["tvg-logo"].orEmpty(),
                        streamUrl = url,
                        headers = headers,
                        sourceId = sourceId,
                    )
                    pending = null
                }
            }
        }

        return channels
    }

    private fun parseAttributes(source: String): Map<String, String> {
        val attributes = LinkedHashMap<String, String>()
        quotedAttr.findAll(source).forEach { match ->
            attributes[match.groupValues[1].lowercase()] = match.groupValues[2]
        }
        bareAttr.findAll(source).forEach { match ->
            val key = match.groupValues[1].lowercase()
            if (!attributes.containsKey(key)) attributes[key] = match.groupValues[2]
        }
        return attributes
    }

    private fun splitInlineHeaders(line: String): Pair<String, Map<String, String>> {
        val separator = line.indexOf('|')
        if (separator < 0) return line to emptyMap()
        val url = line.substring(0, separator).trim()
        val headerPart = line.substring(separator + 1)
        val headers = LinkedHashMap<String, String>()
        headerPart.split('&').forEach { pair ->
            val eq = pair.indexOf('=')
            if (eq > 0) {
                val key = pair.substring(0, eq).trim()
                val value = pair.substring(eq + 1).trim()
                if (key.isNotEmpty() && value.isNotEmpty()) headers[key] = value
            }
        }
        return url to headers
    }

    private class Pending(val title: String, val attrs: Map<String, String>) {
        var group: String? = null
        val headers = LinkedHashMap<String, String>()
    }
}
