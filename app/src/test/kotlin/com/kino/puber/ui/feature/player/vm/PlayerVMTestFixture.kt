package com.kino.puber.ui.feature.player.vm

import com.kino.puber.core.content.ContentChangeSet
import com.kino.puber.core.content.ContentChangeType
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.RESULT_CONTENT_CHANGED
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.data.api.models.Episode
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.Season
import com.kino.puber.domain.interactor.player.PlayerInteractor
import com.kino.puber.domain.interactor.player.ResolvedMedia
import com.kino.puber.domain.interactor.player.SkipSegmentInteractor
import com.kino.puber.domain.model.SubtitleSize
import com.kino.puber.ui.feature.player.model.ActivePanel
import com.kino.puber.ui.feature.player.model.AudioTrackUIState
import com.kino.puber.ui.feature.player.model.BufferPreset
import com.kino.puber.ui.feature.player.model.BufferPresetUIState
import com.kino.puber.ui.feature.player.model.PlayerContentState
import com.kino.puber.ui.feature.player.model.PlayerScreenParams
import com.kino.puber.ui.feature.player.model.PlayerUIMapper
import com.kino.puber.ui.feature.player.model.PlayerViewState
import com.kino.puber.ui.feature.player.model.SubtitleTrackUIState
import com.kino.puber.util.FakeResourceProvider
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

internal abstract class PlayerVMTestFixture {

    protected lateinit var router: AppRouter
    protected lateinit var errorHandler: ErrorHandler
    protected lateinit var interactor: PlayerInteractor
    protected lateinit var skipSegmentInteractor: SkipSegmentInteractor
    protected lateinit var mapper: PlayerUIMapper
    protected lateinit var contentStateFactory: ContentStateFactory
    protected lateinit var playbackController: PlaybackControl
    protected val callbackSlot = slot<PlaybackControl.Callback>()

    private val createdViewModels = mutableListOf<PlayerVM>()
    private val params = PlayerScreenParams(itemId = 42, seasonNumber = 1, episodeNumber = 1)

    @BeforeEach
    fun setupPlayerVMFixture() {
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
        coEvery { contentStateFactory.build(any(), any(), any(), any(), any(), any()) } returns testContentState
        coEvery { interactor.markCurrentAsWatched(any(), any(), any()) } returns
            testItem.withCurrentEpisodeWatched(true)
        every { interactor.selectStreamUrl(any(), any()) } returns "https://test/v.m3u8"
        every { interactor.getPreferredAudioLabel(any()) } returns null
        every { interactor.getPreferredAudioLang(any()) } returns null
        every { interactor.getPreferredSubtitleLang(any()) } returns null
        every { interactor.getPreferredSubtitleUrl(any()) } returns null
        every { interactor.isDebugOverlayEnabled() } returns false
        every { interactor.getSubtitleSize() } returns SubtitleSize.MEDIUM
        every { interactor.getBufferPreset() } returns BufferPreset.AUTO
        every { interactor.isFastDnsEnabled() } returns true
        every { interactor.saveTrackPreferences(any(), any(), any(), any(), any()) } returns Unit
        every { interactor.findNextEpisode(any(), any(), any()) } returns null
        every { interactor.findPreviousEpisode(any(), any(), any()) } returns null
        coEvery { skipSegmentInteractor.loadSegments(any(), any(), any()) } returns emptyList()
        every { skipSegmentInteractor.findCreditsSegment(any()) } returns null
        every { skipSegmentInteractor.findActiveSegment(any(), any()) } returns null
        every { mapper.formatTime(any()) } returns "00:00"
        every { mapper.formatSeekOffset(any(), any()) } returns "+10s"
        every { mapper.buildTitle(any(), any(), any()) } returns "Title"
        every { mapper.buildSubtitle(any(), any(), any(), any()) } returns "Sub"
        every { mapper.mapEpisodes(any()) } returns null
        every { mapper.mapSkipSegmentLabel(any()) } returns "Skip"
        every { mapper.defaultSoundModeLabel() } returns "Stereo"
        every { playbackController.setCallback(capture(callbackSlot)) } returns Unit
    }

    @AfterEach
    fun tearDownPlayerVMFixture() {
        createdViewModels.forEach(PlayerVM::testCancelScope)
        createdViewModels.clear()
        clearAllMocks()
    }

