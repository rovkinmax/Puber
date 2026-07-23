package com.kino.puber.ui.feature.player.vm

import com.kino.puber.core.content.ContentChangeType
import com.kino.puber.core.error.ErrorEntity
import com.kino.puber.core.ui.uikit.component.moviesList.VideoGridUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.SkipSegmentType
import com.kino.puber.domain.interactor.player.WatchedDetailsRefreshException
import com.kino.puber.ui.feature.player.model.PlayerAction
import com.kino.puber.ui.feature.player.model.SkipSegmentUIState
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

internal class PlayerVMMutationAndResultTest : PlayerVMTestFixture() {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    // region Manual mark watched

    @Test
    fun markCurrentWatched_movieCallsInteractorWithoutEpisodeAndUpdatesState() {
        val updated = testItem.copy(type = ItemType.MOVIE, watched = 1)
        coEvery { interactor.resolveMedia(any(), any(), any()) } returns testResolvedMedia.copy(
            isSeries = false,
            hasNext = false,
            hasPrevious = false,
            seasonNumber = null,
            episodeNumber = null,
            episodeId = null,
            episodeTitle = null,
        )
        coEvery { contentStateFactory.build(any(), any(), any(), any(), any(), any()) } returns testContentState.copy(
            isMovie = true,
            hasNextEpisode = false,
            hasPreviousEpisode = false,
        )
        coEvery {
            interactor.markCurrentAsWatched(id = 42, season = null, episode = null)
        } returns updated
        val vm = startedVM()

        vm.onAction(PlayerAction.MarkCurrentWatched)

        assertTrue(contentState(vm).isCurrentMediaWatched)
        coVerify(exactly = 1) {
            interactor.markCurrentAsWatched(id = 42, season = null, episode = null)
        }
    }

    @Test
    fun markCurrentWatched_episodePassesSeasonAndEpisodeAndUpdatesEpisodes() {
        val updated = testItem.copy(watched = 1)
        val updatedEpisodes = VideoGridUIState(emptyList())
        coEvery { interactor.markCurrentAsWatched(id = 42, season = 1, episode = 1) } returns updated
        every { mapper.mapEpisodes(updated) } returns updatedEpisodes
        val vm = startedVM()

        vm.onAction(PlayerAction.MarkCurrentWatched)

        assertTrue(contentState(vm).isCurrentMediaWatched)
        assertEquals(updatedEpisodes, contentState(vm).episodes)
        coVerify(exactly = 1) { interactor.markCurrentAsWatched(id = 42, season = 1, episode = 1) }
    }

    @Test
    fun markCurrentWatched_alreadyWatchedDoesNotCallInteractor() {
        coEvery { contentStateFactory.build(any(), any(), any(), any(), any(), any()) } returns testContentState.copy(
            isCurrentMediaWatched = true,
        )
        val vm = startedVM()

        vm.onAction(PlayerAction.MarkCurrentWatched)

        coVerify(exactly = 0) { interactor.markCurrentAsWatched(any(), any(), any()) }
    }

    @Test
    fun autoMark_alreadyWatchedDoesNotCallInteractor() {
        coEvery { contentStateFactory.build(any(), any(), any(), any(), any(), any()) } returns testContentState.copy(
            isCurrentMediaWatched = true,
        )
        val vm = startedVM()

        callbackSlot.captured.onPlaybackEnded()

        coVerify(exactly = 0) { interactor.markCurrentAsWatched(any(), any(), any()) }
    }

    @Test
    fun markCurrentWatched_failurePreservesStateAndMapsError() {
        val failure = IllegalStateException("Failed")
        coEvery { interactor.markCurrentAsWatched(id = 42, season = 1, episode = 1) } throws failure
        every { errorHandler.map(failure) } returns ErrorEntity(message = "Mapped", code = "test")
        val vm = startedVM()

        vm.onAction(PlayerAction.MarkCurrentWatched)

        assertFalse(contentState(vm).isCurrentMediaWatched)
        verify(exactly = 1) { errorHandler.map(failure) }
    }

