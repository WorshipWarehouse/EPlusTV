package com.gameday.tv.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRetryTest {

    @Test
    fun `transient failures retry up to the attempt limit`() {
        assertTrue(PlaybackRetry.shouldRetry(PlaybackFailure.NETWORK, 0))
        assertTrue(PlaybackRetry.shouldRetry(PlaybackFailure.NETWORK, 2))
        assertFalse(PlaybackRetry.shouldRetry(PlaybackFailure.NETWORK, PlaybackRetry.MAX_ATTEMPTS))

        assertTrue(PlaybackRetry.shouldRetry(PlaybackFailure.TIMEOUT, 0))
        assertTrue(PlaybackRetry.shouldRetry(PlaybackFailure.SERVER, 1))
    }

    @Test
    fun `authorisation, drm and decoder failures are not retried`() {
        listOf(
            PlaybackFailure.AUTH,
            PlaybackFailure.NOT_FOUND,
            PlaybackFailure.DECODER,
            PlaybackFailure.DRM,
            PlaybackFailure.UNSUPPORTED,
        ).forEach { failure ->
            assertFalse(failure.name, PlaybackRetry.shouldRetry(failure, 0))
        }
    }

    @Test
    fun `backoff grows and is capped`() {
        assertEquals(1_000L, PlaybackRetry.delayMs(0))
        assertEquals(2_000L, PlaybackRetry.delayMs(1))
        assertEquals(4_000L, PlaybackRetry.delayMs(2))
        assertEquals(8_000L, PlaybackRetry.delayMs(3))
        assertEquals(8_000L, PlaybackRetry.delayMs(10))
    }

    @Test
    fun `every failure has a message that does not promise a workaround for drm`() {
        PlaybackFailure.values().forEach { failure ->
            assertTrue(failure.name, PlaybackErrors.describe(failure).isNotBlank())
        }
        assertTrue(PlaybackErrors.describe(PlaybackFailure.DRM).contains("does not bypass DRM"))
    }
}
