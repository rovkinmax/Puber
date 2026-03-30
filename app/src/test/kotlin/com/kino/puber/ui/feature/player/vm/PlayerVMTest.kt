package com.kino.puber.ui.feature.player.vm

import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.domain.interactor.player.PlayerInteractor
import com.kino.puber.domain.interactor.player.ResolvedMedia
import com.kino.puber.domain.interactor.player.SkipSegmentInteractor
import com.kino.puber.domain.model.SubtitleSize
import com.kino.puber.ui.feature.player.model.ActivePanel
import com.kino.puber.ui.feature.player.model.AudioTrackUIState
import com.kino.puber.ui.feature.player.model.PlayerAction
import com.kino.puber.ui.feature.player.model.PlayerContentState
import com.kino.puber.ui.feature.player.model.PlayerScreenParams
import com.kino.puber.ui.feature.player.model.PlayerUIMapper
import com.kino.puber.ui.feature.player.model.PlayerViewState
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
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
class PlayerVMTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    private lateinit var router: AppRouter
    private lateinit var errorHandler: ErrorHandler
    private lateinit var interactor: PlayerInteractor
    private lateinit var skipSegmentInteractor: SkipSegmentInteractor
    private lateinit var mapper: PlayerUIMapper
    private lateinit var contentStateFactory: ContentStateFactory
    private lateinit var playbackController: PlaybackControl
    private val callbackSlot = slot<PlaybackControl.Callback>()

    private val params = PlayerScreenParams(itemId = 42, seasonNumber = 1, episodeNumber = 1)

    @BeforeEach
    fun setup() {
        router = mockk(relaxed = true)
        errorHandler = mockk { every { proceed(any()) } returns { } }
        interactor = mockk(relaxUnitFun = true)
        skipSegmentInteractor = mockk()
        mapper = mockk(relaxUnitFun = true)
        contentStateFactory = mockk()
        playbackController = mockk(relaxUnitFun = true) {
            every { isPlaying } returns true
            every { currentPosition } returns 0L
            every { duration } returns 2_400_000L
            every { bufferedPosition } returns 0L
        }

        coEvery { interactor.getItemDetails(any()) } returns testItem
        coEvery { interactor.resolveMedia(any(), any(), any()) } returns testResolvedMedia
        coEvery { contentStateFactory.build(any(), any(), any(), any()) } returns testContentState
        every { interactor.selectStreamUrl(any(), any()) } returns "https://test/v.m3u8"
        every { interactor.getPreferredAudioLang(any()) } returns null
        every { interactor.getPreferredSubtitleLang(any()) } returns null
        every { interactor.isDebugOverlayEnabled() } returns false
        every { interactor.getSubtitleSize() } returns SubtitleSize.MEDIUM
        every { interactor.saveTrackPreferences(any(), any(), any()) } returns Unit
        every { interactor.findNextEpisode(any(), any(), any()) } returns null
        coEvery { skipSegmentInteractor.loadSegments(any(), any(), any()) } returns emptyList()
        every { skipSegmentInteractor.findCreditsSegment(any()) } returns null
        every { skipSegmentInteractor.findActiveSegment(any(), any()) } returns null
        every { mapper.formatTime(any()) } returns "00:00"
        every { mapper.formatSeekOffset(any(), any()) } returns "+10s"
        every { mapper.buildTitle(any(), any(), any()) } returns "Title"
        every { mapper.buildSubtitle(any(), any(), any(), any()) } returns "Sub"
        every { mapper.mapSkipSegmentLabel(any()) } returns "Skip"
        every { mapper.defaultSoundModeLabel() } returns "Stereo"
        every { playbackController.setCallback(capture(callbackSlot)) } returns Unit
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    private fun createVM() = PlayerVM(
        router = router, errorHandler = errorHandler, params = params,
        mapper = mapper, interactor = interactor,
        skipSegmentInteractor = skipSegmentInteractor,
        contentStateFactory = contentStateFactory,
        playbackController = playbackController,
    )

    private fun startedVM(): PlayerVM = createVM().also { it.testOnStart() }

    private fun contentState(vm: PlayerVM) = (vm.testStateValue as PlayerViewState.Content).content

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
        verify { interactor.saveTrackPreferences(42, "rus", any()) }
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

    // endregion

    // region Movie-specific behavior

    @Test
    fun playbackEnded_doesNotStartCountdown_forMovies() {
        // Movie content should never show next-episode countdown.
        coEvery { contentStateFactory.build(any(), any(), any(), any()) } returns testContentState.copy(
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
        assertEquals(false, contentState(vm).isPlaying)
    }

    @Test
    fun pauseForBackground_doesNothing_whenAlreadyPaused() {
        every { playbackController.isPlaying } returns false
        startedVM().onAction(PlayerAction.OnBackground)

        verify(exactly = 0) { playbackController.pause() }
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

    private val testItem = Item(id = 42, title = "Breaking Bad", type = ItemType.SERIAL, watched = 5, new = 3)

    private val testResolvedMedia = ResolvedMedia(
        files = emptyList(), audios = emptyList(), subtitles = emptyList(),
        watchingTime = null, duration = 2400, videoNumber = 1, episodeId = 101,
        episodeTitle = "Pilot", isSeries = true, hasNext = true, seasonNumber = 1, episodeNumber = 1,
    )

    private val testContentState = PlayerContentState(
        title = "Breaking Bad", subtitle = "S1E1", isPlaying = true,
        currentPosition = 0L, duration = 2_400_000L, bufferedPosition = 0L,
        controlsVisible = true, controlsFocusTarget = null,
        activePanel = ActivePanel.None, seekIndicator = null, playPauseIndicator = null,
        audioTracks = listOf(AudioTrackUIState(0, "English", "eng"), AudioTrackUIState(1, "Russian", "rus")),
        selectedAudioTrackIndex = 0, subtitleTracks = emptyList(), selectedSubtitleIndex = 0,
        soundModes = emptyList(), selectedSoundModeIndex = 0, subtitleSize = SubtitleSize.MEDIUM,
        qualities = emptyList(), selectedQualityIndex = 0,
        speeds = emptyList(), selectedSpeedIndex = 0,
        aspectRatios = emptyList(), selectedAspectRatioIndex = 0,
        isMovie = false, hasNextEpisode = true, nextEpisodeCountdown = null,
        resumeDialog = null, episodes = null, currentEpisodeId = 101,
    )
}
