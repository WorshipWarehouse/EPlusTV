package com.gameday.tv.storage

import com.gameday.tv.core.KeyValueStore

/**
 * Persistent user settings.
 *
 * Migration rules: never overwrite a value the user already has. Each schema step only seeds
 * keys that are absent, so upgrading the APK keeps sources, favorites and playback choices.
 */
class Settings(private val store: KeyValueStore) {

    data class MigrationResult(val fromVersion: Int, val toVersion: Int, val changed: Boolean)

    var refreshIntervalHours: Int
        get() = store.getLong(KEY_REFRESH_HOURS, DEFAULT_REFRESH_HOURS.toLong()).toInt().coerceIn(1, 48)
        set(value) = store.putLong(KEY_REFRESH_HOURS, value.toLong().coerceIn(1, 48))

    var autoRefresh: Boolean
        get() = store.getBoolean(KEY_AUTO_REFRESH, true)
        set(value) = store.putBoolean(KEY_AUTO_REFRESH, value)

    var updateManifestUrl: String
        get() = store.getString(KEY_UPDATE_URL, "").orEmpty()
        set(value) = store.putString(KEY_UPDATE_URL, value.trim())

    var autoCheckUpdates: Boolean
        get() = store.getBoolean(KEY_AUTO_CHECK_UPDATES, true)
        set(value) = store.putBoolean(KEY_AUTO_CHECK_UPDATES, value)

    var lastRefreshMs: Long
        get() = store.getLong(KEY_LAST_REFRESH, 0L)
        set(value) = store.putLong(KEY_LAST_REFRESH, value)

    var lastRefreshOk: Boolean
        get() = store.getBoolean(KEY_LAST_REFRESH_OK, false)
        set(value) = store.putBoolean(KEY_LAST_REFRESH_OK, value)

    var lastRefreshMessage: String
        get() = store.getString(KEY_LAST_REFRESH_MESSAGE, "").orEmpty()
        set(value) = store.putString(KEY_LAST_REFRESH_MESSAGE, value)

    /** BCP-47-ish audio preference, e.g. "eng", "spa"; blank means "leave ExoPlayer default". */
    var preferredAudioLanguage: String
        get() = store.getString(KEY_AUDIO_LANGUAGE, "").orEmpty()
        set(value) = store.putString(KEY_AUDIO_LANGUAGE, value.trim())

    var subtitlesEnabled: Boolean
        get() = store.getBoolean(KEY_SUBTITLES, false)
        set(value) = store.putBoolean(KEY_SUBTITLES, value)

    /** Hardware-accelerated decoding; disabled only if a device shows decoder faults. */
    var preferHardwareDecoding: Boolean
        get() = store.getBoolean(KEY_HARDWARE_DECODING, true)
        set(value) = store.putBoolean(KEY_HARDWARE_DECODING, value)

    /** Limits HLS variant selection to keep the 1st-gen Fire TV from over-buffering. */
    var dataSaver: Boolean
        get() = store.getBoolean(KEY_DATA_SAVER, false)
        set(value) = store.putBoolean(KEY_DATA_SAVER, value)

    var lastUpdateCheckMs: Long
        get() = store.getLong(KEY_LAST_UPDATE_CHECK, 0L)
        set(value) = store.putLong(KEY_LAST_UPDATE_CHECK, value)

    var dismissedUpdateVersionCode: Long
        get() = store.getLong(KEY_DISMISSED_UPDATE, 0L)
        set(value) = store.putLong(KEY_DISMISSED_UPDATE, value)

    fun migrate(): MigrationResult {
        val from = store.getLong(KEY_SCHEMA, 0L).toInt()
        if (from >= SCHEMA_VERSION) return MigrationResult(from, from, false)

        var working = from
        if (working < 1) {
            seedLongIfAbsent(KEY_REFRESH_HOURS, DEFAULT_REFRESH_HOURS.toLong())
            seedBooleanIfAbsent(KEY_AUTO_REFRESH, true)
            seedStringIfAbsent(KEY_UPDATE_URL, "")
            seedBooleanIfAbsent(KEY_AUTO_CHECK_UPDATES, true)
            seedBooleanIfAbsent(KEY_SUBTITLES, false)
            seedBooleanIfAbsent(KEY_HARDWARE_DECODING, true)
            seedStringIfAbsent(KEY_AUDIO_LANGUAGE, "")
            working = 1
        }
        if (working < 2) {
            // v2 added signed-update handling and data saver; existing values are preserved.
            seedBooleanIfAbsent(KEY_DATA_SAVER, false)
            seedLongIfAbsent(KEY_LAST_UPDATE_CHECK, 0L)
            seedLongIfAbsent(KEY_DISMISSED_UPDATE, 0L)
            seedLongIfAbsent(KEY_LAST_REFRESH, 0L)
            seedStringIfAbsent(KEY_LAST_REFRESH_MESSAGE, "")
            working = 2
        }

        store.putLong(KEY_SCHEMA, SCHEMA_VERSION.toLong())
        return MigrationResult(from, SCHEMA_VERSION, true)
    }

    private fun seedLongIfAbsent(key: String, value: Long) {
        if (store.keys().contains(key).not()) store.putLong(key, value)
    }

    private fun seedBooleanIfAbsent(key: String, value: Boolean) {
        if (store.keys().contains(key).not()) store.putBoolean(key, value)
    }

    private fun seedStringIfAbsent(key: String, value: String) {
        if (store.keys().contains(key).not()) store.putString(key, value)
    }

    companion object {
        const val SCHEMA_VERSION = 2
        const val DEFAULT_REFRESH_HOURS = 6

        private const val KEY_SCHEMA = "settings.schema"
        private const val KEY_REFRESH_HOURS = "settings.refreshHours"
        private const val KEY_AUTO_REFRESH = "settings.autoRefresh"
        private const val KEY_UPDATE_URL = "settings.updateManifestUrl"
        private const val KEY_AUTO_CHECK_UPDATES = "settings.autoCheckUpdates"
        private const val KEY_LAST_REFRESH = "settings.lastRefreshMs"
        private const val KEY_LAST_REFRESH_OK = "settings.lastRefreshOk"
        private const val KEY_LAST_REFRESH_MESSAGE = "settings.lastRefreshMessage"
        private const val KEY_AUDIO_LANGUAGE = "settings.audioLanguage"
        private const val KEY_SUBTITLES = "settings.subtitles"
        private const val KEY_HARDWARE_DECODING = "settings.hardwareDecoding"
        private const val KEY_DATA_SAVER = "settings.dataSaver"
        private const val KEY_LAST_UPDATE_CHECK = "settings.lastUpdateCheckMs"
        private const val KEY_DISMISSED_UPDATE = "settings.dismissedUpdateVersionCode"
    }
}
