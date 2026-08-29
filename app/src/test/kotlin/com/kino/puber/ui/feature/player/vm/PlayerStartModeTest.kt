package com.kino.puber.ui.feature.player.vm

import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.domain.interactor.player.PlayerInteractor
import com.kino.puber.domain.interactor.player.ResolvedMedia
import com.kino.puber.domain.interactor.player.SkipSegmentInteractor
import com.kino.puber.domain.interactor.player.StreamSource
import com.kino.puber.domain.model.SubtitleSize
import com.kino.puber.ui.ScreensImpl
import com.kino.puber.ui.feature.player.model.BufferPreset
import com.kino.puber.ui.feature.player.model.PlayerContentState
import com.kino.puber.ui.feature.player.model.PlayerScreenParams
import com.kino.puber.ui.feature.player.model.PlayerStartMode
import com.kino.puber.ui.feature.player.model.PlayerUIMapper
import com.kino.puber.ui.feature.player.model.ResumeDialogState
import com.kino.puber.util.FakeResourceProvider
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

internal class PlayerStartModeTest {

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
    private lateinit var contentState: PlayerContentState

    private val item = Item(
        id = 42,
        title = "Synthetic series",
        type = ItemType.SERIAL,
    )
    private val savedMedia = ResolvedMedia(
        files = emptyList(),
        audios = emptyList(),
        subtitles = emptyList(),
        watchingTime = 120,
        duration = 2_400,
        videoNumber = 1,
        episodeId = 101,
        episodeTitle = "Pilot",
        isCurrentMediaWatched = false,
        isSeries = true,
        hasNext = false,
        hasPrevious = false,
        seasonNumber = 1,
        episodeNumber = 1,
    )

    @BeforeEach
    fun setUp() {
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
        contentState = mockk(relaxed = true)

        coEvery { interactor.getItemDetails(item.id) } returns item
        every { interactor.resolveMedia(item, 1, 1, null) } returns savedMedia
        every {
            contentStateFactory.build(any(), any(), any(), any(), any(), any())
        } returns contentState
        every { interactor.selectStreamUrl(any(), any()) } returns StreamSource("https://test/v.m3u8", isHls = true)
        every { interactor.isDebugOverlayEnabled() } returns false
        every { interactor.getSubtitleSize() } returns SubtitleSize.MEDIUM
        every { interactor.getBufferPreset() } returns BufferPreset.AUTO
        every { interactor.isFastDnsEnabled() } returns true
        coEvery { skipSegmentInteractor.loadSegments(any(), any(), any()) } returns emptyList()
        every { skipSegmentInteractor.findCreditsSegment(any()) } returns null
        every { mapper.formatTime(any()) } returns "2:00"
        every { mapper.buildSubtitle(any(), any(), any(), any()) } returns "S1E1"
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun explicitStartOverSuppressesSavedProgressResumeAndPreparesAtZero() {
        createVM(PlayerStartMode.StartFromBeginning).testOnStart()

        verify {
            contentStateFactory.build(
                item = item,
                resolved = savedMedia,
                resumeDialog = null,
                subtitleSize = SubtitleSize.MEDIUM,
                savedBufferPreset = BufferPreset.AUTO,
                fastDnsEnabled = true,
            )
        }
        verify {
            playbackController.prepare(
                stream = StreamSource("https://test/v.m3u8", isHls = true),
                subtitles = emptyList(),
                startPosition = 0L,
                bufferPreset = BufferPreset.AUTO,
                fastDns = false,
            )
        }
        verify(exactly = 0) { mapper.formatTime(any()) }
    }

    @Test
    fun normalStartModeKeepsSavedProgressResumeDialog() {
        createVM(PlayerStartMode.ResumeIfAvailable).testOnStart()

        verify {
            contentStateFactory.build(
                item = item,
                resolved = savedMedia,
                resumeDialog = ResumeDialogState(
                    savedPosition = 120_000L,
                    formattedTime = "2:00",
                    episodeInfo = "S1E1",
                ),
                subtitleSize = SubtitleSize.MEDIUM,
                savedBufferPreset = BufferPreset.AUTO,
                fastDnsEnabled = true,
            )
        }
        verify {
            playbackController.prepare(
                stream = StreamSource("https://test/v.m3u8", isHls = true),
                subtitles = emptyList(),
                startPosition = null,
                bufferPreset = BufferPreset.AUTO,
                fastDns = false,
            )
        }
    }

    @Test
    fun playerScreenKeyDistinguishesExplicitStartOver() {
        assertEquals(
            "PlayerScreen_42_v7_startOver",
            ScreensImpl.player(
                itemId = 42,
                videoNumber = 7,
                startMode = PlayerStartMode.StartFromBeginning,
            ).key,
        )
    }

    private fun createVM(startMode: PlayerStartMode): PlayerVM {
        return PlayerVM(
            router = router,
            errorHandler = errorHandler,
            params = PlayerScreenParams(
                itemId = item.id,
                seasonNumber = 1,
                episodeNumber = 1,
                startMode = startMode,
            ),
            mapper = mapper,
            interactor = interactor,
            skipSegmentInteractor = skipSegmentInteractor,
            contentStateFactory = contentStateFactory,
            playbackController = playbackController,
            resources = FakeResourceProvider(),
        )
    }
}
