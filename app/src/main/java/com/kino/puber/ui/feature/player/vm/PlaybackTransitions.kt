package com.kino.puber.ui.feature.player.vm

import com.kino.puber.data.api.models.SubtitleLink
import com.kino.puber.domain.interactor.player.StreamSource

internal interface PlaybackEnginePort {
    val isPlaying: Boolean
    val playWhenReady: Boolean
    val playbackState: Int
    val playbackSuppressionReason: Int
    val currentPosition: Long
    val duration: Long
    val bufferedPosition: Long
    var trackSelectionParameters: Any

    fun stop()
    fun setMediaSource(stream: StreamSource, subtitles: List<SubtitleLink>?)
    fun restoreTrackSelection()
    fun prepare()
    fun seekTo(positionMs: Long)
    fun setPlayWhenReady(playWhenReady: Boolean)
    fun seekToDefaultPosition()
    fun play()
}

internal interface PlaybackEventSink {
    fun onPlaybackStateChanged(snapshot: PlaybackSnapshot)
    fun onPlaybackEnded()
}

internal object PlaybackTransitions {
    fun switchStream(
        engine: PlaybackEnginePort,
        stream: StreamSource,
        subtitles: List<SubtitleLink>?,
    ) {
        val savedPosition = engine.currentPosition
        val savedTrackSelectionParameters = engine.trackSelectionParameters
        val shouldRestartFromDefault = engine.playbackState == androidx.media3.common.Player.STATE_ENDED
        val shouldResume = engine.snapshot().shouldResumeAfterStreamSwitch

        engine.stop()
        engine.setMediaSource(stream, subtitles)
        engine.trackSelectionParameters = savedTrackSelectionParameters
        engine.restoreTrackSelection()
        engine.setPlayWhenReady(shouldResume)
        engine.prepare()
        if (shouldRestartFromDefault) {
            engine.seekToDefaultPosition()
        } else {
            engine.seekTo(savedPosition)
        }
    }

    fun play(engine: PlaybackEnginePort) {
        if (engine.playbackState == androidx.media3.common.Player.STATE_ENDED) {
            engine.seekToDefaultPosition()
        }
        engine.play()
    }

    fun dispatchPlaybackState(
        engine: PlaybackEnginePort,
        playbackState: Int,
        sink: PlaybackEventSink?,
    ) {
        dispatchPlaybackSnapshot(engine, sink)
        if (playbackState == androidx.media3.common.Player.STATE_ENDED) {
            sink?.onPlaybackEnded()
        }
    }

    fun dispatchPlaybackSnapshot(
        engine: PlaybackEnginePort,
        sink: PlaybackEventSink?,
    ) {
        sink?.onPlaybackStateChanged(engine.snapshot())
    }

    private fun PlaybackEnginePort.snapshot(): PlaybackSnapshot {
        return PlaybackStatePolicy.derive(
            isPlaying = isPlaying,
            playWhenReady = playWhenReady,
            playbackState = playbackState,
            suppressionReason = playbackSuppressionReason,
            position = currentPosition,
            duration = duration,
            buffered = bufferedPosition,
        )
    }
}
