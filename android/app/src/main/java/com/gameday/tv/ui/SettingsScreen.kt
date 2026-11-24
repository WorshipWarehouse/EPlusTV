package com.gameday.tv.ui

import com.gameday.tv.storage.Settings

class SettingsScreen(host: MainActivity) : ListScreen(host, "Settings") {

    override val label: String = LABEL

    override fun render() {
        val container = host.container
        val settings = container.settings
        val items = ArrayList<TvItem>()

        subtitleView.text = "Settings stay on this device and survive updates"

        items += TvItem.Header("Guide")
        items += toggle(
            key = "auto-refresh",
            title = "Automatic refresh",
            subtitle = "Refresh the guide in the background",
            enabled = settings.autoRefresh,
        ) {
            settings.autoRefresh = !settings.autoRefresh
            container.rescheduleRefresh()
            render()
        }
        items += value(
            key = "refresh-interval",
            title = "Refresh interval",
            subtitle = "How often the guide refreshes automatically",
            value = "${settings.refreshIntervalHours}h",
        ) {
            val options = listOf(2, 4, 6, 12, 24)
            val next = options.firstOrNull { it > settings.refreshIntervalHours } ?: options.first()
            settings.refreshIntervalHours = next
            container.rescheduleRefresh()
            render()
        }
        items += value(
            key = "refresh-now",
            title = "Refresh guide now",
            subtitle = settings.lastRefreshMessage.ifBlank { "No refresh has run yet" },
            value = "",
        ) {
            host.refreshGuideNow()
        }

        items += TvItem.Header("Playback")
        items += toggle(
            key = "hardware",
            title = "Prefer hardware decoding",
            subtitle = "Turn off only if video fails on this device",
            enabled = settings.preferHardwareDecoding,
        ) {
            settings.preferHardwareDecoding = !settings.preferHardwareDecoding
            render()
        }
        items += toggle(
            key = "subtitles",
            title = "Subtitles by default",
            subtitle = "Select a text track when the stream provides one",
            enabled = settings.subtitlesEnabled,
        ) {
            settings.subtitlesEnabled = !settings.subtitlesEnabled
            render()
        }
        items += value(
            key = "audio-lang",
            title = "Preferred audio language",
            subtitle = "Three-letter code, for example eng or spa",
            value = settings.preferredAudioLanguage.ifBlank { "Device default" },
        ) {
            host.promptText(
                heading = "Preferred audio language",
                initial = settings.preferredAudioLanguage,
                hint = "Three-letter language code, or blank",
            ) { value ->
                settings.preferredAudioLanguage = value
                render()
            }
        }
        items += toggle(
            key = "data-saver",
            title = "Data saver",
            subtitle = "Cap video bitrate around 2.5 Mbps",
            enabled = settings.dataSaver,
        ) {
            settings.dataSaver = !settings.dataSaver
            render()
        }

        items += TvItem.Header("Updates")
        items += value(
            key = "update-url",
            title = "Update manifest URL",
            subtitle = "HTTPS address of the signed update manifest",
            value = settings.updateManifestUrl.ifBlank { "Not set" },
        ) {
            host.promptText(
                heading = "Update manifest URL",
                initial = settings.updateManifestUrl,
                hint = "Must be https://",
                allowUrlSymbols = true,
            ) { value ->
                if (value.isBlank() || value.startsWith("https://")) {
                    settings.updateManifestUrl = value
                    render()
                } else {
                    host.toast("Update manifests must use HTTPS")
                }
            }
        }
        items += toggle(
            key = "auto-update",
            title = "Automatic update checks",
            subtitle = "Look for a verified release when the app starts",
            enabled = settings.autoCheckUpdates,
        ) {
            settings.autoCheckUpdates = !settings.autoCheckUpdates
            render()
        }
        items += value(
            key = "open-updates",
            title = "Open Updates",
            subtitle = "Check for and install a signed release",
            value = "",
        ) {
            host.navigateTo(UpdatesScreen.LABEL)
        }

        items += TvItem.Header("About")
        items += value(
            key = "about",
            title = "GameDay television app",
            subtitle = "Native Kotlin client with embedded guide and Media3 playback",
            value = "v${com.gameday.tv.BuildConfig.VERSION_NAME}",
        ) { }

        setItems(items, "No settings available.")
    }

    private fun toggle(key: String, title: String, subtitle: String, enabled: Boolean, action: () -> Unit) =
        TvItem.Entry(
            key = key,
            title = title,
            subtitle = subtitle,
            meta = if (enabled) "On" else "Off",
            action = action,
        )

    private fun value(key: String, title: String, subtitle: String, value: String, action: () -> Unit) =
        TvItem.Entry(
            key = key,
            title = title,
            subtitle = subtitle,
            meta = value,
            action = action,
        )

    companion object {
        const val LABEL = "Settings"
    }
}
