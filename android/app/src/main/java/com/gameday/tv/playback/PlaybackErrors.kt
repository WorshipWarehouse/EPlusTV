package com.gameday.tv.playback

/** Transport-agnostic failure reasons so messages are testable without Media3. */
enum class PlaybackFailure {
    NETWORK,
    TIMEOUT,
    AUTH,
    NOT_FOUND,
    SERVER,
    DECODER,
    DRM,
    UNSUPPORTED,
    UNKNOWN,
}

object PlaybackErrors {

    fun describe(failure: PlaybackFailure): String = when (failure) {
        PlaybackFailure.NETWORK ->
            "No connection to the stream. Check the network, then retry."
        PlaybackFailure.TIMEOUT ->
            "The stream server took too long to respond. Retry in a moment."
        PlaybackFailure.AUTH ->
            "The provider rejected this session. Sign in again or refresh the channel's access token."
        PlaybackFailure.NOT_FOUND ->
            "The stream is not available at this address. It may have ended."
        PlaybackFailure.SERVER ->
            "The stream server returned an error."
        PlaybackFailure.DECODER ->
            "This device could not decode the video. Try again, or turn off hardware decoding in Settings."
        PlaybackFailure.DRM ->
            "Protected playback could not be authorised. GameDay does not bypass DRM; use a supported provider app or device."
        PlaybackFailure.UNSUPPORTED ->
            "This stream format is not supported on this device."
        PlaybackFailure.UNKNOWN ->
            "Playback stopped unexpectedly."
    }
}

/**
 * Retry policy: transient network faults are retried with exponential backoff; authorisation,
 * DRM and decoder faults are not, because retrying cannot fix them.
 */
object PlaybackRetry {

    const val MAX_ATTEMPTS = 3

    fun shouldRetry(failure: PlaybackFailure, attempt: Int): Boolean {
        if (attempt >= MAX_ATTEMPTS) return false
        return when (failure) {
            PlaybackFailure.NETWORK,
            PlaybackFailure.TIMEOUT,
            PlaybackFailure.SERVER,
            PlaybackFailure.UNKNOWN,
            -> true
            PlaybackFailure.AUTH,
            PlaybackFailure.NOT_FOUND,
            PlaybackFailure.DECODER,
            PlaybackFailure.DRM,
            PlaybackFailure.UNSUPPORTED,
            -> false
        }
    }

    fun delayMs(attempt: Int): Long {
        val base = 1_000L shl attempt.coerceIn(0, 4)
        return base.coerceAtMost(8_000L)
    }
}
