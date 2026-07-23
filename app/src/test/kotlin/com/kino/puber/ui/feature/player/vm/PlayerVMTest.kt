package com.kino.puber.ui.feature.player.vm

import com.kino.puber.core.content.ContentChangeSet
import com.kino.puber.core.content.ContentChangeType
import com.kino.puber.core.ui.navigation.RESULT_CONTENT_CHANGED
import com.kino.puber.data.api.models.SkipSegment
import com.kino.puber.data.api.models.SkipSegmentType
import com.kino.puber.domain.model.SubtitleSize
import com.kino.puber.ui.feature.player.model.ActivePanel
import com.kino.puber.ui.feature.player.model.AudioTrackUIState
import com.kino.puber.ui.feature.player.model.PlayerAction
import com.kino.puber.ui.feature.player.model.PlayerViewState
import com.kino.puber.ui.feature.player.model.ResumeDialogState
import com.kino.puber.ui.feature.player.model.SkipSegmentUIState
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * PlayerVM tests.
 *
 * No `runTest` — UnconfinedTestDispatcher makes all coroutines synchronous.
 * `runTest` adds `advanceUntilIdle()` at the end which spins PlayerVM's infinite
 * `startPositionUpdates()` loop forever → OOM.
 * Without `runTest`, the infinite loop stays suspended at its first `delay()` — harmless.
 */
internal class PlayerVMTest : PlayerVMTestFixture() {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    // region Lifecycle

    @Test
    fun initialState_isLoading() {
        assertEquals(PlayerViewState.Loading, createVM().testStateValue)
    }

    @Test
    fun onStart_transitionsToContent() {
        val vm = startedVM()
        assertTrue(vm.testStateValue is PlayerViewState.Content)
    }

    @Test
    fun onStart_preparesPlayer() {
        startedVM()
        verify { playbackController.setCallback(any()) }
        verify { playbackController.prepare("https://test/v.m3u8", any(), any()) }
    }

    // endregion

    // region Bug 2: Audio track restore by language

    @Test
    fun tracksUpdated_restoresPreferredLang() {
        every { interactor.getPreferredAudioLang(42) } returns "rus"
        val vm = startedVM()

        callbackSlot.captured.onTracksUpdated(
            listOf(AudioTrackUIState(0, "English", "eng"), AudioTrackUIState(1, "Russian", "rus")),
            selectedIndex = 0,
        )

        verify { playbackController.selectAudioTrack(1) }
        assertEquals(1, contentState(vm).selectedAudioTrackIndex)
    }

    @Test
    fun tracksUpdated_keepsDefault_whenNoSavedPreference() {
        val vm = startedVM()

        callbackSlot.captured.onTracksUpdated(
            listOf(AudioTrackUIState(0, "English", "eng"), AudioTrackUIState(1, "Russian", "rus")),
            selectedIndex = 0,
        )

        verify(exactly = 0) { playbackController.selectAudioTrack(any()) }
        assertEquals(0, contentState(vm).selectedAudioTrackIndex)
    }

    @Test
    fun tracksUpdated_keepsDefault_whenSavedLangNotFound() {
        every { interactor.getPreferredAudioLang(42) } returns "deu"
        val vm = startedVM()

        callbackSlot.captured.onTracksUpdated(
            listOf(AudioTrackUIState(0, "English", "eng"), AudioTrackUIState(1, "Russian", "rus")),
            selectedIndex = 0,
        )

        verify(exactly = 0) { playbackController.selectAudioTrack(any()) }
    }

    @Test
    fun tracksUpdated_restoresOnlyOnce_perEpisode() {
        every { interactor.getPreferredAudioLang(42) } returns "rus"
        val vm = startedVM()

        val tracks = listOf(AudioTrackUIState(0, "English", "eng"), AudioTrackUIState(1, "Russian", "rus"))
        callbackSlot.captured.onTracksUpdated(tracks, 0)
        callbackSlot.captured.onTracksUpdated(tracks, 1)

        verify(exactly = 1) { playbackController.selectAudioTrack(1) }
    }

    @Test
    fun selectTrack_savesLangToPrefs() {
        startedVM().onAction(PlayerAction.SelectAudioTrack(1))
        verify { interactor.saveTrackPreferences(42, "rus", any(), any(), any()) }
    }