    protected fun createVM() = PlayerVM(
        router = router,
        errorHandler = errorHandler,
        params = params,
        mapper = mapper,
        interactor = interactor,
        resources = FakeResourceProvider(),
        skipSegmentInteractor = skipSegmentInteractor,
        contentStateFactory = contentStateFactory,
        playbackController = playbackController,
    ).also(createdViewModels::add)

    protected fun startedVM(): PlayerVM = createVM().also { it.testOnStart() }

    protected fun contentState(vm: PlayerVM) = (vm.testStateValue as PlayerViewState.Content).content

    protected fun verifyContentChangeResult(vararg expectedTypes: ContentChangeType) {
        verify {
            router.back(
                RESULT_CONTENT_CHANGED,
                match { result ->
                    val changes = result as? ContentChangeSet ?: return@match false
                    changes.changes[42] == expectedTypes.toSet()
                },
            )
        }
    }

    protected fun verifyEmptyContentChangeResult() {
        verify {
            router.back(
                RESULT_CONTENT_CHANGED,
                match { result ->
                    (result as? ContentChangeSet)?.isEmpty == true
                },
            )
        }
    }

    protected val testItem = Item(
        id = 42,
        title = "Breaking Bad",
        type = ItemType.SERIAL,
        watched = 5,
        new = 3,
    )

    protected val currentEpisodeItem = VideoItemUIState(
        id = 101,
        title = "1. Pilot",
        imageUrl = "",
        bigImageUrl = "",
        seasonNumber = 1,
        episodeNumber = 1,
    )

    protected fun Item.withCurrentEpisodeWatched(watched: Boolean?): Item {
        val status = watched?.let { if (it) 1 else 0 }
        return copy(
            seasons = listOf(
                Season(
                    id = 1,
                    number = 1,
                    episodes = listOf(Episode(id = 101, number = 1, title = "Pilot", watched = status)),
                )
            )
        )
    }

    protected val testResolvedMedia = ResolvedMedia(
        files = emptyList(),
        audios = emptyList(),
        subtitles = emptyList(),
        watchingTime = null,
        duration = 2400,
        videoNumber = 1,
        episodeId = 101,
        episodeTitle = "Pilot",
        isSeries = true,
        hasNext = true,
        isCurrentMediaWatched = false,
        hasPrevious = true,
        seasonNumber = 1,
        episodeNumber = 1,
    )

    protected val testSubtitleTracks = listOf(
        SubtitleTrackUIState(index = 0, label = "Off", language = "", url = ""),
        SubtitleTrackUIState(index = 1, label = "Russian", language = "rus", url = "https://test/subtitles/rus.vtt"),
        SubtitleTrackUIState(
            index = 2,
            label = "Russian forced",
            language = "rus",
            url = "https://test/subtitles/rus-forced.vtt",
        ),
    )

    protected val testContentState = PlayerContentState(
        title = "Breaking Bad",
        subtitle = "S1E1",
        isPlaying = true,
        currentPosition = 0L,
        duration = 2_400_000L,
        bufferedPosition = 0L,
        controlsVisible = true,
        controlsFocusTarget = null,
        activePanel = ActivePanel.None,
        seekIndicator = null,
        playPauseIndicator = null,
        audioTracks = listOf(
            AudioTrackUIState(0, "English", "eng"),
            AudioTrackUIState(1, "Russian", "rus"),
        ),
        selectedAudioTrackIndex = 0,
        subtitleTracks = testSubtitleTracks,
        selectedSubtitleIndex = 0,
        soundModes = emptyList(),
        selectedSoundModeIndex = 0,
        subtitleSize = SubtitleSize.MEDIUM,
        qualities = emptyList(),
        selectedQualityIndex = 0,
        speeds = emptyList(),
        selectedSpeedIndex = 0,
        aspectRatios = emptyList(),
        selectedAspectRatioIndex = 0,
        bufferPresets = listOf(BufferPresetUIState(0, "Auto", BufferPreset.AUTO)),
        selectedBufferPresetIndex = 0,
        isMovie = false,
        hasNextEpisode = true,
        hasPreviousEpisode = true,
        nextEpisodeCountdown = null,
        canMarkCurrentWatched = true,
        isCurrentMediaWatched = false,
        isMarkCurrentWatchedInFlight = false,
        resumeDialog = null,
        episodes = null,
        currentEpisodeId = 101,
    )
}
