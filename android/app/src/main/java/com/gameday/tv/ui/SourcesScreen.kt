package com.gameday.tv.ui

import com.gameday.tv.model.SourceConfig
import com.gameday.tv.model.SourceKind
import com.gameday.tv.providers.HeaderParser
import java.net.URL

class SourcesScreen(host: MainActivity) : ListScreen(host, "Sources") {

    override val label: String = LABEL

    private val addButton = UiKit.button(host, "Add source") { chooseKind() }

    init {
        headerActions.addView(addButton)
    }

    override fun render() {
        val sourceStore = host.container.sourceStore
        val sources = sourceStore.list()
        val items = ArrayList<TvItem>()

        subtitleView.text = "Add only streams you are authorised to watch"

        if (sources.isEmpty()) {
            items += TvItem.Header("No sources yet")
            items += TvItem.Entry(
                key = "add-m3u",
                title = "Add an M3U playlist",
                subtitle = "Channels and HLS stream URLs from your own subscription",
                badge = "+",
                action = { addSource(SourceKind.M3U) },
            )
            items += TvItem.Entry(
                key = "add-eplustv",
                title = "Add an EPlusTV gateway",
                subtitle = "Your own EPlusTV instance on the local network",
                badge = "+",
                action = { addSource(SourceKind.EPLUSTV) },
            )
            items += TvItem.Entry(
                key = "add-direct",
                title = "Add one HLS channel",
                subtitle = "A single authorised manifest address",
                badge = "+",
                action = { addSource(SourceKind.DIRECT) },
            )
            setItems(items, "No sources configured.")
            return
        }

        items += TvItem.Header("${sources.size} source(s)")
        sources.forEach { source ->
            items += TvItem.Entry(
                key = source.id,
                title = source.name,
                subtitle = buildString {
                    append(kindLabel(source.kind))
                    append(" \u00b7 ")
                    append(hostOf(source.url))
                    if (source.hasHeaders) append(" \u00b7 custom headers")
                },
                meta = if (source.enabled) "On" else "Off",
                badge = "\u25A0",
                action = { sourceMenu(source) },
                longAction = { sourceMenu(source) },
            )
        }

        items += TvItem.Header("Add another")
        items += TvItem.Entry(
            key = "add-more",
            title = "Add source",
            subtitle = "M3U playlist, EPlusTV gateway, or single HLS channel",
            badge = "+",
            action = { chooseKind() },
        )

        setItems(items, "No sources configured.")
    }

    private fun chooseKind() {
        host.showItemMenu(
            "Add source",
            listOf("M3U playlist URL", "EPlusTV gateway on this LAN", "Single HLS channel"),
        ) { which ->
            when (which) {
                0 -> addSource(SourceKind.M3U)
                1 -> addSource(SourceKind.EPLUSTV)
                else -> addSource(SourceKind.DIRECT)
            }
        }
    }

    private fun addSource(kind: SourceKind) {
        val (heading, hint) = when (kind) {
            SourceKind.M3U -> "M3U playlist URL" to "Address of your authorised playlist"
            SourceKind.EPLUSTV -> "EPlusTV gateway URL" to "For example http://192.168.1.20:8000"
            SourceKind.DIRECT -> "HLS manifest URL" to "Address of one authorised stream"
        }
        host.promptText(heading, hint = hint, allowUrlSymbols = true) { url ->
            val trimmed = url.trim()
            if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                host.toast("Enter a full http:// or https:// address")
                return@promptText
            }
            val source = SourceConfig(
                id = host.container.sourceStore.newId(),
                name = when (kind) {
                    SourceKind.EPLUSTV -> "EPlusTV (${hostOf(trimmed)})"
                    SourceKind.DIRECT -> "HLS channel"
                    SourceKind.M3U -> hostOf(trimmed)
                },
                kind = kind,
                url = trimmed,
            )
            host.container.sourceStore.save(source)
            host.toast("Added ${source.name}")
            render()
            host.refreshGuideNow()
        }
    }

    private fun sourceMenu(source: SourceConfig) {
        val options = arrayListOf(
            "Refresh guide now",
            if (source.enabled) "Disable" else "Enable",
            "Rename",
            "Edit URL",
            "HTTP headers",
            "Delete",
        )
        host.showItemMenu(source.name, options) { which ->
            when (which) {
                0 -> {
                    host.navigateTo(HomeScreen.LABEL)
                    host.refreshGuideNow()
                }
                1 -> save(source.copy(enabled = !source.enabled))
                2 -> host.promptText("Rename source", source.name) { name ->
                    if (name.isNotBlank()) save(source.copy(name = name))
                }
                3 -> host.promptText("Edit URL", source.url, allowUrlSymbols = true) { url ->
                    if (url.startsWith("http")) save(source.copy(url = url.trim()))
                }
                4 -> editHeaders(source)
                else -> host.confirm(
                    "Delete source",
                    "Remove \"${source.name}\" and its stored headers from this device?",
                    "Delete",
                ) {
                    host.container.sourceStore.delete(source.id)
                    render()
                    host.toast("Source deleted")
                }
            }
        }
    }

    private fun editHeaders(source: SourceConfig) {
        val current = HeaderParser.format(host.container.sourceStore.headersFor(source))
        host.promptText(
            heading = "HTTP headers",
            initial = current,
            hint = "Name=Value;Name=Value (stored encrypted)",
            allowUrlSymbols = true,
        ) { spec ->
            host.container.sourceStore.saveHeaders(source.id, HeaderParser.parse(spec))
            save(source.copy())
            host.toast("Headers saved")
        }
    }

    private fun save(source: SourceConfig) {
        host.container.sourceStore.save(source)
        render()
    }

    private fun kindLabel(kind: SourceKind): String = when (kind) {
        SourceKind.M3U -> "M3U playlist"
        SourceKind.EPLUSTV -> "EPlusTV gateway"
        SourceKind.DIRECT -> "Direct HLS"
    }

    private fun hostOf(url: String): String = try {
        URL(url).host ?: url
    } catch (e: Exception) {
        url
    }

    companion object {
        const val LABEL = "Sources"
    }
}
