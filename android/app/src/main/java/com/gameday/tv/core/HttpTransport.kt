package com.gameday.tv.core

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Blocking text HTTP used for guide, playlist and update manifests. Playback does not go
 * through this class; Media3 has its own data source so video is never double-buffered.
 */
interface HttpTransport {
    @Throws(IOException::class)
    fun getText(url: String, headers: Map<String, String> = emptyMap()): String
}

class UrlConnectionTransport(
    private val userAgent: String = DEFAULT_USER_AGENT,
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
    private val maxBytes: Int = 32 * 1024 * 1024,
) : HttpTransport {

    override fun getText(url: String, headers: Map<String, String>): String {
        require(url.startsWith("http://") || url.startsWith("https://")) {
            "Only http and https URLs are supported"
        }
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept-Encoding", "identity")
                headers.forEach { (key, value) ->
                    if (key.isNotBlank() && value.isNotBlank()) setRequestProperty(key, value)
                }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { readLimited(it, maxBytes) }.orEmpty()
            if (code !in 200..299) {
                throw IOException("HTTP $code from ${hostOf(url)}")
            }
            if (body.isBlank()) {
                throw IOException("Empty response from ${hostOf(url)}")
            }
            return body
        } finally {
            connection?.disconnect()
        }
    }

    private fun readLimited(stream: InputStream, limit: Int): String {
        val buffer = ByteArray(16 * 1024)
        val out = java.io.ByteArrayOutputStream(minOf(limit, 64 * 1024))
        var total = 0
        while (true) {
            val read = stream.read(buffer)
            if (read <= 0) break
            total += read
            if (total > limit) throw IOException("Response exceeded ${limit / (1024 * 1024)} MiB limit")
            out.write(buffer, 0, read)
        }
        return out.toString("UTF-8")
    }

    private fun hostOf(url: String): String = try {
        URL(url).host ?: "host"
    } catch (e: Exception) {
        "host"
    }

    companion object {
        /** A desktop browser UA is required by several TV provider CDNs. */
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 9; AFTMM Build/PS7233) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