    // endregion

    // region Bug 3: Countdown

    @Test
    fun cancelCountdown_setsNull() {
        val vm = startedVM()
        vm.onAction(PlayerAction.CancelNextEpisodeCountdown)
        assertNull(contentState(vm).nextEpisodeCountdown)
    }

    @Test
    fun playbackEnded_startsCountdown_forSeries() {
        val vm = startedVM()
        callbackSlot.captured.onPlaybackEnded()
        assertEquals(15, contentState(vm).nextEpisodeCountdown)
    }

    // endregion

    // region Play/Pause

    @Test
    fun togglePause_whenPlaying() {
        every { playbackController.isPlaying } returns true
        startedVM().onAction(PlayerAction.TogglePlayPause)
        verify { playbackController.pause() }
    }

    @Test
    fun togglePlay_whenPaused() {
        every { playbackController.isPlaying } returns false
        startedVM().onAction(PlayerAction.TogglePlayPause)
        verify { playbackController.play() }
    }

    // endregion

    // region Panels

    @Test
    fun openAudioPanel_setsActivePanel() {
        val vm = startedVM()
        vm.onAction(PlayerAction.OpenAudioSubtitlesPanel)
        assertEquals(ActivePanel.AudioSubtitles, contentState(vm).activePanel)
    }

    @Test
    fun closePanel_resetsToNone() {
        val vm = startedVM()
        vm.onAction(PlayerAction.OpenAudioSubtitlesPanel)
        vm.onAction(PlayerAction.ClosePanel)
        assertEquals(ActivePanel.None, contentState(vm).activePanel)
    }

    // endregion

    // region Error

    @Test
    fun onError_setsErrorState() {
        val vm = startedVM()
        callbackSlot.captured.onError("Network error")
        assertTrue(vm.testStateValue is PlayerViewState.Error)
        assertEquals("Network error", (vm.testStateValue as PlayerViewState.Error).message)
    }

    // endregion

    // region Back navigation

    @Test
    fun backPressed_cancelsCountdown_whenActive() {
        val vm = startedVM()
        callbackSlot.captured.onPlaybackEnded()
        assertNotNull(contentState(vm).nextEpisodeCountdown)

        vm.onAction(PlayerAction.OnBackPressed)
        assertNull(contentState(vm).nextEpisodeCountdown)
    }

    @Test
    fun backPressed_beforeContent_consumesResultListenerWithEmptyChanges() {
        createVM().onAction(PlayerAction.OnBackPressed)

        verifyEmptyContentChangeResult()
    }

    @Test
    fun backPressed_afterProgressSave_returnsPlaybackProgressResult() {
        val vm = startedVM()

        vm.onAction(PlayerAction.OnBackPressed)

        verifyContentChangeResult(ContentChangeType.PlaybackProgress)
    }

    @Test
    fun backPressed_waitsForFinalProgressSave() {
        val releaseSave = CompletableDeferred<Unit>()
        coEvery { interactor.saveWatchingTime(42, 1, 0, 1) } coAnswers {
            releaseSave.await()
        }
        val vm = startedVM()

        vm.onAction(PlayerAction.OnBackPressed)

        verify(exactly = 0) { router.back(any(), any()) }
        releaseSave.complete(Unit)
        verifyContentChangeResult(ContentChangeType.PlaybackProgress)
    }

    @Test
    fun repeatedBackWhileFinalProgressSavePending_staysInterceptedAndReturnsOnce() {
        val releaseSave = CompletableDeferred<Unit>()
        coEvery { interactor.saveWatchingTime(42, 1, 0, 1) } coAnswers {
            releaseSave.await()
        }
        val vm = startedVM()

        vm.onBackPressed()
        vm.onBackPressed()

        verify(exactly = 2) { router.addBackDispatcher(vm) }
        verify(exactly = 0) { router.back(any(), any()) }
        releaseSave.complete(Unit)
        verify(exactly = 1) {
            router.back(
                RESULT_CONTENT_CHANGED,
                match { result ->
                    val changes = result as? ContentChangeSet ?: return@match false
                    changes.changes[42] == setOf(ContentChangeType.PlaybackProgress)
                },
            )
        }
    }

