package com.kino.puber.ui.feature.player.vm

import androidx.media3.common.Player
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class PlaybackIntentTest {

    @Test
    fun `playing counts as intended playback`() {
        assertTrue(intended(playWhenReady = true, playbackState = Player.STATE_READY))
    }

    @Test
    fun `re-buffering keeps playback intended`() {
        assertTrue(intended(playWhenReady = true, playbackState = Player.STATE_BUFFERING))
    }

    @Test
    fun `pause during a stall stops playback intent`() {
        assertFalse(intended(playWhenReady = false, playbackState = Player.STATE_BUFFERING))
    }

    @Test
    fun `pause during playback stops playback intent`() {
        assertFalse(intended(playWhenReady = false, playbackState = Player.STATE_READY))
    }

    @Test
    fun `finished media stops playback intent`() {
        assertFalse(intended(playWhenReady = true, playbackState = Player.STATE_ENDED))
    }

    @Test
    fun `idle player stops playback intent`() {
        assertFalse(intended(playWhenReady = true, playbackState = Player.STATE_IDLE))
    }

    @Test
    fun `transient audio focus loss stops playback intent`() {
        assertFalse(
            intended(
                playWhenReady = true,
                playbackState = Player.STATE_READY,
                suppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS,
            )
        )
    }

    @Test
    fun `audio focus regained restores playback intent`() {
        assertTrue(
            intended(
                playWhenReady = true,
                playbackState = Player.STATE_READY,
                suppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_NONE,
            )
        )
    }

    private fun intended(
        playWhenReady: Boolean,
        playbackState: Int,
        suppressionReason: Int = Player.PLAYBACK_SUPPRESSION_REASON_NONE,
    ) = isPlaybackIntended(playWhenReady, playbackState, suppressionReason)
}
