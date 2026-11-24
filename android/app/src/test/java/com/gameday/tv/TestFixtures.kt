package com.gameday.tv

import com.gameday.tv.core.HttpTransport
import com.gameday.tv.model.Channel
import com.gameday.tv.model.Program
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object TestFixtures {

    fun channel(id: String, number: Int, name: String = "Channel $number"): Channel = Channel(
        id = id,
        number = number,
        name = name,
        streamUrl = "https://example.test/$id.m3u8",
        sourceId = "source",
    )

    fun program(
        channelId: String,
        title: String,
        startMs: Long,
        stopMs: Long,
        description: String = "",
    ): Program = Program(
        channelId = channelId,
        title = title,
        description = description,
        startMs = startMs,
        stopMs = stopMs,
    )

    fun playlist(vararg entries: Triple<String, String, String>): String = buildString {
        appendLine("#EXTM3U")
        entries.forEach { (attributes, name, url) ->
            appendLine("#EXTINF:-1 $attributes,$name")
            appendLine(url)
        }
    }

    fun xmltvTime(epochMs: Long): String {
        val formatter = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return formatter.format(Date(epochMs)) + " +0000"
    }

    fun xmltv(
        programmes: List<Triple<String, String, String>>,
        channelIds: List<String> = emptyList(),
    ): String = buildString {
        appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        appendLine("<tv generator-info-name=\"eplustv\">")
        channelIds.forEach { id ->
            appendLine("  <channel id=\"$id\"><display-name>$id</display-name></channel>")
        }
        programmes.forEach { (channelId, start, stop) ->
            appendLine("  <programme start=\"$start\" stop=\"$stop\" channel=\"$channelId\">")
            appendLine("    <title lang=\"en\">$channelId event</title>")
            appendLine("    <desc lang=\"en\">Test event</desc>")
            appendLine("    <category lang=\"en\">Sports</category>")
            appendLine("  </programme>")
        }
        appendLine("</tv>")
    }
}

class FakeTransport(
    private val responses: MutableMap<String, String> = mutableMapOf(),
    private val failures: MutableMap<String, Exception> = mutableMapOf(),
) : HttpTransport {

    var requestCount = 0
        private set
    var lastHeaders: Map<String, String> = emptyMap()
        private set

    fun respond(url: String, body: String) {
        responses[url] = body
    }

    fun fail(url: String, error: Exception = IOException("boom")) {
        failures[url] = error
    }

    override fun getText(url: String, headers: Map<String, String>): String {
        requestCount++
        lastHeaders = headers
        failures[url]?.let { throw it }
        return responses[url] ?: throw IOException("No fake response for $url")
    }
}
