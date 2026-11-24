package com.gameday.tv.playback

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.gameday.tv.model.Channel

data class PlaybackParams(
    val streamUrl: String,
    val title: String,
    val subtitle: String,
    val headers: Map<String, String>,
)

object PlaybackRequest {

    private const val EXTRA_URL = "gameday.url"
    private const val EXTRA_TITLE = "gameday.title"
    private const val EXTRA_SUBTITLE = "gameday.subtitle"
    private const val EXTRA_HEADERS = "gameday.headers"

    fun intent(context: Context, channel: Channel, subtitle: String = ""): Intent {
        val headers = Bundle().apply {
            channel.headers.forEach { (key, value) -> putString(key, value) }
        }
        return Intent(context, PlayerActivity::class.java).apply {
            putExtra(EXTRA_URL, channel.streamUrl)
            putExtra(EXTRA_TITLE, channel.name)
            putExtra(EXTRA_SUBTITLE, subtitle)
            putExtra(EXTRA_HEADERS, headers)
        }
    }

    fun parse(intent: Intent): PlaybackParams? {
        val url = intent.getStringExtra(EXTRA_URL)?.takeIf { it.isNotBlank() } ?: return null
        val bundle = intent.getBundleExtra(EXTRA_HEADERS)
        val headers = LinkedHashMap<String, String>()
        bundle?.keySet()?.forEach { key ->
            bundle.getString(key)?.let { headers[key] = it }
        }
        return PlaybackParams(
            streamUrl = url,
            title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "GameDay" },
            subtitle = intent.getStringExtra(EXTRA_SUBTITLE).orEmpty(),
            headers = headers,
        )
    }
}