    @Test
    fun failedFinalProgressSave_returnsEmptyChanges() {
        coEvery { interactor.saveWatchingTime(42, 1, 0, 1) } throws IllegalStateException("save failed")
        val vm = startedVM()

        vm.onAction(PlayerAction.OnBackPressed)

        verifyEmptyContentChangeResult()
    }

    // endregion

    // region Episode switching

    @Test
    fun switchEpisode_releasesPlayer() {
        startedVM().onAction(PlayerAction.SelectEpisode(1, 2))
        verify { playbackController.release() }
    }

    @Test
    fun switchEpisode_resetsTracksRestoredFlag() {
        // After episode switch, track restoration should run again for the new episode.
        // Regression: without reset, tracksRestoredForCurrentMedia stays true → tracks not restored.
        every { interactor.getPreferredAudioLang(42) } returns "rus"
        val vm = startedVM()

        // First episode: tracks restored
        val tracks = listOf(AudioTrackUIState(0, "English", "eng"), AudioTrackUIState(1, "Russian", "rus"))
        callbackSlot.captured.onTracksUpdated(tracks, 0)
        verify(exactly = 1) { playbackController.selectAudioTrack(1) }

        // Switch episode → flag should reset
        vm.onAction(PlayerAction.SelectEpisode(1, 2))

        // Second episode: tracks restored again
        callbackSlot.captured.onTracksUpdated(tracks, 0)
        verify(exactly = 2) { playbackController.selectAudioTrack(1) }
    }

    @Test
    fun switchEpisode_resetsCountdownDismissedFlag() {
        // After episode switch, user should see next-episode countdown again.
        // Regression: without reset, countdownDismissed stays true → countdown never shown.
        val vm = startedVM()

        // Dismiss countdown on current episode
        callbackSlot.captured.onPlaybackEnded()
        vm.onAction(PlayerAction.CancelNextEpisodeCountdown)
        assertNull(contentState(vm).nextEpisodeCountdown)

        // Switch episode — re-triggers preparePlayback which re-calls setCallback
        vm.onAction(PlayerAction.SelectEpisode(1, 2))
        // After switch, preparePlayback runs → Content state restored
        assertTrue(vm.testStateValue is PlayerViewState.Content)

        // New episode: playback ends → countdown should start again (dismissed flag was reset)
        callbackSlot.captured.onPlaybackEnded()
        assertNotNull(contentState(vm).nextEpisodeCountdown)
    }

    @Test
    fun stalePrepareCompletion_cannotOverwriteNewEpisode() {
        val releaseFirstLoad = CompletableDeferred<Unit>()
        var detailsCalls = 0
        coEvery { interactor.getItemDetails(42) } coAnswers {
            detailsCalls += 1
            if (detailsCalls == 1) {
                releaseFirstLoad.await()
            }
            testItem
        }
        val nextEpisode = testResolvedMedia.copy(
            videoNumber = 2,
            episodeId = 102,
            episodeNumber = 2,
        )
        coEvery { interactor.resolveMedia(any(), any(), any()) } returns nextEpisode
        coEvery { contentStateFactory.build(any(), any(), any(), any(), any(), any()) } returns
            testContentState.copy(currentEpisodeId = 102)
        val vm = startedVM()

        vm.onAction(PlayerAction.SelectEpisode(1, 2))
        assertEquals(102, contentState(vm).currentEpisodeId)

        releaseFirstLoad.complete(Unit)

        assertEquals(102, contentState(vm).currentEpisodeId)
        verify(exactly = 1) { playbackController.prepare(any(), any(), any()) }
    }

