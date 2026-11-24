package com.gameday.tv.providers

import com.gameday.tv.core.Diag
import com.gameday.tv.core.JsonCodec
import com.gameday.tv.core.KeyValueStore
import com.gameday.tv.model.SourceConfig
import com.gameday.tv.storage.SecretStore
import java.util.UUID
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

interface SourceStore {
    fun list(): List<SourceConfig>
    fun get(id: String): SourceConfig?
    fun save(source: SourceConfig)
    fun delete(id: String)
    fun headersFor(source: SourceConfig): Map<String, String>
    fun saveHeaders(sourceId: String, headers: Map<String, String>)
    fun newId(): String = UUID.randomUUID().toString()
}

/**
 * Sources are user-owned authorized endpoints (an M3U of streams they subscribe to, a single
 * HLS manifest, or an EPlusTV gateway on their own LAN). Per-source HTTP headers, which may
 * contain a bearer token, are stored through [SecretStore] rather than in plain preferences.
 */
class JsonSourceStore(
    private val store: KeyValueStore,
    private val secrets: SecretStore,
) : SourceStore {

    override fun list(): List<SourceConfig> {
        val raw = store.getString(KEY_SOURCES, null) ?: return emptyList()
        return try {
            JsonCodec.json.decodeFromString<List<SourceConfig>>(raw)
        } catch (e: Exception) {
            Diag.warn("SourceStore", "Ignoring unreadable source list: ${e.javaClass.simpleName}")
            emptyList()
        }
    }

    override fun get(id: String): SourceConfig? = list().firstOrNull { it.id == id }

    override fun save(source: SourceConfig) {
        val sources = list().toMutableList()
        val index = sources.indexOfFirst { it.id == source.id }
        val prepared = source.copy(hasHeaders = headersFor(source).isNotEmpty())
        if (index >= 0) sources[index] = prepared else sources += prepared
        store.putString(KEY_SOURCES, JsonCodec.json.encodeToString(sources))
    }

    override fun delete(id: String) {
        val sources = list().filterNot { it.id == id }
        store.putString(KEY_SOURCES, JsonCodec.json.encodeToString(sources))
        secrets.remove(headerKey(id))
    }

    override fun headersFor(source: SourceConfig): Map<String, String> {
        val raw = secrets.get(headerKey(source.id)) ?: return emptyMap()
        return try {
            JsonCodec.json.decodeFromString<Map<String, String>>(raw)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    override fun saveHeaders(sourceId: String, headers: Map<String, String>) {
        val cleaned = headers.filter { (key, value) -> key.isNotBlank() && value.isNotBlank() }
        if (cleaned.isEmpty()) {
            secrets.remove(headerKey(sourceId))
        } else {
            secrets.put(headerKey(sourceId), JsonCodec.json.encodeToString(cleaned))
        }

        // Keep the on-screen "custom headers" hint in sync with the encrypted store.
        val sources = list().toMutableList()
        val index = sources.indexOfFirst { it.id == sourceId }
        if (index >= 0) {
            sources[index] = sources[index].copy(hasHeaders = cleaned.isNotEmpty())
            store.putString(KEY_SOURCES, JsonCodec.json.encodeToString(sources))
        }
    }

    private fun headerKey(sourceId: String) = "headers.$sourceId"

    companion object {
        private const val KEY_SOURCES = "sources.v1"
    }
}
