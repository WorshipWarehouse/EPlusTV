package com.gameday.tv.providers

/**
 * HTTP headers are entered on a TV remote as `Name=Value;Name=Value` (or comma separated)
 * and stored encrypted; this keeps the on-screen entry short while still supporting
 * provider tokens that must accompany manifest and segment requests.
 */
object HeaderParser {

    fun parse(spec: String): Map<String, String> {
        val headers = LinkedHashMap<String, String>()
        spec.split(';', '\n', ',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { pair ->
                val separator = pair.indexOfFirst { it == '=' || it == ':' }
                if (separator <= 0) return@forEach
                val name = pair.substring(0, separator).trim()
                val value = pair.substring(separator + 1).trim()
                if (name.isNotEmpty() && value.isNotEmpty()) headers[name] = value
            }
        return headers
    }

    fun format(headers: Map<String, String>): String =
        headers.entries.joinToString("; ") { (name, value) -> "$name=$value" }
}
