package com.gameday.tv.playback

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.gameday.tv.GameDayApp
import com.gameday.tv.R
import com.gameday.tv.core.Diag
import com.gameday.tv.ui.UiKit
import java.util.Locale

// Media3 marks its renderer/HLS/PlayerView surface as @UnstableApi. Opting in here consumes the
// requirement for this class only, so callers (PlaybackRequest/MainActivity) stay unaffected.
@androidx.annotation.OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity(), Player.Listener {

    private val settings get() = GameDayApp.container(this).settings

    private lateinit var params: PlaybackParams
    private lateinit var playerView: PlayerView
    private lateinit var player: ExoPlayer
    private lateinit var errorPanel: LinearLayout
    private lateinit var errorText: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var attempt = 0
    private var zoomed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val parsed = PlaybackRequest.parse(intent)
        if (parsed == null) {
            finish()
            return
        }
        params = parsed
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUi()
        buildUi()
        createPlayer()
        startPlayback(0L)
    }

    private fun hideSystemUi() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun buildUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        playerView = PlayerView(this).apply {
            useController = true
            controllerShowTimeoutMs = 5_000
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            setBackgroundColor(Color.BLACK)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        root.addView(playerView)

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(40), dp(26), dp(40), dp(18))
            background = UiKit.rounded(Color.parseColor("#99000000"), dp(12))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START,
            ).apply {
                topMargin = dp(20)
                marginStart = dp(24)
            }
        }
        topBar.addView(UiKit.text(this, params.title, 19f, bold = true))
        if (params.subtitle.isNotBlank()) {
            topBar.addView(
                UiKit.text(this, params.subtitle, 12.5f, R.color.text_secondary).apply {
                    setPadding(0, dp(3), 0, 0)
                },
            )
        }
        topBar.addView(
            UiKit.text(this, "MENU: audio \u00b7 subtitles \u00b7 aspect \u00b7 info", 11f, R.color.text_secondary)
                .apply { setPadding(0, dp(8), 0, 0) },
        )
        root.addView(topBar)

        errorPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(22), dp(28), dp(22))
            background = UiKit.rounded(Color.parseColor("#F2151C25"), dp(14), UiKit.color(this@PlayerActivity, R.color.danger), dp(2))
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(dp(640), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        }
        errorText = UiKit.text(this, "", 15f).apply {
            gravity = Gravity.CENTER
            setLineSpacing(dp(4).toFloat(), 1f)
        }
        errorPanel.addView(errorText)

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
        }
        val retry = UiKit.button(this, "Retry") {
            attempt = 0
            startPlayback(player.currentPosition)
        }
        val back = UiKit.button(this, "Back") { finish() }.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = dp(12) }
        }
        buttons.addView(retry)
        buttons.addView(back)
        errorPanel.addView(buttons)
        root.addView(errorPanel)

        setContentView(root)
    }

    private fun createPlayer() {
        val renderersFactory = DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true)
            .setMediaCodecSelector(codecSelector())

        val trackSelector = DefaultTrackSelector(this)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 45_000, 1_500, 3_000)
            .build()

        player = ExoPlayer.Builder(this, renderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .build()
        player.addListener(this)
        playerView.player = player
        applyTrackParameters()
    }

    private fun codecSelector(): MediaCodecSelector {
        if (settings.preferHardwareDecoding) return MediaCodecSelector.DEFAULT
        return MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            MediaCodecSelector.DEFAULT
                .getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
                .filter { it.softwareOnly }
        }
    }

    private fun buildMediaSource(): MediaSource {
        val userAgent = params.headers["User-Agent"]
            ?: "Mozilla/5.0 (Linux; Android 9; AFTMM Build/PS7233) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(params.headers.filterKeys { it != "User-Agent" })

        return HlsMediaSource.Factory(dataSourceFactory)
            .setAllowChunklessPreparation(true)
            .createMediaSource(MediaItem.fromUri(params.streamUrl))
    }

    private fun startPlayback(startPositionMs: Long) {
        hideError()
        applyTrackParameters()
        player.setMediaSource(buildMediaSource())
        player.prepare()
        if (startPositionMs > 0L) player.seekTo(startPositionMs)
        player.playWhenReady = true
        Diag.info("Player", "Starting playback for ${params.title}")
    }

    private fun applyTrackParameters() {
        val builder = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !settings.subtitlesEnabled)
        settings.preferredAudioLanguage.takeIf { it.isNotBlank() }?.let {
            builder.setPreferredAudioLanguage(it)
        }
        if (settings.dataSaver) builder.setMaxVideoBitrate(2_500_000)
        player.trackSelectionParameters = builder.build()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_READY -> {
                attempt = 0
                hideError()
            }
            Player.STATE_ENDED -> showError("This stream has ended.")
            else -> Unit
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        val failure = failureOf(error)
        Diag.warn("Player", "Playback error ${error.errorCode}: ${error.message}")
        if (PlaybackRetry.shouldRetry(failure, attempt)) {
            attempt++
            val delay = PlaybackRetry.delayMs(attempt - 1)
            Diag.info("Player", "Retrying playback in ${delay}ms (attempt $attempt)")
            handler.postDelayed({ startPlayback(player.currentPosition) }, delay)
        } else {
            showError(PlaybackErrors.describe(failure))
        }
    }

    private fun failureOf(error: PlaybackException): PlaybackFailure = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        -> PlaybackFailure.NETWORK

        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> PlaybackFailure.NOT_FOUND

        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
            val responseCode = (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode
            when (responseCode) {
                401, 403 -> PlaybackFailure.AUTH
                404 -> PlaybackFailure.NOT_FOUND
                else -> PlaybackFailure.SERVER
            }
        }

        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
        -> PlaybackFailure.UNSUPPORTED

        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        -> PlaybackFailure.DECODER

        else -> if (error.errorCode in 6000..6999) PlaybackFailure.DRM else PlaybackFailure.UNKNOWN
    }

    private fun showError(message: String) {
        errorText.text = message
        errorPanel.visibility = View.VISIBLE
        errorPanel.requestFocus()
        (errorPanel.getChildAt(1) as? ViewGroup)?.getChildAt(0)?.requestFocus()
    }

    private fun hideError() {
        errorPanel.visibility = View.GONE
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN &&
            (event.keyCode == KeyEvent.KEYCODE_MENU || event.keyCode == KeyEvent.KEYCODE_INFO)
        ) {
            showOptions()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun showOptions() {
        val items = arrayListOf(
            "Audio track",
            "Subtitles: ${if (settings.subtitlesEnabled) "on" else "off"}",
            "Aspect: ${if (zoomed) "zoom" else "fit"}",
            "Playback info",
            "Retry stream",
        )
        AlertDialog.Builder(this)
            .setTitle(params.title)
            .setItems(items.toTypedArray()) { _, which ->
                when (which) {
                    0 -> chooseAudioTrack()
                    1 -> toggleSubtitles()
                    2 -> toggleAspect()
                    3 -> showInfo()
                    else -> {
                        attempt = 0
                        startPlayback(player.currentPosition)
                    }
                }
            }
            .show()
    }

    private fun chooseAudioTrack() {
        val groups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        val labels = ArrayList<String>()
        val references = ArrayList<Pair<Tracks.Group, Int>>()
        groups.forEach { group ->
            for (index in 0 until group.length) {
                labels += audioLabel(group.getTrackFormat(index))
                references += group to index
            }
        }
        if (labels.isEmpty()) {
            Diag.info("Player", "No selectable audio tracks yet")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Audio track")
            .setItems(labels.toTypedArray()) { _, which ->
                val (group, index) = references[which]
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
                    .build()
            }
            .show()
    }

    private fun audioLabel(format: Format): String {
        val label: String? = format.label
        val code: String? = format.language
        val language = when {
            !label.isNullOrBlank() -> label
            !code.isNullOrBlank() -> {
                val display = Locale.forLanguageTag(code).displayLanguage
                if (display.isBlank()) code else display
            }
            else -> "Unknown language"
        }
        val channels = when (format.channelCount) {
            1 -> "mono"
            2 -> "stereo"
            6 -> "5.1"
            8 -> "7.1"
            else -> ""
        }
        return if (channels.isBlank()) language else "$language \u00b7 $channels"
    }

    private fun toggleSubtitles() {
        settings.subtitlesEnabled = !settings.subtitlesEnabled
        applyTrackParameters()
        if (settings.subtitlesEnabled) {
            val group = player.currentTracks.groups.firstOrNull { it.type == C.TRACK_TYPE_TEXT } ?: return
            val index = (0 until group.length).firstOrNull { group.isTrackSupported(it) } ?: 0
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
                .build()
        }
    }

    private fun toggleAspect() {
        zoomed = !zoomed
        playerView.resizeMode = if (zoomed) {
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        } else {
            AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    }

    private fun showInfo() {
        val video = player.videoFormat
        val text = buildString {
            appendLine("Channel: ${params.title}")
            appendLine("State: ${player.playbackState}")
            video?.let {
                appendLine("Video: ${it.width}x${it.height} ${it.codecs.orEmpty()}".trim())
            }
            val audioTracks = player.currentTracks.groups.count { it.type == C.TRACK_TYPE_AUDIO }
            val textTracks = player.currentTracks.groups.count { it.type == C.TRACK_TYPE_TEXT }
            appendLine("Audio track groups: $audioTracks")
            appendLine("Subtitle track groups: $textTracks")
            appendLine("Buffered: ${player.bufferedPosition / 1000}s")
            append("Hardware decoding: ${if (settings.preferHardwareDecoding) "preferred" else "software only"}")
        }
        AlertDialog.Builder(this)
            .setTitle("Playback info")
            .setMessage(text)
            .setPositiveButton("Close", null)
            .show()
    }

    override fun onStop() {
        super.onStop()
        if (::player.isInitialized) player.pause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::player.isInitialized) {
            player.removeListener(this)
            player.release()
        }
        super.onDestroy()
    }

    private fun dp(value: Int): Int = UiKit.dp(this, value.toFloat())
}
