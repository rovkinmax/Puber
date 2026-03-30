package com.kino.puber.domain.interactor.player

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.Episode
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.Season
import com.kino.puber.data.api.models.VideoFile
import com.kino.puber.data.api.models.VideoUrl
import com.kino.puber.data.repository.ItemDetailsRepository
import com.kino.puber.data.repository.PlayerPreferencesRepository
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PlayerInteractorTest {

    private val api = mockk<KinoPubApiClient>()
    private val itemDetailsRepository = mockk<ItemDetailsRepository>()
    private val playerPreferencesRepository = mockk<PlayerPreferencesRepository>()

    private lateinit var interactor: PlayerInteractor

    // region Fixtures

    private val episode1 = Episode(id = 1, number = 1, title = "Pilot")
    private val episode2 = Episode(id = 2, number = 2, title = "Second")
    private val episode3 = Episode(id = 3, number = 3, title = "Third")

    private val episode4 = Episode(id = 4, number = 1, title = "Season 2 Pilot")
    private val episode5 = Episode(id = 5, number = 2, title = "Season 2 Second")

    private val season1 = Season(id = 1, number = 1, episodes = listOf(episode1, episode2, episode3))
    private val season2 = Season(id = 2, number = 2, episodes = listOf(episode4, episode5))

    private val serialItem = Item(
        id = 42,
        title = "Test Serial",
        type = ItemType.SERIAL,
        seasons = listOf(season1, season2),
    )

    private val movieItem = Item(
        id = 10,
        title = "Test Movie",
        type = ItemType.MOVIE,
    )

    // endregion

    @BeforeEach
    fun setup() {
        interactor = PlayerInteractor(api, itemDetailsRepository, playerPreferencesRepository)
    }

    // region isSeriesType

    @Test
    fun isSeriesType_serial_returnsTrue() {
        assertTrue(interactor.isSeriesType(ItemType.SERIAL))
    }

    @Test
    fun isSeriesType_tvShow_returnsTrue() {
        assertTrue(interactor.isSeriesType(ItemType.TV_SHOW))
    }

    @Test
    fun isSeriesType_docuSerial_returnsTrue() {
        assertTrue(interactor.isSeriesType(ItemType.DOCU_SERIAL))
    }

    @Test
    fun isSeriesType_movie_returnsFalse() {
        assertFalse(interactor.isSeriesType(ItemType.MOVIE))
    }

    @Test
    fun isSeriesType_docuMovie_returnsFalse() {
        assertFalse(interactor.isSeriesType(ItemType.DOCU_MOVIE))
    }

    @Test
    fun isSeriesType_concert_returnsFalse() {
        assertFalse(interactor.isSeriesType(ItemType.CONCERT))
    }

    // endregion

    // region findNextEpisode

    @Test
    fun findNextEpisode_withinSameSeason_returnsNextEpisode() {
        val result = interactor.findNextEpisode(serialItem, currentSeason = 1, currentEpisode = 1)
        assertEquals(1 to 2, result)
    }

    @Test
    fun findNextEpisode_lastEpisodeOfSeason_returnsFirstOfNextSeason() {
        val result = interactor.findNextEpisode(serialItem, currentSeason = 1, currentEpisode = 3)
        assertEquals(2 to 1, result)
    }

    @Test
    fun findNextEpisode_lastEpisodeOfLastSeason_returnsNull() {
        val result = interactor.findNextEpisode(serialItem, currentSeason = 2, currentEpisode = 2)
        assertNull(result)
    }

    @Test
    fun findNextEpisode_noSeasons_returnsNull() {
        val item = Item(id = 1, title = "No Seasons", type = ItemType.SERIAL, seasons = null)
        assertNull(interactor.findNextEpisode(item, currentSeason = 1, currentEpisode = 1))
    }

    @Test
    fun findNextEpisode_unknownSeason_returnsNull() {
        assertNull(interactor.findNextEpisode(serialItem, currentSeason = 99, currentEpisode = 1))
    }

    // endregion

    // region findPreviousEpisode

    @Test
    fun findPreviousEpisode_withinSameSeason_returnsPreviousEpisode() {
        val result = interactor.findPreviousEpisode(serialItem, currentSeason = 1, currentEpisode = 2)
        assertEquals(1 to 1, result)
    }

    @Test
    fun findPreviousEpisode_firstEpisodeOfSeason_returnsLastOfPreviousSeason() {
        val result = interactor.findPreviousEpisode(serialItem, currentSeason = 2, currentEpisode = 1)
        assertEquals(1 to 3, result)
    }

    @Test
    fun findPreviousEpisode_firstEpisodeOfFirstSeason_returnsNull() {
        val result = interactor.findPreviousEpisode(serialItem, currentSeason = 1, currentEpisode = 1)
        assertNull(result)
    }

    @Test
    fun findPreviousEpisode_noSeasons_returnsNull() {
        val item = Item(id = 1, title = "No Seasons", type = ItemType.SERIAL, seasons = null)
        assertNull(interactor.findPreviousEpisode(item, currentSeason = 1, currentEpisode = 1))
    }

    @Test
    fun findPreviousEpisode_unknownSeason_returnsNull() {
        assertNull(interactor.findPreviousEpisode(serialItem, currentSeason = 99, currentEpisode = 1))
    }

    // endregion

    // region findEpisode

    @Test
    fun findEpisode_found_returnsCorrectEpisode() {
        val result = interactor.findEpisode(serialItem, seasonNumber = 1, episodeNumber = 2)
        assertNotNull(result)
        assertEquals(episode2, result)
    }

    @Test
    fun findEpisode_notFound_wrongSeason_returnsNull() {
        assertNull(interactor.findEpisode(serialItem, seasonNumber = 99, episodeNumber = 1))
    }

    @Test
    fun findEpisode_notFound_wrongEpisode_returnsNull() {
        assertNull(interactor.findEpisode(serialItem, seasonNumber = 1, episodeNumber = 99))
    }

    @Test
    fun findEpisode_noSeasons_returnsNull() {
        val item = Item(id = 1, title = "No Seasons", type = ItemType.SERIAL, seasons = null)
        assertNull(interactor.findEpisode(item, seasonNumber = 1, episodeNumber = 1))
    }

    // endregion

    // region selectStreamUrl

    @Test
    fun selectStreamUrl_auto_prefersHls4() {
        val files = listOf(
            VideoFile(url = VideoUrl(http = "http://video.mp4", hls = "hls://video.m3u8", hls4 = "hls4://video.m3u8")),
        )
        val result = interactor.selectStreamUrl(files, qualityIndex = 0)
        assertEquals("hls4://video.m3u8", result)
    }

    @Test
    fun selectStreamUrl_auto_fallsBackToHls() {
        val files = listOf(
            VideoFile(url = VideoUrl(http = "http://video.mp4", hls = "hls://video.m3u8", hls4 = null)),
        )
        val result = interactor.selectStreamUrl(files, qualityIndex = 0)
        assertEquals("hls://video.m3u8", result)
    }

    @Test
    fun selectStreamUrl_auto_fallsBackToHttp_whenNoHls() {
        val files = listOf(
            VideoFile(url = VideoUrl(http = "http://video.mp4", hls = null, hls4 = null)),
        )
        val result = interactor.selectStreamUrl(files, qualityIndex = 0)
        assertEquals("http://video.mp4", result)
    }

    @Test
    fun selectStreamUrl_specific_returnsCorrectQuality() {
        val files = listOf(
            VideoFile(url = VideoUrl(hls = "hls://1080p.m3u8"), quality = "1080p", qualityId = 4),
            VideoFile(url = VideoUrl(hls = "hls://720p.m3u8"), quality = "720p", qualityId = 3),
            VideoFile(url = VideoUrl(hls = "hls://480p.m3u8"), quality = "480p", qualityId = 2),
        )
        // qualityIndex = 1 → first entry after sorting descending by qualityId → 1080p
        val result = interactor.selectStreamUrl(files, qualityIndex = 1)
        assertEquals("hls://1080p.m3u8", result)
    }

    @Test
    fun selectStreamUrl_specific_secondQuality_returns720p() {
        val files = listOf(
            VideoFile(url = VideoUrl(hls = "hls://1080p.m3u8"), quality = "1080p", qualityId = 4),
            VideoFile(url = VideoUrl(hls = "hls://720p.m3u8"), quality = "720p", qualityId = 3),
        )
        val result = interactor.selectStreamUrl(files, qualityIndex = 2)
        assertEquals("hls://720p.m3u8", result)
    }

    @Test
    fun selectStreamUrl_nullFiles_returnsNull() {
        assertNull(interactor.selectStreamUrl(null, qualityIndex = 0))
    }

    @Test
    fun selectStreamUrl_emptyFiles_returnsNull() {
        assertNull(interactor.selectStreamUrl(emptyList(), qualityIndex = 0))
    }

    @Test
    fun selectStreamUrl_fileWithNullUrl_returnsNull() {
        val files = listOf(VideoFile(url = null))
        assertNull(interactor.selectStreamUrl(files, qualityIndex = 0))
    }

    // endregion

    // region resolveMedia

    @Test
    fun resolveMedia_movie_returnsCorrectFlags() {
        val result = interactor.resolveMedia(movieItem, seasonNumber = null, episodeNumber = null)
        assertFalse(result.isSeries)
        assertFalse(result.hasNext)
        assertFalse(result.hasPrevious)
        assertNull(result.seasonNumber)
        assertNull(result.episodeNumber)
        assertNull(result.episodeId)
        assertNull(result.episodeTitle)
    }

    @Test
    fun resolveMedia_series_computesHasNextAndHasPrevious() {
        // Middle episode: s1e2 — has both next (s1e3) and previous (s1e1)
        val result = interactor.resolveMedia(serialItem, seasonNumber = 1, episodeNumber = 2)
        assertTrue(result.isSeries)
        assertTrue(result.hasNext)
        assertTrue(result.hasPrevious)
        assertEquals(1, result.seasonNumber)
        assertEquals(2, result.episodeNumber)
    }

    @Test
    fun resolveMedia_series_firstEpisode_hasPreviousIsFalse() {
        val result = interactor.resolveMedia(serialItem, seasonNumber = 1, episodeNumber = 1)
        assertTrue(result.isSeries)
        assertTrue(result.hasNext)
        assertFalse(result.hasPrevious)
    }

    @Test
    fun resolveMedia_series_lastEpisode_hasNextIsFalse() {
        val result = interactor.resolveMedia(serialItem, seasonNumber = 2, episodeNumber = 2)
        assertTrue(result.isSeries)
        assertFalse(result.hasNext)
        assertTrue(result.hasPrevious)
    }

    @Test
    fun resolveMedia_series_setsEpisodeFields() {
        val result = interactor.resolveMedia(serialItem, seasonNumber = 1, episodeNumber = 1)
        assertEquals(episode1.id, result.episodeId)
        assertEquals(episode1.title, result.episodeTitle)
        assertEquals(episode1.number, result.videoNumber)
    }

    // endregion
}