    @Test
    fun staleSkipSegmentsCompletion_cannotAffectNewEpisode() {
        val releaseFirstSegments = CompletableDeferred<Unit>()
        val staleSegments = listOf(SkipSegment(SkipSegmentType.INTRO, startMs = 0, endMs = 10_000))
        var segmentLoads = 0
        coEvery { skipSegmentInteractor.loadSegments(any(), any(), any()) } coAnswers {
            segmentLoads += 1
            if (segmentLoads == 1) {
                withContext(NonCancellable) {
                    releaseFirstSegments.await()
                }
                staleSegments
            } else {
                emptyList()
            }
        }
        coEvery { interactor.resolveMedia(any(), any(), any()) } returns testResolvedMedia andThen
            testResolvedMedia.copy(videoNumber = 2, episodeId = 102, episodeNumber = 2)
        val vm = startedVM()

        vm.onAction(PlayerAction.SelectEpisode(1, 2))
        releaseFirstSegments.complete(Unit)

        verify(exactly = 0) { skipSegmentInteractor.findCreditsSegment(staleSegments) }
    }

    // endregion

    // region Movie-specific behavior

    @Test
    fun playbackEnded_doesNotStartCountdown_forMovies() {
        // Movie content should never show next-episode countdown.
        coEvery { contentStateFactory.build(any(), any(), any(), any(), any(), any()) } returns testContentState.copy(
            isMovie = true,
            hasNextEpisode = false,
        )
        val vm = startedVM()

        callbackSlot.captured.onPlaybackEnded()

        assertNull(contentState(vm).nextEpisodeCountdown)
    }

    // endregion

    // region Background / Resume

    @Test
    fun pauseForBackground_pausesAndSavesPosition() {
        every { playbackController.isPlaying } returns true
        val vm = startedVM()

        vm.onAction(PlayerAction.OnBackground)

        verify { playbackController.pause() }
        verify(exactly = 0) { router.back(any(), any()) }
        assertEquals(false, contentState(vm).isPlaying)
    }

    @Test
    fun pauseForBackground_savesPosition_whenAlreadyPaused() {
        every { playbackController.isPlaying } returns false
        startedVM().onAction(PlayerAction.OnBackground)

        verify(exactly = 0) { playbackController.pause() }
        coVerify(exactly = 1) { interactor.saveWatchingTime(42, 1, 0, 1) }
    }

    @Test
    fun retryPlayback_transitionsToLoadingAndReloads() {
        val vm = startedVM()

        // Force error state
        callbackSlot.captured.onError("Error")
        assertTrue(vm.testStateValue is PlayerViewState.Error)

        vm.onAction(PlayerAction.RetryPlayback)

        verify { playbackController.release() }
        // After retry, preparePlayback runs again → Content
        assertTrue(vm.testStateValue is PlayerViewState.Content)
    }

    // endregion

    // region Seek

    @Test
    fun seekForward_updatesCurrentPosition() {
        val vm = startedVM()
        vm.onAction(PlayerAction.SeekForward)
        assertTrue(contentState(vm).currentPosition > 0)
    }

    @Test
    fun seekBackward_updatesCurrentPosition() {
        every { playbackController.currentPosition } returns 30_000L
        val vm = startedVM()
        vm.onAction(PlayerAction.SeekBackward)
        assertTrue(contentState(vm).currentPosition < 30_000L)
    }

    // endregion

    // region Race condition

    @Test
    fun nextEpisode_cancelsCountdown_and_switches() {
        every { interactor.findNextEpisode(any(), any(), any()) } returns (1 to 2)
        val vm = startedVM()

        // Playback ends → starts countdown
        callbackSlot.captured.onPlaybackEnded()
        assertNotNull(contentState(vm).nextEpisodeCountdown)

        // User manually triggers next episode during countdown
        vm.onAction(PlayerAction.NextEpisode)

        verify { playbackController.release() }
        assertTrue(vm.testStateValue is PlayerViewState.Content)
    }

    @Test
    fun nextEpisode_doesNothing_whenNoNextEpisode() {
        val vm = startedVM()
        vm.onAction(PlayerAction.NextEpisode)
        verify(exactly = 0) { playbackController.release() }
    }

    // endregion

    // region Previous episode

    @Test
    fun previousEpisode_switchesEpisode() {
        every { interactor.findPreviousEpisode(any(), any(), any()) } returns (1 to 1)
        val vm = startedVM()

        vm.onAction(PlayerAction.PreviousEpisode)

        verify { playbackController.release() }
    }

    @Test
    fun previousEpisode_doesNothing_whenNoPrevious() {
        val vm = startedVM()

        vm.onAction(PlayerAction.PreviousEpisode)

        verify(exactly = 0) { playbackController.release() }
    }

