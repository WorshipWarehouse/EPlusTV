package com.gameday.tv.ui

import com.gameday.tv.epg.GuideQuery

class SearchScreen(host: MainActivity) : ListScreen(host, "Search") {

    override val label: String = LABEL
    private var query: String = ""

    private val searchButton = UiKit.button(host, "Search") { promptQuery() }

    init {
        headerActions.addView(searchButton)
    }

    private fun promptQuery() {
        host.promptText(
            heading = "Search",
            initial = query,
            hint = "Programme or channel name",
            onResult = { value ->
                query = value
                render()
            },
        )
    }

    override fun render() {
        val snapshot = host.container.guideRepository.cached()
        val nowMs = System.currentTimeMillis()
        val items = ArrayList<TvItem>()

        if (query.isBlank()) {
            subtitleView.text = "Find any programme or channel in your guide"
            items += TvItem.Header("Quick searches")
            listOf("NFL", "Football", "Basketball", "Hockey", "Baseball", "Live").forEach { term ->
                items += TvItem.Entry(
                    key = "quick-$term",
                    title = term,
                    subtitle = "Search guide for $term",
                    badge = "\u2315",
                    action = {
                        query = term
                        render()
                    },
                )
            }
            setItems(items, "Press Search to look for a programme.")
            return
        }

        val hits = GuideQuery.search(snapshot, query, 80)
        subtitleView.text = if (hits.isEmpty()) {
            "No matches for \"$query\""
        } else {
            "${hits.size} match(es) for \"$query\""
        }

        hits.forEach { hit ->
            val program = hit.program
            if (program != null) {
                items += UiRows.programEntry(
                    channel = hit.channel,
                    program = program,
                    nowMs = nowMs,
                    onPlay = { host.playChannel(hit.channel, program.title) },
                )
            } else {
                items += TvItem.Entry(
                    key = hit.channel.id,
                    title = hit.channel.name,
                    subtitle = "Channel",
                    badge = hit.channel.number.toString(),
                    action = { host.playChannel(hit.channel) },
                )
            }
        }

        setItems(items, "No matches. Press Search to try another term.")
    }

    companion object {
        const val LABEL = "Search"
    }
}
