package com.kino.puber.ui.feature.player.vm

import androidx.media3.common.Player
import com.kino.puber.data.api.models.SubtitleLink
import com.kino.puber.domain.interactor.player.StreamSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

internal class PlaybackControllerTransitionsTest {

    @Test
    fun switchStream_derivesResumePolicy_andPreservesOrderedMutations_forRequiredStateMatrix() {
        listOf(
            StreamSwitchCase(
                label = "ready playing",
                playbackState = Player.STATE_READY,
                isPlaying = true,
                playWhenReady = true,
                suppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_NONE,
                shouldResume = true,
            ),
            StreamSwitchCase(
                label = "buffering",
                playbackState = Player.STATE_BUFFERING,
                isPlaying = false,
                playWhenReady = true,
                suppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_NONE,
                shouldResume = true,
            ),
            StreamSwitchCase(
                label = "paused",
                playbackState = Player.STATE_READY,
                isPlaying = false,
                playWhenReady = false,
                suppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_NONE,
                shouldResume = false,
            ),
            StreamSwitchCase(
                label = "suppressed",
                playbackState = Player.STATE_READY,
                isPlaying = false,
                playWhenReady = true,
                suppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS,
                shouldResume = true,
            ),
            StreamSwitchCase(
                label = "ended",
                playbackState = Player.STATE_ENDED,
                isPlaying = false,
                playWhenReady = true,
                suppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_NONE,
                shouldResume = false,
                expectedSeekMutation = "seekToDefaultPosition",
            ),
            StreamSwitchCase(
                label = "idle",
                playbackState = Player.STATE_IDLE,
                isPlaying = false,
                playWhenReady = true,
                suppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_NONE,
                shouldResume = false,
            ),
        ).forEach { testCase ->
            val engine = RecordingPlaybackEngine(
                playbackState = testCase.playbackState,
                isPlaying = testCase.isPlaying,
                playWhenReady = testCase.playWhenReady,
                playbackSuppressionReason = testCase.suppressionReason,
                currentPosition = 120_000L,
            )

            PlaybackTransitions.switchStream(
                engine = engine,
                stream = StreamSource("https://test/new.m3u8", isHls = true),
                subtitles = null,
            )

            assertEquals(
                listOf(
                    "stop",
                    "setMediaSource:https://test/new.m3u8",
                    "restoreTrackSelection",
                    "setPlayWhenReady:${testCase.shouldResume}",
                    "prepare",
                    testCase.expectedSeekMutation,
                ),
                engine.mutations,
                testCase.label,
            )
        }
    }

    @Test
    fun play_afterNaturalEnd_seeksToDefaultPosition_beforeRequestingPlayback() {
        val engine = RecordingPlaybackEngine(
            playbackState = Player.STATE_ENDED,
            currentPosition = 2_400_000L,
        )

        PlaybackTransitions.play(engine)

        assertEquals(
            listOf("seekToDefaultPosition", "play"),
            engine.mutations,
        )
    }

    @Test
    fun play_afterEndedStreamReplacement_restartsFromDefault_withoutDuplicateEndEvent() {
        val sink = RecordingPlaybackEventSink()
        val engine = RecordingPlaybackEngine(
            playbackState = Player.STATE_ENDED,
            isPlaying = false,
            playWhenReady = true,
            currentPosition = 2_400_000L,
            eventSink = sink,
        )
        engine.dispatchPlaybackState(Player.STATE_ENDED)
        assertEquals(1, sink.playbackEndedDispatches)

        PlaybackTransitions.switchStream(
            engine = engine,
            stream = StreamSource("https://test/quality.m3u8", isHls = true),
            subtitles = null,
        )
        PlaybackTransitions.play(engine)

        assertEquals(1, sink.playbackEndedDispatches)
        assertEquals(
            listOf(
                "stop",
                "setMediaSource:https://test/quality.m3u8",
                "restoreTrackSelection",
                "setPlayWhenReady:false",
                "prepare",
                "seekToDefaultPosition",
                "play",
            ),
            engine.mutations,
        )
        assertFalse(engine.mutations.contains("seekTo:2400000"))
    }