    @Test
    fun markCurrentWatched_writeSuccessAndRefreshFailure_keepsStateAndShowsRefreshError() {
        val refreshFailure = IllegalStateException("refresh failed")
        coEvery { interactor.markCurrentAsWatched(42, 1, 1) } throws
            WatchedDetailsRefreshException(refreshFailure)
        every { errorHandler.map(refreshFailure) } returns ErrorEntity(message = "Refresh failed", code = "test")
        val vm = startedVM()

        vm.onAction(PlayerAction.MarkCurrentWatched)

        assertTrue(contentState(vm).isCurrentMediaWatched)
        verify(exactly = 1) { errorHandler.map(refreshFailure) }
        vm.onAction(PlayerAction.OnBackPressed)
        verifyContentChangeResult(ContentChangeType.Watched, ContentChangeType.PlaybackProgress)
    }

    @Test
    fun markCurrentWatched_afterAutoMarkDoesNotDuplicateManualApiCall() {
        val vm = startedVM()

        callbackSlot.captured.onPlaybackEnded()
        vm.onAction(PlayerAction.MarkCurrentWatched)

        assertTrue(contentState(vm).isCurrentMediaWatched)
        coVerify(exactly = 1) { interactor.markCurrentAsWatched(id = 42, season = 1, episode = 1) }
    }

    @Test
    fun markCurrentWatched_rapidClicksShareSingleRequest() {
        val releaseMark = CompletableDeferred<Unit>()
        coEvery { interactor.markCurrentAsWatched(42, 1, 1) } coAnswers {
            releaseMark.await()
            testItem.withCurrentEpisodeWatched(true)
        }
        val vm = startedVM()

        vm.onAction(PlayerAction.MarkCurrentWatched)
        vm.onAction(PlayerAction.MarkCurrentWatched)

        assertTrue(contentState(vm).isMarkCurrentWatchedInFlight)
        coVerify(exactly = 1) { interactor.markCurrentAsWatched(42, 1, 1) }
        releaseMark.complete(Unit)
        assertFalse(contentState(vm).isMarkCurrentWatchedInFlight)
    }

    @Test
    fun autoMark_failurePreservesStateAndAllowsManualRetry() {
        val failure = IllegalStateException("auto mark failed")
        coEvery { interactor.markCurrentAsWatched(42, 1, 1) } throws failure
        every { errorHandler.map(failure) } returns ErrorEntity(message = "Mapped", code = "test")
        val vm = startedVM()

        callbackSlot.captured.onPlaybackEnded()
        callbackSlot.captured.onPlaybackEnded()
        vm.onAction(PlayerAction.MarkCurrentWatched)

        assertFalse(contentState(vm).isCurrentMediaWatched)
        coVerify(exactly = 2) { interactor.markCurrentAsWatched(42, 1, 1) }
    }

    @Test
    fun markCurrentWatched_nullableRefreshedStatus_usesSuccessfulWriteFallback() {
        val refreshedWithUnknownStatus = testItem.withCurrentEpisodeWatched(null)
        coEvery { interactor.markCurrentAsWatched(42, 1, 1) } returns refreshedWithUnknownStatus
        val vm = startedVM()

        vm.onAction(PlayerAction.MarkCurrentWatched)

        assertTrue(contentState(vm).isCurrentMediaWatched)
        vm.onAction(PlayerAction.MarkCurrentWatched)
        coVerify(exactly = 1) { interactor.markCurrentAsWatched(42, 1, 1) }
    }