    // endregion

    // region Resume dialog

    @Test
    fun resumeFromPosition_seeksToSavedPosition_clearsDialog() {
        coEvery { contentStateFactory.build(any(), any(), any(), any(), any(), any()) } returns testContentState.copy(
            resumeDialog = ResumeDialogState(savedPosition = 120_000L, formattedTime = "2:00", episodeInfo = null),
            isPlaying = false,
        )
        val vm = startedVM()

        vm.onAction(PlayerAction.ResumeFromPosition)

        verify { playbackController.seekTo(120_000L) }
        verify { playbackController.play() }
        assertNull(contentState(vm).resumeDialog)
        assertTrue(contentState(vm).isPlaying)
    }

    @Test
    fun startFromBeginning_seeksToZero_clearsDialog() {
        coEvery { contentStateFactory.build(any(), any(), any(), any(), any(), any()) } returns testContentState.copy(
            resumeDialog = ResumeDialogState(savedPosition = 120_000L, formattedTime = "2:00", episodeInfo = null),
            isPlaying = false,
        )
        val vm = startedVM()

        vm.onAction(PlayerAction.StartFromBeginning)

        verify { playbackController.seekTo(0) }
        verify { playbackController.play() }
        assertNull(contentState(vm).resumeDialog)
    }

    // endregion

    // region Subtitle selection

    @Test
    fun selectSubtitle_updatesStateAndDelegates() {
        val vm = startedVM()
        vm.onAction(PlayerAction.SelectSubtitle(1))
        assertEquals(1, contentState(vm).selectedSubtitleIndex)
        verify { playbackController.selectSubtitle(testSubtitleTracks[1]) }
    }

    @Test
    fun selectSubtitleOff_disablesSubtitleTrack() {
        val vm = startedVM()
        vm.onAction(PlayerAction.SelectSubtitle(0))
        assertEquals(0, contentState(vm).selectedSubtitleIndex)
        verify { playbackController.selectSubtitle(testSubtitleTracks[0]) }
    }

    @Test
    fun tracksUpdated_restoresPreferredSubtitleByUrl_beforeLanguage() {
        every { interactor.getPreferredSubtitleLang(42) } returns "rus"
        every { interactor.getPreferredSubtitleUrl(42) } returns "https://test/subtitles/rus-forced.vtt"
        val vm = startedVM()

        val tracks = listOf(AudioTrackUIState(0, "English", "eng"), AudioTrackUIState(1, "Russian", "rus"))
        callbackSlot.captured.onTracksUpdated(tracks, 0)

        verify { playbackController.selectSubtitle(testSubtitleTracks[2]) }
        assertEquals(2, contentState(vm).selectedSubtitleIndex)
    }

    @Test
    fun tracksUpdated_restoresPreferredSubtitleByStableUrl_whenSignedUrlChanges() {
        every { interactor.getPreferredSubtitleLang(42) } returns "rus"
        every { interactor.getPreferredSubtitleUrl(42) } returns
                "https://old-cdn.example/pd/expired-token/subtitles/rus-forced.vtt?e=1"
        val vm = startedVM()

        val tracks = listOf(AudioTrackUIState(0, "English", "eng"), AudioTrackUIState(1, "Russian", "rus"))
        callbackSlot.captured.onTracksUpdated(tracks, 0)

        verify { playbackController.selectSubtitle(testSubtitleTracks[2]) }
        assertEquals(2, contentState(vm).selectedSubtitleIndex)
    }

    @Test
    fun tracksUpdated_doesNotRestoreAmbiguousSubtitleLanguage_whenUrlIsMissing() {
        every { interactor.getPreferredSubtitleLang(42) } returns "rus"
        every { interactor.getPreferredSubtitleUrl(42) } returns null
        val vm = startedVM()

        val tracks = listOf(AudioTrackUIState(0, "English", "eng"), AudioTrackUIState(1, "Russian", "rus"))
        callbackSlot.captured.onTracksUpdated(tracks, 0)

        verify(exactly = 0) { playbackController.selectSubtitle(any()) }
        assertEquals(0, contentState(vm).selectedSubtitleIndex)
    }

