package com.kino.puber.ui.feature.player.vm

import androidx.media3.common.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class PlaybackStatePolicyTest {

    @Test
    fun readyPlayRequested_keepsScreenOn_andResumesAfterStreamSwitch() {
        assertSnapshot(
            snapshot = PlaybackStatePolicy.derive(
                isPlaying = true,
                playWhenReady = true,
                playbackState = Player.STATE_READY,
                suppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_NONE,
            ),
            expectedIntent = PlaybackIntent.PlayRequested,
            expectedKeepScreenOn = true,
            expectedResumeAfterStreamSwitch = true,
        )
    }

    @Test
    fun bufferingPlayRequested_withoutSuppression_keepsScreenOnWithoutActualProgression() {
        val snapshot = PlaybackStatePolicy.derive(
            isPlaying = false,
            playWhenReady = true,
            playbackState = Player.STATE_BUFFERING,
            suppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_NONE,
        )

        assertEquals(false, snapshot.isPlaying)
        assertSnapshot(
            snapshot = snapshot,
            expectedIntent = PlaybackIntent.PlayRequested,
            expectedKeepScreenOn = true,
            expectedResumeAfterStreamSwitch = true,
        )
    }

    @Test
    fun bufferingPlayRequested_keepsResumeIntent_butSuppressionReleasesScreen() {
        assertSnapshot(
            snapshot = PlaybackStatePolicy.derive(
                isPlaying = false,
                playWhenReady = true,
                playbackState = Player.STATE_BUFFERING,
                suppressionReason = 1,
            ),
            expectedIntent = PlaybackIntent.PlayRequested,
            expectedKeepScreenOn = false,
            expectedResumeAfterStreamSwitch = true,
        )
    }

    @Test
    fun readyPaused_releasesScreen_andDoesNotResumeAfterStreamSwitch() {
        assertSnapshot(
            snapshot = PlaybackStatePolicy.derive(
                isPlaying = false,
                playWhenReady = false,
                playbackState = Player.STATE_READY,
                suppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_NONE,
            ),
            expectedIntent = PlaybackIntent.Paused,
            expectedKeepScreenOn = false,
            expectedResumeAfterStreamSwitch = false,
        )
    }

    @Test
    fun idleAndEnded_areInactive_evenWhenPlayWasRequested() {
        listOf(Player.STATE_IDLE, Player.STATE_ENDED).forEach { state ->
            assertSnapshot(
                snapshot = PlaybackStatePolicy.derive(
                    isPlaying = false,
                    playWhenReady = true,
                    playbackState = state,
                    suppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_NONE,
                ),
                expectedIntent = PlaybackIntent.Inactive,
                expectedKeepScreenOn = false,
                expectedResumeAfterStreamSwitch = false,
            )
        }
    }

    @Test
    fun actualProgression_remainsIndependentFromPlayIntent() {
        val snapshot = PlaybackStatePolicy.derive(
            isPlaying = false,
            playWhenReady = true,
            playbackState = Player.STATE_READY,
            suppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_NONE,
        )

        assertEquals(false, snapshot.isPlaying)
        assertEquals(PlaybackIntent.PlayRequested, snapshot.intent)
        assertEquals(true, snapshot.shouldKeepScreenOn)
    }

    private fun assertSnapshot(
        snapshot: PlaybackSnapshot,
        expectedIntent: PlaybackIntent,
        expectedKeepScreenOn: Boolean,
        expectedResumeAfterStreamSwitch: Boolean,
    ) {
        assertEquals(expectedIntent, snapshot.intent)
        assertEquals(expectedKeepScreenOn, snapshot.shouldKeepScreenOn)
        assertEquals(expectedResumeAfterStreamSwitch, snapshot.shouldResumeAfterStreamSwitch)
    }
}
