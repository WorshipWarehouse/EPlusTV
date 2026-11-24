package com.gameday.tv.core

import android.content.SharedPreferences

/** Small typed key/value abstraction so settings logic is testable off-device. */
interface KeyValueStore {
    fun getString(key: String, default: String? = null): String?
    fun putString(key: String, value: String)
    fun getLong(key: String, default: Long): Long
    fun putLong(key: String, value: Long)
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun remove(key: String)
    fun keys(): Set<String>

    fun getStringSet(key: String): Set<String> = emptySet()
    fun putStringSet(key: String, value: Set<String>) = Unit
}

class PrefsKeyValueStore(private val prefs: SharedPreferences) : KeyValueStore {

    override fun getString(key: String, default: String?): String? =
        (prefs.all[key] as? String) ?: default

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun getLong(key: String, default: Long): Long =
        (prefs.all[key] as? Long) ?: default

    override fun putLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }

    override fun getBoolean(key: String, default: Boolean): Boolean =
        (prefs.all[key] as? Boolean) ?: default

    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    override fun keys(): Set<String> = prefs.all.keys.toSet()

    override fun getStringSet(key: String): Set<String> =
        (prefs.all[key] as? Set<*>)?.mapNotNull { it as? String }?.toSet() ?: emptySet()

    override fun putStringSet(key: String, value: Set<String>) {
        prefs.edit().putStringSet(key, value).apply()
    }
}

class MemoryKeyValueStore(initial: Map<String, Any?> = emptyMap()) : KeyValueStore {
    private val values = LinkedHashMap<String, Any?>()

    init {
        values.putAll(initial)
    }

    override fun getString(key: String, default: String?): String? = values[key] as? String ?: default

    override fun putString(key: String, value: String) {
        values[key] = value
    }

    override fun getLong(key: String, default: Long): Long = values[key] as? Long ?: default

    override fun putLong(key: String, value: Long) {
        values[key] = value
    }

    override fun getBoolean(key: String, default: Boolean): Boolean = values[key] as? Boolean ?: default

    override fun putBoolean(key: String, value: Boolean) {
        values[key] = value
    }

    override fun remove(key: String) {
        values.remove(key)
    }

    override fun keys(): Set<String> = values.keys.toSet()

    override fun getStringSet(key: String): Set<String> =
        (values[key] as? Set<*>)?.mapNotNull { it as? String }?.toSet() ?: emptySet()

    override fun putStringSet(key: String, value: Set<String>) {
        values[key] = value
    }
}