    @Test
    fun tracksUpdated_restoreDoesNotRewritePreferencesFromIntermediateState() {
        every { interactor.getPreferredAudioLang(42) } returns "rus"
        every { interactor.getPreferredSubtitleLang(42) } returns "rus"
        every { interactor.getPreferredSubtitleUrl(42) } returns "https://test/subtitles/rus-forced.vtt"
        val vm = startedVM()

        val tracks = listOf(AudioTrackUIState(0, "English", "eng"), AudioTrackUIState(1, "Russian", "rus"))
        callbackSlot.captured.onTracksUpdated(tracks, 0)

        verify { playbackController.selectAudioTrack(1) }
        verify { playbackController.selectSubtitle(testSubtitleTracks[2]) }
        verify(exactly = 0) { interactor.saveTrackPreferences(any(), any(), any(), any(), any()) }
        assertEquals(1, contentState(vm).selectedAudioTrackIndex)
        assertEquals(2, contentState(vm).selectedSubtitleIndex)
    }

    // endregion

    // region Subtitle size

    @Test
    fun cycleSubtitleSize_cyclesThrough() {
        coEvery { contentStateFactory.build(any(), any(), any(), any(), any(), any()) } returns testContentState.copy(
            subtitleSize = SubtitleSize.SMALL,
        )
        val vm = startedVM()

        vm.onAction(PlayerAction.CycleSubtitleSize)
        assertEquals(SubtitleSize.MEDIUM, contentState(vm).subtitleSize)

        vm.onAction(PlayerAction.CycleSubtitleSize)
        assertEquals(SubtitleSize.LARGE, contentState(vm).subtitleSize)

        vm.onAction(PlayerAction.CycleSubtitleSize)
        assertEquals(SubtitleSize.SMALL, contentState(vm).subtitleSize)

        verify(exactly = 3) { interactor.saveSubtitleSize(any()) }
    }

    // endregion

    // region Quality

    @Test
    fun selectQuality_switchesStreamUrl() {
        val vm = startedVM()
        vm.onAction(PlayerAction.SelectQuality(1))
        assertEquals(1, contentState(vm).selectedQualityIndex)
        verify { playbackController.switchStream(any(), any()) }
    }

    @Test
    fun selectQuality_doesNothing_whenSameIndex() {
        val vm = startedVM()
        vm.onAction(PlayerAction.SelectQuality(0))
        verify(exactly = 0) { playbackController.switchStream(any(), any()) }
    }

    // endregion

    // region Skip segments

    @Test
    fun skipSegmentClicked_seeksToTarget() {
        coEvery { contentStateFactory.build(any(), any(), any(), any(), any(), any()) } returns testContentState.copy(
            activeSkipSegment = SkipSegmentUIState("Skip Intro", 30_000L, SkipSegmentType.INTRO, 5),
        )
        val vm = startedVM()

        vm.onAction(PlayerAction.SkipSegmentClicked)

        verify { playbackController.seekTo(30_000L) }
        assertNull(contentState(vm).activeSkipSegment)
    }

    @Test
    fun cancelSkipSegment_clearsOverlay() {
        coEvery { contentStateFactory.build(any(), any(), any(), any(), any(), any()) } returns testContentState.copy(
            activeSkipSegment = SkipSegmentUIState("Skip Intro", 30_000L, SkipSegmentType.INTRO, 5),
        )
        val vm = startedVM()

        vm.onAction(PlayerAction.CancelSkipSegment)

        assertNull(contentState(vm).activeSkipSegment)
    }

    // endregion

    // region Episodes panel

    @Test
    fun openEpisodesPanel_setsActivePanel() {
        val vm = startedVM()
        vm.onAction(PlayerAction.OpenEpisodesPanel)
        assertEquals(ActivePanel.Episodes, contentState(vm).activePanel)
    }

    @Test
    fun openVideoSettingsPanel_setsActivePanel() {
        val vm = startedVM()
        vm.onAction(PlayerAction.OpenVideoSettingsPanel)
        assertEquals(ActivePanel.VideoSettings, contentState(vm).activePanel)
    }

    // endregion

}