    @Test
    fun endedAndIdleStreamReplacement_publishNoActiveIntentOrDuplicateEndEvent() {
        listOf(Player.STATE_ENDED, Player.STATE_IDLE).forEach { replacementState ->
            val sink = RecordingPlaybackEventSink()
            val engine = RecordingPlaybackEngine(
                playbackState = Player.STATE_ENDED,
                isPlaying = false,
                playWhenReady = true,
                playbackSuppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_NONE,
                currentPosition = 2_400_000L,
                eventSink = sink,
            )
            engine.dispatchPlaybackState(Player.STATE_ENDED)
            assertEquals(1, sink.playbackEndedDispatches)
            if (replacementState == Player.STATE_IDLE) {
                engine.dispatchPlaybackState(Player.STATE_IDLE)
            }
            sink.snapshots.clear()

            PlaybackTransitions.switchStream(
                engine = engine,
                stream = StreamSource("https://test/quality.m3u8", isHls = true),
                subtitles = null,
            )

            assertEquals(1, sink.playbackEndedDispatches)
            assertFalse(
                sink.snapshots.any { snapshot ->
                    snapshot.intent == PlaybackIntent.PlayRequested
                },
            )
            assertFalse(sink.snapshots.any(PlaybackSnapshot::shouldKeepScreenOn))
            assertEquals(
                listOf(
                    "stop",
                    "setMediaSource:https://test/quality.m3u8",
                    "restoreTrackSelection",
                    "setPlayWhenReady:false",
                    "prepare",
                    if (replacementState == Player.STATE_ENDED) {
                        "seekToDefaultPosition"
                    } else {
                        "seekTo:2400000"
                    },
                ),
                engine.mutations,
            )
        }
    }

    private class RecordingPlaybackEngine(
        playbackState: Int,
        override val isPlaying: Boolean = false,
        playWhenReady: Boolean = false,
        override val playbackSuppressionReason: Int = Player.PLAYBACK_SUPPRESSION_REASON_NONE,
        override val currentPosition: Long,
        private val eventSink: PlaybackEventSink? = null,
    ) : PlaybackEnginePort {
        val mutations = mutableListOf<String>()
        private var requestedPlayWhenReady = playWhenReady
        override val playWhenReady: Boolean
            get() = requestedPlayWhenReady
        override var playbackState: Int = playbackState
            private set
        override val duration: Long = 2_400_000L
        override val bufferedPosition: Long = currentPosition
        override var trackSelectionParameters: Any = Any()

        override fun stop() {
            mutations += "stop"
            dispatchPlaybackState(Player.STATE_IDLE)
        }

        override fun setMediaSource(stream: StreamSource, subtitles: List<SubtitleLink>?) {
            mutations += "setMediaSource:${stream.url}"
        }

        override fun restoreTrackSelection() {
            mutations += "restoreTrackSelection"
        }

        override fun prepare() {
            mutations += "prepare"
            dispatchPlaybackState(Player.STATE_BUFFERING)
        }

        override fun seekTo(positionMs: Long) {
            mutations += "seekTo:$positionMs"
        }

        override fun setPlayWhenReady(playWhenReady: Boolean) {
            mutations += "setPlayWhenReady:$playWhenReady"
            requestedPlayWhenReady = playWhenReady
            PlaybackTransitions.dispatchPlaybackSnapshot(this, eventSink)
        }

        override fun seekToDefaultPosition() {
            mutations += "seekToDefaultPosition"
        }

        override fun play() {
            mutations += "play"
        }

        fun dispatchPlaybackState(playbackState: Int) {
            this.playbackState = playbackState
            PlaybackTransitions.dispatchPlaybackState(
                engine = this,
                playbackState = playbackState,
                sink = eventSink,
            )
        }
    }

    private class RecordingPlaybackEventSink : PlaybackEventSink {
        val snapshots = mutableListOf<PlaybackSnapshot>()
        var playbackEndedDispatches = 0

        override fun onPlaybackStateChanged(snapshot: PlaybackSnapshot) {
            snapshots += snapshot
        }

        override fun onPlaybackEnded() {
            playbackEndedDispatches += 1
        }
    }

    private data class StreamSwitchCase(
        val label: String,
        val playbackState: Int,
        val isPlaying: Boolean,
        val playWhenReady: Boolean,
        val suppressionReason: Int,
        val shouldResume: Boolean,
        val expectedSeekMutation: String = "seekTo:120000",
    )
}