    @Test
    fun markCurrentWatched_staleCompletionDoesNotUpdateNextEpisode() {
        val releaseMark = CompletableDeferred<Unit>()
        coEvery { interactor.resolveMedia(any(), any(), any()) } returns testResolvedMedia andThen
            testResolvedMedia.copy(
                videoNumber = 2,
                episodeId = 102,
                episodeNumber = 2,
            )
        coEvery { contentStateFactory.build(any(), any(), any(), any(), any(), any()) } returns testContentState andThen
            testContentState.copy(currentEpisodeId = 102, isCurrentMediaWatched = false)
        coEvery { interactor.markCurrentAsWatched(42, 1, 1) } coAnswers {
            releaseMark.await()
            testItem.withCurrentEpisodeWatched(true)
        }
        val vm = startedVM()

        vm.onAction(PlayerAction.MarkCurrentWatched)
        vm.onAction(PlayerAction.SelectEpisode(1, 2))
        releaseMark.complete(Unit)

        assertFalse(contentState(vm).isCurrentMediaWatched)
    }

    @Test
    fun episodeWatchedChanged_currentEpisodeUpdatesWatchedButtonState() {
        val updated = testItem.withCurrentEpisodeWatched(true)
        val updatedEpisodes = VideoGridUIState(emptyList())
        coEvery { interactor.setEpisodeWatched(42, season = 1, episode = 1, watched = true) } returns updated
        every { mapper.mapEpisodes(updated) } returns updatedEpisodes
        val vm = startedVM()

        vm.onAction(PlayerAction.EpisodeWatchedChanged(currentEpisodeItem, watched = true))

        assertTrue(contentState(vm).isCurrentMediaWatched)
        assertEquals(updatedEpisodes, contentState(vm).episodes)
    }

    @Test
    fun episodeWatchedChanged_writeSuccessAndRefreshFailure_keepsChangeAndLocalState() {
        coEvery { interactor.setEpisodeWatched(42, season = 1, episode = 1, watched = true) } returns null
        val vm = startedVM()

        vm.onAction(PlayerAction.EpisodeWatchedChanged(currentEpisodeItem, watched = true))
        vm.onAction(PlayerAction.OnBackPressed)

        assertTrue(contentState(vm).isCurrentMediaWatched)
        verifyContentChangeResult(ContentChangeType.Watched, ContentChangeType.PlaybackProgress)
    }

    @Test
    fun episodeWatchedChanged_currentEpisodeUnwatchedUpdatesWatchedButtonState() {
        val updated = testItem.withCurrentEpisodeWatched(false)
        coEvery { contentStateFactory.build(any(), any(), any(), any(), any(), any()) } returns testContentState.copy(
            isCurrentMediaWatched = true,
        )
        coEvery { interactor.setEpisodeWatched(42, season = 1, episode = 1, watched = false) } returns updated
        val vm = startedVM()

        vm.onAction(PlayerAction.EpisodeWatchedChanged(currentEpisodeItem, watched = false))

        assertFalse(contentState(vm).isCurrentMediaWatched)
    }

    @Test
    fun explicitCurrentEpisodeUnwatchSuppressesAutomaticRemark() {
        val updated = testItem.withCurrentEpisodeWatched(false)
        coEvery { contentStateFactory.build(any(), any(), any(), any(), any(), any()) } returns testContentState.copy(
            isCurrentMediaWatched = true,
        )
        coEvery { interactor.setEpisodeWatched(42, season = 1, episode = 1, watched = false) } returns updated
        val vm = startedVM()

        vm.onAction(PlayerAction.EpisodeWatchedChanged(currentEpisodeItem, watched = false))
        callbackSlot.captured.onPlaybackEnded()

        assertFalse(contentState(vm).isCurrentMediaWatched)
        coVerify(exactly = 0) { interactor.markCurrentAsWatched(any(), any(), any()) }
    }

    @Test
    fun failedExplicitCurrentEpisodeMutation_doesNotSuppressAutomaticMark() {
        val failure = IllegalStateException("failed")
        coEvery { interactor.setEpisodeWatched(42, 1, 1, false) } throws failure
        every { errorHandler.map(failure) } returns ErrorEntity(message = "Mapped", code = "test")
        val vm = startedVM()

        vm.onAction(PlayerAction.EpisodeWatchedChanged(currentEpisodeItem, watched = false))
        callbackSlot.captured.onPlaybackEnded()

        coVerify(exactly = 1) { interactor.markCurrentAsWatched(42, 1, 1) }
        assertTrue(contentState(vm).isCurrentMediaWatched)
    }

