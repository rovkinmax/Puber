package com.kino.puber.ui.feature.player.vm

import com.kino.puber.R
import com.kino.puber.core.content.ContentChangeSet
import com.kino.puber.core.content.ContentChangeType
import com.kino.puber.core.error.DefaultErrorHandler
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.RESULT_CONTENT_CHANGED
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.SkipSegment
import com.kino.puber.data.api.models.SkipSegmentType
import com.kino.puber.domain.model.SubtitleSize
import com.kino.puber.ui.ScreensImpl
import com.kino.puber.ui.feature.player.model.ActivePanel
import com.kino.puber.ui.feature.player.model.AudioTrackUIState
import com.kino.puber.ui.feature.player.model.PlayerAction
import com.kino.puber.ui.feature.player.model.PlayerScreenParams
import com.kino.puber.ui.feature.player.model.PlayerViewState
import com.kino.puber.ui.feature.player.model.SkipSegmentUIState
import com.kino.puber.util.FakeResourceProvider
import com.kino.puber.util.MainDispatcherExtension
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import timber.log.Timber

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

    @Test
    fun onStart_urlBearingItemDetailsFailurePreservesErrorAndSanitizesTimberPipeline() {
        val privateItemId = 424_242
        val privateUrl = "https://api.example.test/v1/items/$privateItemId"
        val timeout = HttpRequestTimeoutException(privateUrl, 5_000L, null)
        val failure = IllegalStateException("Player startup failed", timeout)
        val logTree = CollectingLogTree()
        coEvery { interactor.getItemDetails(privateItemId) } throws failure

        Timber.plant(logTree)
        val vm = try {
            createVM(
                playerParams = PlayerScreenParams(itemId = privateItemId, videoNumber = 1),
                playerErrorHandler = DefaultErrorHandler(FakeResourceProvider()),
            ).also(PlayerVM::testOnStart)
        } finally {
            Timber.uproot(logTree)
        }

        assertEquals(
            "string_${R.string.error_generic}",
            (vm.testStateValue as PlayerViewState.Error).message,
        )
        val output = logTree.output()
        assertFalse(output.contains(privateItemId.toString()), output)
        assertFalse(output.contains(privateUrl), output)
        assertTrue(output.contains("/items/<redacted>"), output)
        assertEquals(1, logTree.entryCount)
        verify(exactly = 0) { contentStateFactory.build(any(), any(), any(), any(), any(), any()) }
        verify(exactly = 0) { playbackController.prepare(any(), any(), any()) }
    }

    @Test
    fun onStart_passesExplicitMovieVideoNumberToResolver() {
        val movieParams = PlayerScreenParams(itemId = 42, videoNumber = 7)
        val movieItem = Item(id = 42, title = "Movie", type = ItemType.MOVIE)
        val movieMedia = testResolvedMedia.copy(
            videoNumber = 7,
            episodeId = null,
            episodeTitle = null,
            isSeries = false,
            hasNext = false,
            hasPrevious = false,
            seasonNumber = null,
            episodeNumber = null,
        )
        coEvery { interactor.getItemDetails(42) } returns movieItem
        every { interactor.resolveMedia(movieItem, null, null, 7) } returns movieMedia

        createVM(movieParams).testOnStart()

        verify {
            interactor.resolveMedia(
                item = movieItem,
                seasonNumber = null,
                episodeNumber = null,
                videoNumber = 7,
            )
        }
    }

    @Test
    fun onStart_missingExplicitMovieVideo_showsPlaybackErrorWithoutInitialization() {
        val movieParams = PlayerScreenParams(itemId = 42, videoNumber = 7)
        val movieItem = Item(id = 42, title = "Movie", type = ItemType.MOVIE)
        val missingMedia = testResolvedMedia.copy(
            files = null,
            audios = null,
            subtitles = null,
            videoNumber = null,
            episodeId = null,
            episodeTitle = null,
            isSeries = false,
            hasNext = false,
            hasPrevious = false,
            seasonNumber = null,
            episodeNumber = null,
        )
        coEvery { interactor.getItemDetails(42) } returns movieItem
        every { interactor.resolveMedia(movieItem, null, null, 7) } returns missingMedia

        val vm = createVM(movieParams).also { it.testOnStart() }

        assertEquals(
            "string_${R.string.player_error_playback}",
            (vm.testStateValue as PlayerViewState.Error).message,
        )
        verify(exactly = 0) { contentStateFactory.build(any(), any(), any(), any(), any(), any()) }
        verify(exactly = 0) { playbackController.prepare(any(), any(), any()) }
    }

    @Test
    fun playerScreenKey_includesExplicitMovieVideoNumber() {
        assertEquals(
            "PlayerScreen_42_v7",
            ScreensImpl.player(itemId = 42, videoNumber = 7).key,
        )
    }

    @Test
    fun playerScreenKey_withoutVideoNumber_preservesExistingKey() {
        assertEquals(
            "PlayerScreen_42_s1_e2",
            ScreensImpl.player(itemId = 42, seasonNumber = 1, episodeNumber = 2).key,
        )
    }

    // endregion

    private class CollectingLogTree : Timber.Tree() {
        private val entries = mutableListOf<Pair<String, Throwable?>>()
        val entryCount: Int
            get() = entries.size

        override fun log(
            priority: Int,
            tag: String?,
            message: String,
            t: Throwable?,
        ) {
            entries += message to t
        }

        fun output(): String {
            return entries.joinToString("\n") { (message, throwable) ->
                listOfNotNull(message, throwable?.stackTraceToString()).joinToString("\n")
            }
        }
    }

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
        every { playbackController.playbackIntent } returns PlaybackIntent.PlayRequested
        startedVM().onAction(PlayerAction.TogglePlayPause)
        verify { playbackController.pause() }
    }

    @Test
    fun togglePlay_whenPaused() {
        every { playbackController.playbackIntent } returns PlaybackIntent.Paused
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

    @Test
    fun rebindRouter_routesPanelBackThroughRecreatedFlowRouter() {
        val previousRouter = router
        val recreatedRouter = mockk<AppRouter>(relaxed = true)
        val vm = startedVM()
        vm.onAction(PlayerAction.OpenAudioSubtitlesPanel)

        vm.rebindRouter(recreatedRouter)
        vm.onBackPressed()

        assertEquals(ActivePanel.None, contentState(vm).activePanel)
        verify { previousRouter.removeBackDispatcher(vm) }
        verify { recreatedRouter.addBackDispatcher(vm) }
        verify(exactly = 0) { previousRouter.addBackDispatcher(vm) }
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
        vm.onAction(PlayerAction.OnBackPressed)

        verifyEmptyContentChangeResult()
    }

    @Test
    fun failedFinalProgressSave_emitsIdentityFreeDiagnostic() {
        val privateItemId = 424_242
        val privateTitle = "Private watched title"
        val privateTime = "private-watching-time"
        val failure = IllegalStateException(
            "Failed to save $privateTitle for item=$privateItemId at time=$privateTime",
        )
        coEvery { interactor.saveWatchingTime(42, 1, 0, 1) } throws failure
        val logTree = CollectingLogTree()
        val vm = startedVM()

        Timber.plant(logTree)
        try {
            vm.onAction(PlayerAction.OnBackPressed)
            vm.onAction(PlayerAction.OnBackPressed)
        } finally {
            Timber.uproot(logTree)
        }

        val output = logTree.output()
        assertEquals(1, logTree.entryCount)
        assertTrue(output.contains(PROGRESS_SAVE_FAILURE_DIAGNOSTIC), output)
        assertFalse(output.contains(privateItemId.toString()), output)
        assertFalse(output.contains(privateTitle), output)
        assertFalse(output.contains(privateTime), output)
        assertFalse(output.contains(failure.message.orEmpty()), output)
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
    fun switchEpisode_doesNotReuseInitialMovieVideoNumber() {
        val vm = createVM(PlayerScreenParams(itemId = 42, seasonNumber = 1, episodeNumber = 1, videoNumber = 7))
            .also { it.testOnStart() }

        vm.onAction(PlayerAction.SelectEpisode(1, 2))

        verify {
            interactor.resolveMedia(
                item = testItem,
                seasonNumber = 1,
                episodeNumber = 2,
                videoNumber = null,
            )
        }
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
        every { interactor.resolveMedia(any(), any(), any(), any()) } returns nextEpisode
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
        every { interactor.resolveMedia(any(), any(), any(), any()) } returns testResolvedMedia andThen
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
        every { playbackController.playbackIntent } returns PlaybackIntent.PlayRequested
        val vm = startedVM()

        vm.onAction(PlayerAction.OnBackground)

        verify { playbackController.pause() }
        verify(exactly = 0) { router.back(any(), any()) }
        assertEquals(PlaybackIntent.Paused, contentState(vm).playbackIntent)
    }

    @Test
    fun pauseForBackground_savesPosition_whenAlreadyPaused() {
        every { playbackController.playbackIntent } returns PlaybackIntent.Paused
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
    fun tracksUpdated_addsAndSelectsManifestOnlySubtitle_withLanguagePreference() {
        val vm = startedVM()
        val audioTracks = listOf(AudioTrackUIState(0, "English", "eng"))
        val manifestTrack = testSubtitleTracks.first().copy(
            index = 1,
            label = "Ukrainian HLS",
            language = "uk",
            playerTrackId = "hls-ukrainian",
            playerGroupIndex = 0,
            playerTrackIndex = 0,
        )

        callbackSlot.captured.onTracksUpdated(audioTracks, 0, listOf(manifestTrack))
        vm.onAction(PlayerAction.SelectSubtitle(3))

        val selectedTrack = contentState(vm).subtitleTracks[3]
        assertEquals("uk", selectedTrack.language)
        assertEquals("hls-ukrainian", selectedTrack.playerTrackId)
        verify { playbackController.selectSubtitle(selectedTrack) }
        verify { interactor.saveTrackPreferences(42, "eng", "English", "uk", "hls-ukrainian") }
    }

    @Test
    fun tracksUpdated_defersUrlLessLanguagePreference_untilManifestTracksAppear() {
        every { interactor.getPreferredSubtitleLang(42) } returns "ukr"
        every { interactor.getPreferredSubtitleUrl(42) } returns ""
        val vm = startedVM()
        val audioTracks = listOf(AudioTrackUIState(0, "English", "eng"))
        val manifestTrack = testSubtitleTracks.first().copy(
            index = 1,
            label = "Ukrainian HLS",
            language = "uk",
            playerTrackId = "hls-ukrainian",
            playerGroupIndex = 0,
            playerTrackIndex = 0,
        )

        callbackSlot.captured.onTracksUpdated(audioTracks, 0, emptyList())
        verify(exactly = 0) { playbackController.selectSubtitle(any()) }

        callbackSlot.captured.onTracksUpdated(audioTracks, 0, listOf(manifestTrack))

        val selectedTrack = contentState(vm).subtitleTracks[3]
        assertEquals(3, contentState(vm).selectedSubtitleIndex)
        assertEquals("hls-ukrainian", selectedTrack.playerTrackId)
        verify { playbackController.selectSubtitle(selectedTrack) }
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
