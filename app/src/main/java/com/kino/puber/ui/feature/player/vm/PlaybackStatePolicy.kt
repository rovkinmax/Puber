package com.kino.puber.ui.feature.player.vm

import androidx.media3.common.Player
import com.kino.puber.ui.feature.player.model.PlayerContentState

internal enum class PlaybackIntent {
    PlayRequested,
    Paused,
    Inactive,
}

internal data class PlaybackSnapshot(
    val isPlaying: Boolean,
    val intent: PlaybackIntent,
    val shouldKeepScreenOn: Boolean,
    val shouldResumeAfterStreamSwitch: Boolean,
    val isBuffering: Boolean,
    val position: Long,
    val duration: Long,
    val buffered: Long,
)

internal object PlaybackStatePolicy {
    fun derive(
        isPlaying: Boolean,
        playWhenReady: Boolean,
        playbackState: Int,
        suppressionReason: Int,
        position: Long = 0L,
        duration: Long = 0L,
        buffered: Long = 0L,
    ): PlaybackSnapshot {
        val isActiveState = playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING
        val intent = when {
            !isActiveState -> PlaybackIntent.Inactive
            playWhenReady -> PlaybackIntent.PlayRequested
            else -> PlaybackIntent.Paused
        }
        return PlaybackSnapshot(
            isPlaying = isPlaying,
            intent = intent,
            shouldKeepScreenOn = intent == PlaybackIntent.PlayRequested &&
                suppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE,
            shouldResumeAfterStreamSwitch = intent == PlaybackIntent.PlayRequested,
            isBuffering = playbackState == Player.STATE_BUFFERING,
            position = position,
            duration = duration,
            buffered = buffered,
        )
    }
}

internal fun PlayerContentState.withPlaybackSnapshot(snapshot: PlaybackSnapshot): PlayerContentState {
    return copy(
        isPlaying = snapshot.isPlaying,
        playbackIntent = snapshot.intent,
        shouldKeepScreenOn = snapshot.shouldKeepScreenOn,
        currentPosition = snapshot.position,
        duration = snapshot.duration,
        bufferedPosition = snapshot.buffered,
    )
}