    @Test
    fun seasonWatchedChanged_currentSeasonUnwatchedUpdatesWatchedButtonState() {
        val updated = testItem.withCurrentEpisodeWatched(false)
        coEvery { contentStateFactory.build(any(), any(), any(), any(), any(), any()) } returns testContentState.copy(
            isCurrentMediaWatched = true,
        )
        coEvery { interactor.setSeasonWatched(42, season = 1, watched = false) } returns updated
        val vm = startedVM()

        vm.onAction(PlayerAction.SeasonWatchedChanged(currentEpisodeItem, watched = false))

        assertFalse(contentState(vm).isCurrentMediaWatched)
    }

    // endregion

    // region Back navigation (additional branches)

    @Test
    fun backPressed_cancelsSkipSegment_whenActive() {
        coEvery { contentStateFactory.build(any(), any(), any(), any(), any(), any()) } returns testContentState.copy(
            activeSkipSegment = SkipSegmentUIState("Skip", 30_000L, SkipSegmentType.INTRO, 5),
        )
        val vm = startedVM()

        vm.onAction(PlayerAction.OnBackPressed)

        assertNull(contentState(vm).activeSkipSegment)
    }

    // endregion

    // region PlaybackEnded edge cases

    @Test
    fun playbackEnded_showsControls_forSeriesWithoutNextEpisode() {
        coEvery { contentStateFactory.build(any(), any(), any(), any(), any(), any()) } returns testContentState.copy(
            hasNextEpisode = false,
        )
        val vm = startedVM()

        callbackSlot.captured.onPlaybackEnded()

        assertNull(contentState(vm).nextEpisodeCountdown)
        assertTrue(contentState(vm).controlsVisible)
    }

    @Test
    fun playbackEnded_thenBack_returnsWatchedResult() {
        coEvery { contentStateFactory.build(any(), any(), any(), any(), any(), any()) } returns testContentState.copy(
            hasNextEpisode = false,
        )
        val vm = startedVM()

        callbackSlot.captured.onPlaybackEnded()
        vm.onAction(PlayerAction.OnBackPressed)

        verifyContentChangeResult(ContentChangeType.Watched, ContentChangeType.PlaybackProgress)
    }

    // endregion

    // region Content change results

    @Test
    fun episodeWatchedChanged_success_returnsWatchedResult() {
        every { mapper.mapEpisodes(any()) } returns null
        coEvery { interactor.setEpisodeWatched(42, 1, 1, true) } returns testItem
        val vm = startedVM()

        vm.onAction(
            PlayerAction.EpisodeWatchedChanged(
                item = VideoItemUIState(
                    id = 101,
                    title = "Episode 1",
                    imageUrl = "",
                    bigImageUrl = "",
                    seasonNumber = 1,
                    episodeNumber = 1,
                ),
                watched = true,
            )
        )
        vm.onAction(PlayerAction.OnBackPressed)

        verifyContentChangeResult(ContentChangeType.Watched, ContentChangeType.PlaybackProgress)
    }

    @Test
    fun seasonWatchedChanged_success_returnsWatchedResult() {
        every { mapper.mapEpisodes(any()) } returns null
        coEvery { interactor.setSeasonWatched(42, 1, true) } returns testItem
        val vm = startedVM()

        vm.onAction(
            PlayerAction.SeasonWatchedChanged(
                item = VideoItemUIState(
                    id = 1,
                    title = "Season 1",
                    imageUrl = "",
                    bigImageUrl = "",
                    seasonNumber = 1,
                ),
                watched = true,
            )
        )
        vm.onAction(PlayerAction.OnBackPressed)

        verifyContentChangeResult(ContentChangeType.Watched, ContentChangeType.PlaybackProgress)
    }

    // endregion
}
