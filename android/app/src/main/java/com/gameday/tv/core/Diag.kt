package com.gameday.tv.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sanitized, in-memory diagnostic log.
 *
 * Nothing here is written to disk or uploaded. Every message is scrubbed of credentials,
 * bearer tokens, JWTs, cookies and URL user-info before it is retained, so a diagnostic
 * screen or a bug report can never leak account material.
 */
object Diag {

    private const val MAX_ENTRIES = 200
    private val entries = ArrayDeque<String>()
    private val lock = Any()
    private val clock = SimpleDateFormat("HH:mm:ss", Locale.US)

    private val keyValuePattern = Regex(
        "(?i)\\b(token|access_token|refresh_token|id_token|authorization|auth|password|passwd|secret|api_key|apikey|sig|signature|username|user)=([^&\\s\"',]+)",
    )
    private val bearerPattern = Regex("(?i)(bearer\\s+)[A-Za-z0-9\\-._~+/]+=*")
    private val jwtPattern = Regex("\\b[A-Za-z0-9_-]{12,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\b")
    private val userInfoPattern = Regex("(?i)(//)[^/@\\s:]+(?::[^/@\\s]*)?@")
    private val cookiePattern = Regex("(?i)\\b(set-cookie|cookie)\\s*:\\s*[^\\r\\n]+")
    private val longSecretPattern = Regex("\\b[A-Za-z0-9+/]{40,}={0,2}\\b")

    fun sanitize(raw: String): String {
        if (raw.isEmpty()) return raw
        var value = raw
        value = cookiePattern.replace(value, "$1: <redacted>")
        value = keyValuePattern.replace(value, "$1=<redacted>")
        value = bearerPattern.replace(value, "$1<redacted>")
        value = jwtPattern.replace(value, "<redacted-jwt>")
        value = userInfoPattern.replace(value, "$1<redacted>@")
        value = longSecretPattern.replace(value, "<redacted>")
        return value
    }

    fun record(level: String, tag: String, message: String) {
        val line = "${clock.format(Date())} ${level.uppercase(Locale.US)}/$tag: ${sanitize(message)}"
        synchronized(lock) {
            entries.addLast(line)
            while (entries.size > MAX_ENTRIES) entries.removeFirst()
        }
    }

    fun info(tag: String, message: String) = record("I", tag, message)

    fun warn(tag: String, message: String) = record("W", tag, message)

    fun error(tag: String, message: String) = record("E", tag, message)

    fun snapshot(): List<String> = synchronized(lock) { entries.toList() }

    fun clear() = synchronized(lock) { entries.clear() }
}
