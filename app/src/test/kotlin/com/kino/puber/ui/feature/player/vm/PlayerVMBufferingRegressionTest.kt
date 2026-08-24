package com.kino.puber.ui.feature.player.vm

import androidx.media3.common.Player
import com.kino.puber.data.api.models.SkipSegmentType
import com.kino.puber.data.api.models.SkipSegment
import com.kino.puber.ui.feature.player.model.PlayerViewState
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

internal class PlayerVMBufferingRegressionTest : PlayerVMTestFixture() {

    companion object {
        private const val THRESHOLD_POSITION_MS = 1_000L

        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    @BeforeEach
    fun setFrozenPlayback() {
        every { playbackController.isPlaying } returns false
    }

    @Test
    fun stationaryBuffering_nearWatchedThreshold_doesNotAutoMarkWatched_afterPositionTick() {
        loadThresholdSegments()
        val vm = startBufferingAt(positionMs = 2_370_000L)

        advanceBeyondBufferingPositionTick()

        assertFrozenBufferingState(vm, expectedPositionMs = 2_370_000L)
        coVerify(exactly = 0) { interactor.markCurrentAsWatched(any(), any(), any()) }
    }

    @Test
    fun stationaryBuffering_atLoadedCreditsThreshold_doesNotStartEarlyNextCountdown_afterPositionTick() {
        loadThresholdSegments()
        val vm = startBufferingAt(positionMs = THRESHOLD_POSITION_MS)

        advanceBeyondBufferingPositionTick()

        assertFrozenBufferingState(vm, expectedPositionMs = THRESHOLD_POSITION_MS)
        assertNull(contentState(vm).nextEpisodeCountdown)
    }

    @Test
    fun lateCreditsResponse_whileBufferingAtThreshold_doesNotStartEarlyNextCountdown() {
        val releaseSegments = CompletableDeferred<Unit>()
        val credits = deferredCreditsSegment(releaseSegments)
        val vm = startBufferingAt(positionMs = THRESHOLD_POSITION_MS)

        releaseSegments.complete(Unit)
        advanceBeyondBufferingPositionTick()

        assertFrozenBufferingState(vm, expectedPositionMs = THRESHOLD_POSITION_MS)
        assertNull(contentState(vm).nextEpisodeCountdown)
        verify { skipSegmentInteractor.findCreditsSegment(listOf(credits)) }
    }

    @Test
    fun lateCreditsResponse_whileActuallyPlayingAtThreshold_startsEarlyNextCountdown() {
        val releaseSegments = CompletableDeferred<Unit>()
        deferredCreditsSegment(releaseSegments)
        every { playbackController.isPlaying } returns true
        every { playbackController.currentPosition } returns THRESHOLD_POSITION_MS
        val vm = startedVM()
        callbackSlot.captured.onPlaybackStateChanged(
            PlaybackStatePolicy.derive(
                isPlaying = true,
                playWhenReady = true,
                playbackState = Player.STATE_READY,
                suppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_NONE,
                position = THRESHOLD_POSITION_MS,
                duration = 2_400_000L,
                buffered = 2_400_000L,
            ),
        )

        releaseSegments.complete(Unit)
        mainDispatcher.dispatcher.scheduler.advanceTimeBy(1L)

        assertEquals(15, contentState(vm).nextEpisodeCountdown)
    }

    @Test
    fun stationaryBuffering_insideLoadedSkipSegment_doesNotStartSkipCountdown_afterPositionTick() {
        val segments = loadThresholdSegments()
        val vm = startBufferingAt(positionMs = THRESHOLD_POSITION_MS)

        advanceBeyondBufferingPositionTick()

        assertFrozenBufferingState(vm, expectedPositionMs = THRESHOLD_POSITION_MS)
        assertNull(contentState(vm).activeSkipSegment)
        verify(exactly = 0) {
            skipSegmentInteractor.findActiveSegment(segments, THRESHOLD_POSITION_MS)
        }
    }

    private fun loadThresholdSegments(): List<SkipSegment> {
        val intro = SkipSegment(
            type = SkipSegmentType.INTRO,
            startMs = THRESHOLD_POSITION_MS - 500L,
            endMs = THRESHOLD_POSITION_MS + 500L,
        )
        val credits = SkipSegment(
            type = SkipSegmentType.CREDITS,
            startMs = THRESHOLD_POSITION_MS,
            endMs = null,
        )
        val segments = listOf(intro, credits)
        coEvery { skipSegmentInteractor.loadSegments(any(), any(), any()) } returns segments
        every { skipSegmentInteractor.findCreditsSegment(segments) } returns credits
        every {
            skipSegmentInteractor.findActiveSegment(segments, THRESHOLD_POSITION_MS)
        } returns intro
        return segments
    }

    private fun deferredCreditsSegment(releaseSegments: CompletableDeferred<Unit>): SkipSegment {
        val credits = SkipSegment(
            type = SkipSegmentType.CREDITS,
            startMs = THRESHOLD_POSITION_MS,
            endMs = null,
        )
        coEvery { skipSegmentInteractor.loadSegments(any(), any(), any()) } coAnswers {
            releaseSegments.await()
            listOf(credits)
        }
        every { skipSegmentInteractor.findCreditsSegment(listOf(credits)) } returns credits
        return credits
    }

    private fun startBufferingAt(positionMs: Long): PlayerVM {
        val vm = startedVM()
        every { playbackController.duration } returns 2_400_000L
        every { playbackController.currentPosition } returns positionMs
        dispatchBufferingSnapshot(
            playWhenReady = true,
            positionMs = 0L,
        )
        return vm
    }

    private fun dispatchBufferingSnapshot(
        playWhenReady: Boolean,
        positionMs: Long = THRESHOLD_POSITION_MS,
    ) {
        callbackSlot.captured.onPlaybackStateChanged(
            PlaybackStatePolicy.derive(
                isPlaying = false,
                playWhenReady = playWhenReady,
                playbackState = Player.STATE_BUFFERING,
                suppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_NONE,
                position = positionMs,
                duration = 2_400_000L,
                buffered = 2_400_000L,
            ),
        )
    }

    private fun advanceBeyondBufferingPositionTick() {
        mainDispatcher.dispatcher.scheduler.advanceTimeBy(1_301L)
    }

    private fun assertFrozenBufferingState(vm: PlayerVM, expectedPositionMs: Long) {
        val content = (vm.testStateValue as PlayerViewState.Content).content
        assertFalse(content.isPlaying)
        assertTrue(content.isBuffering)
        assertEquals(expectedPositionMs, content.currentPosition)
        assertEquals(PlaybackIntent.PlayRequested, contentState(vm).playbackIntent)
        assertTrue(contentState(vm).shouldKeepScreenOn)
    }

    @Test
    fun bufferingPauseAndPlay_useExplicitIntent_notActualProgression() {
        every { playbackController.playbackIntent } returns PlaybackIntent.PlayRequested

        val vm = startedVM()
        dispatchBufferingSnapshot(playWhenReady = true)
        advanceBeyondBufferingPositionTick()

        assertFalse(contentState(vm).isPlaying)
        assertTrue(contentState(vm).isBuffering)
        assertEquals(PlaybackIntent.PlayRequested, contentState(vm).playbackIntent)

        vm.onAction(com.kino.puber.ui.feature.player.model.PlayerAction.TogglePlayPause)

        verify { playbackController.pause() }
        assertFalse(contentState(vm).isPlaying)
        assertEquals(PlaybackIntent.Paused, contentState(vm).playbackIntent)
    }

    @Test
    fun bufferingPauseAndPlay_resumes_whenIntentIsPaused() {
        every { playbackController.playbackIntent } returns PlaybackIntent.Paused

        val vm = startedVM()
        dispatchBufferingSnapshot(playWhenReady = false)
        advanceBeyondBufferingPositionTick()

        assertFalse(contentState(vm).isPlaying)
        assertTrue(contentState(vm).isBuffering)
        assertEquals(PlaybackIntent.Paused, contentState(vm).playbackIntent)

        vm.onAction(com.kino.puber.ui.feature.player.model.PlayerAction.TogglePlayPause)

        verify { playbackController.play() }
        assertFalse(contentState(vm).isPlaying)
        assertEquals(PlaybackIntent.PlayRequested, contentState(vm).playbackIntent)
    }

    @Test
    fun playbackSnapshots_publishRequiredKeepScreenOnMatrixIntoContentState() {
        val vm = startedVM()
        listOf(
            SnapshotCase(
                label = "ready",
                playbackState = Player.STATE_READY,
                playWhenReady = true,
                suppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_NONE,
                expectedIntent = PlaybackIntent.PlayRequested,
                expectedKeepScreenOn = true,
            ),
            SnapshotCase(
                label = "buffering",
                playbackState = Player.STATE_BUFFERING,
                playWhenReady = true,
                suppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_NONE,
                expectedIntent = PlaybackIntent.PlayRequested,
                expectedKeepScreenOn = true,
            ),
            SnapshotCase(
                label = "paused",
                playbackState = Player.STATE_READY,
                playWhenReady = false,
                suppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_NONE,
                expectedIntent = PlaybackIntent.Paused,
                expectedKeepScreenOn = false,
            ),
            SnapshotCase(
                label = "suppressed",
                playbackState = Player.STATE_READY,
                playWhenReady = true,
                suppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS,
                expectedIntent = PlaybackIntent.PlayRequested,
                expectedKeepScreenOn = false,
            ),
        ).forEach { testCase ->
            callbackSlot.captured.onPlaybackStateChanged(
                PlaybackStatePolicy.derive(
                    isPlaying = testCase.playbackState == Player.STATE_READY &&
                        testCase.playWhenReady &&
                        testCase.suppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE,
                    playWhenReady = testCase.playWhenReady,
                    playbackState = testCase.playbackState,
                    suppressionReason = testCase.suppressionReason,
                ),
            )

            assertEquals(testCase.expectedIntent, contentState(vm).playbackIntent, testCase.label)
            assertEquals(
                testCase.expectedKeepScreenOn,
                contentState(vm).shouldKeepScreenOn,
                testCase.label,
            )
        }
    }

    private data class SnapshotCase(
        val label: String,
        val playbackState: Int,
        val playWhenReady: Boolean,
        val suppressionReason: Int,
        val expectedIntent: PlaybackIntent,
        val expectedKeepScreenOn: Boolean,
    )
}
