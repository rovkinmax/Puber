package com.kino.puber.ui.feature.player.vm

import com.kino.puber.data.api.models.SubtitleLink
import com.kino.puber.ui.feature.player.model.BufferPreset
import com.kino.puber.ui.feature.player.model.PlayerAction
import com.kino.puber.ui.feature.player.model.PlayerViewState
import com.kino.puber.ui.feature.player.model.QualityUIState
import com.kino.puber.ui.feature.player.model.SubtitleTrackUIState
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.every
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

internal class PlayerVMCallbackGenerationTest : PlayerVMTestFixture() {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()

        private const val POST_C_STABILITY_DELIVERIES = 3
    }

    @Test
    fun callbackAfterRelease_doesNotMutateReloadedStateOrLeakMappedError() {
        val controlledPlayback = ControlledPlaybackControl()
        playbackController = controlledPlayback
        val vm = startedVM()
        val releasedSession = controlledPlayback.currentSession

        controlledPlayback.deliverError(releasedSession, "Mapped host error")
        assertEquals(
            PlayerViewState.Error("Mapped host error"),
            vm.testStateValue,
        )

        vm.onAction(PlayerAction.RetryPlayback)
        val reloadedState = vm.testStateValue
        assertTrue(reloadedState is PlayerViewState.Content)

        controlledPlayback.deliverError(releasedSession, "Stale released error")

        assertEquals(reloadedState, vm.testStateValue)
        assertEquals(
            listOf("Mapped host error"),
            controlledPlayback.deliveredErrors,
        )
    }

    @Test
    fun rapidQualitySwitches_ignoreSupersededCallbacksAfterFinalStreamState() {
        val controlledPlayback = ControlledPlaybackControl()
        playbackController = controlledPlayback
        coEvery { contentStateFactory.build(any(), any(), any(), any(), any(), any()) } returns
            testContentState.copy(
                qualities = listOf(
                    QualityUIState(0, "A", 1, 640, 360),
                    QualityUIState(1, "B", 2, 1280, 720),
                    QualityUIState(2, "C", 3, 1920, 1080),
                ),
            )
        every { interactor.selectStreamUrl(any(), any()) } answers {
            "https://test/${secondArg<Int>()}.m3u8"
        }
        val vm = startedVM()
        val sessionA = controlledPlayback.currentSession

        vm.onAction(PlayerAction.SelectQuality(1))
        val sessionB = controlledPlayback.currentSession
        vm.onAction(PlayerAction.SelectQuality(2))
        val sessionC = controlledPlayback.currentSession
        controlledPlayback.deliverPlaybackState(
            sessionC,
            PlaybackSnapshot(
                isPlaying = true,
                intent = PlaybackIntent.PlayRequested,
                shouldKeepScreenOn = true,
                shouldResumeAfterStreamSwitch = true,
                isBuffering = false,
                position = 30_000L,
                duration = 2_400_000L,
                buffered = 90_000L,
            ),
        )
        val finalStreamState = contentState(vm)
        assertEquals(2, finalStreamState.selectedQualityIndex)
        assertEquals(30_000L, finalStreamState.currentPosition)

        repeat(POST_C_STABILITY_DELIVERIES) {
            controlledPlayback.deliverError(sessionA, "Stale A error")
            controlledPlayback.deliverPlaybackState(
                sessionB,
                PlaybackSnapshot(
                    isPlaying = false,
                    intent = PlaybackIntent.Paused,
                    shouldKeepScreenOn = false,
                    shouldResumeAfterStreamSwitch = false,
                    isBuffering = true,
                    position = 5_000L,
                    duration = 2_400_000L,
                    buffered = 10_000L,
                ),
            )
        }

        assertEquals(finalStreamState, contentState(vm))
        assertEquals(listOf("state"), controlledPlayback.deliveredEvents)
    }

    private class ControlledPlaybackControl : PlaybackControl {
        private val callbackGate = PlaybackCallbackGate()
        var currentSession = callbackGate.beginSession()
            private set
        val deliveredErrors = mutableListOf<String>()
        val deliveredEvents = mutableListOf<String>()

        override val currentPosition: Long = 0L
        override val duration: Long = 2_400_000L
        override val isPlaying: Boolean = true
        override val playbackIntent: PlaybackIntent = PlaybackIntent.PlayRequested
        override val shouldKeepScreenOn: Boolean = true
        override val bufferedPosition: Long = 0L

        override fun setCallback(callback: PlaybackControl.Callback) {
            callbackGate.setCallback(callback)
        }

        override fun prepare(
            streamUrl: String,
            subtitles: List<SubtitleLink>?,
            startPosition: Long?,
            bufferPreset: BufferPreset,
            fastDns: Boolean,
        ) {
            currentSession = callbackGate.beginSession()
        }

        override fun switchStream(streamUrl: String, subtitles: List<SubtitleLink>?) {
            currentSession = callbackGate.beginSession()
        }

        override fun play() = Unit

        override fun pause() = Unit

        override fun seekTo(positionMs: Long) = Unit

        override fun setSpeed(speed: Float) = Unit

        override fun selectAudioTrack(groupIndex: Int) = Unit

        override fun selectSubtitle(track: SubtitleTrackUIState?) = Unit

        override fun release() {
            callbackGate.invalidate()
        }

        fun deliverError(session: PlaybackCallbackGate.Session, message: String) {
            callbackGate.dispatch(session) { callback ->
                callback?.onError(message)
                if (callback != null) {
                    deliveredErrors += message
                    deliveredEvents += "error"
                }
            }
        }

        fun deliverPlaybackState(
            session: PlaybackCallbackGate.Session,
            snapshot: PlaybackSnapshot,
        ) {
            callbackGate.dispatch(session) { callback ->
                callback?.onPlaybackStateChanged(snapshot)
                if (callback != null) {
                    deliveredEvents += "state"
                }
            }
        }
    }
}
