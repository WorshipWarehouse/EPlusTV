package com.gameday.tv.update

import com.gameday.tv.core.HttpTransport
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

interface ApkDownloader {
    @Throws(IOException::class)
    fun download(url: String, destination: File, headers: Map<String, String> = emptyMap(), onProgress: (Int) -> Unit = {})
}

/** Streams straight to disk so a large APK never sits in RAM on a 1.5 GB device. */
class StreamingApkDownloader(
    private val userAgent: String = HttpTransportDefaults.USER_AGENT,
    private val connectTimeoutMs: Int = 20_000,
    private val readTimeoutMs: Int = 60_000,
) : ApkDownloader {

    override fun download(
        url: String,
        destination: File,
        headers: Map<String, String>,
        onProgress: (Int) -> Unit,
    ) {
        require(url.startsWith("https://")) { "Update downloads must use HTTPS" }
        destination.parentFile?.mkdirs()
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("User-Agent", userAgent)
                headers.forEach { (key, value) -> setRequestProperty(key, value) }
            }
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code while downloading update")
            val length = connection.contentLengthLong
            connection.inputStream.use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    var lastPercent = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (length > 0) {
                            val percent = ((copied * 100) / length).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                    output.flush()
                }
            }
        } finally {
            connection?.disconnect()
        }
    }
}

object HttpTransportDefaults {
    const val USER_AGENT =
        "GameDayTV/1.0 (Android TV)"
}
