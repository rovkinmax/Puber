package com.kino.puber.ui.feature.details.vm

import com.kino.puber.R
import com.kino.puber.core.content.ContentChange
import com.kino.puber.core.content.ContentChangeSet
import com.kino.puber.core.content.ContentChangeType
import com.kino.puber.core.error.ErrorEntity
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.model.BookmarkMode
import com.kino.puber.core.system.ResourceProvider
import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.RESULT_CONTENT_CHANGED
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.isSeriesLike
import com.kino.puber.data.preferences.BookmarkPreferencesRepository
import com.kino.puber.domain.interactor.bookmarks.SavedItemInteractor
import com.kino.puber.domain.interactor.bookmarks.WatchLaterBookmarkInteractor
import com.kino.puber.domain.interactor.details.DetailsInteractor
import com.kino.puber.domain.interactor.schedule.EpisodeScheduleInteractor
import com.kino.puber.ui.feature.details.model.DetailsAction
import com.kino.puber.ui.feature.details.model.DetailsCastMemberUIState
import com.kino.puber.ui.feature.details.model.DetailsScreenParams
import com.kino.puber.ui.feature.details.model.DetailsScreenState
import com.kino.puber.ui.feature.details.model.DetailsScreenUIMapper
import com.kino.puber.ui.feature.bookmarkpicker.model.BookmarkPickerResult
import com.kino.puber.ui.feature.bookmarkpicker.openBookmarkPicker
import com.kino.puber.ui.feature.bookmarkpicker.withBookmarkResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class DetailsVM(
    router: AppRouter,
    private val params: DetailsScreenParams,
    private val mapper: DetailsScreenUIMapper,
    private val interactor: DetailsInteractor,
    private val episodeScheduleInteractor: EpisodeScheduleInteractor,
    private val savedItemInteractor: SavedItemInteractor,
    private val bookmarkPreferencesRepository: BookmarkPreferencesRepository,
    private val resources: ResourceProvider,
    override val errorHandler: ErrorHandler,
) : PuberVM<DetailsScreenState>(router) {

    override val initialViewState = DetailsScreenState.Loading

    override fun dispatchError(error: ErrorEntity) {
        if (stateValue is DetailsScreenState.Content) {
            showMessage(error.message)
        } else {
            updateViewState(DetailsScreenState.Error(error.message))
        }
    }
    private var currentItem: Item? = null
    private val scheduleController = DetailsScheduleController(
        interactor = episodeScheduleInteractor,
        mapper = mapper,
        initialEpisode = params.initialEpisode,
    )
    private var contentChanges = ContentChangeSet.empty()
    private val pendingMutations = mutableSetOf<Job>()
    private val mutationMutex = Mutex()
    private var castEnrichmentJob: Job? = null
    private var castEnrichmentGeneration = 0L
    private var closeJob: Job? = null
    private var closing = false

    override fun onStart() {
        loadData()
    }

    private fun loadData(forceRefresh: Boolean = false) {
        launch {
            val item = if (forceRefresh) {
                interactor.refreshItemDetails(params.itemId)
            } else {
                interactor.getItemDetails(params.itemId)
            }
            scheduleController.clearIfIdentityChanged(item)
            currentItem = item
            val previousCastCards = (stateValue as? DetailsScreenState.Content)
                ?.info
                ?.castCards
                .orEmpty()
            val bookmarkMode = bookmarkPreferencesRepository.mode.value
            val bookmarkState = interactor.getBookmarkState(item, bookmarkMode)
            val mapped = scheduleController.map(
                item = item,
                isInWatchlist = bookmarkState.isInWatchLaterFolder,
            )
            updateViewState(
                preserveCastPhotos(mapped, previousCastCards).copy(
                    isBookmarked = bookmarkState.isBookmarked,
                    bookmarkMode = bookmarkMode,
                    seasonsPanelVisible = mapped.initialEpisodeFocusId != null,
                ),
            )
            startCastEnrichment(item)
            loadSimilarItems()
            scheduleController.load(
                item = item,
                currentItem = { currentItem },
                launchRequest = { block -> launch { block() } },
                onScheduleChanged = {
                    remapCurrentItem(refreshCastEnrichment = false)
                },
            )
        }
    }

    private fun loadSimilarItems() {
        launch {
            runCatching { interactor.getSimilarItems(params.itemId) }
                .onSuccess { items ->
                    updateViewState<DetailsScreenState.Content> {
                        copy(
                            similarItems = mapper.mapSimilarItems(
                                items.filterNot { item -> item.id == params.itemId }
                            )
                        )
                    }
                }
        }
    }

    override fun onAction(action: UIAction) {
        if (closing) return
        when (action) {
            is DetailsAction.PlayClicked -> openPlayer(params.itemId)
            is DetailsAction.TrailerClicked -> showTrailer()
            is DetailsAction.CloseTrailer -> hideTrailer()
            is DetailsAction.SelectSeasonClicked -> showSeasonsPanel()
            is DetailsAction.ScheduleClicked -> openEpisodeSchedule()
            is DetailsAction.WatchlistToggleClicked -> onWatchlistToggle()
            is DetailsAction.BookmarkToggleClicked -> onBookmarkToggle()
            is DetailsAction.WatchedToggleClicked -> onWatchedToggle()
            is DetailsAction.EpisodeSelected -> onEpisodeSelected(action.item)
            is DetailsAction.EpisodeWatchedChanged -> onEpisodeWatchedChanged(action.item, action.watched)
            is DetailsAction.SeasonWatchedChanged -> onSeasonWatchedChanged(action.item, action.watched)
            is DetailsAction.SimilarSelected -> openDetails(action.item.id)
            is DetailsAction.CastMemberSelected -> openActorItems(action.actorQuery)
            is DetailsAction.CloseSeasonsPanel -> hideSeasonsPanel()
            is CommonAction.ItemSelected<*> -> {
                val item = action.item as VideoItemUIState
                openDetails(item.id)
            }
            is CommonAction.ItemPlayed<*> -> {
                val item = action.item as VideoItemUIState
                openPlayer(item.id)
            }
            is CommonAction.ItemSavedChanged<*> -> {
                val item = action.item as VideoItemUIState
                setSimilarItemSaved(item, action.isSaved)
            }
            is CommonAction.ItemBookmarksRequested<*> ->
                router.openBookmarkPicker(
                    item = action.item as VideoItemUIState,
                    listener = ::onSimilarBookmarkPickerResult,
                )
            is CommonAction.RetryClicked -> loadData()
            else -> super.onAction(action)
        }
    }

    private fun showSeasonsPanel() {
        updateViewState<DetailsScreenState.Content> {
            copy(seasonsPanelVisible = true)
        }
    }

    private fun hideSeasonsPanel() {
        updateViewState<DetailsScreenState.Content> {
            copy(seasonsPanelVisible = false)
        }
    }

    private fun onEpisodeSelected(episodeItem: VideoItemUIState) {
        val item = currentItem ?: return
        val seasons = item.seasons ?: return
        for (season in seasons) {
            val episode = season.episodes?.find { it.id == episodeItem.id }
            if (episode != null) {
                openPlayer(params.itemId, season.number, episode.number)
                return
            }
        }
    }

    private fun onEpisodeWatchedChanged(episodeItem: VideoItemUIState, watched: Boolean) {
        val season = episodeItem.seasonNumber ?: return
        val episode = episodeItem.episodeNumber ?: return
        launchMutation {
            val update = interactor.setEpisodeWatched(params.itemId, season, episode, watched)
            contentChanges = contentChanges.withChange(params.itemId, ContentChangeType.Watched)
            applyEpisodeWatched(season, episode, update.isWatched)
            refreshAfterMutation()
            showMessage(
                resources.getString(
                    if (update.isWatched) {
                        R.string.context_menu_episode_watched
                    } else {
                        R.string.context_menu_episode_unwatched
                    }
                )
            )
        }
    }

    private fun onSeasonWatchedChanged(episodeItem: VideoItemUIState, watched: Boolean) {
        val season = episodeItem.seasonNumber ?: return
        launchMutation {
            val update = interactor.setSeasonWatched(params.itemId, season, watched)
            contentChanges = contentChanges.withChange(params.itemId, ContentChangeType.Watched)
            applySeasonWatched(season, update.isWatched)
            refreshAfterMutation()
            showMessage(
                resources.getString(
                    if (update.isWatched) {
                        R.string.context_menu_season_watched
                    } else {
                        R.string.context_menu_season_unwatched
                    }
                )
            )
        }
    }

    private fun updateCurrentItem(
        item: Item,
        isInWatchlist: Boolean,
        isBookmarked: Boolean? = null,
        isWatched: Boolean? = null,
        refreshCastEnrichment: Boolean = true,
    ) {
        val state = stateValue as? DetailsScreenState.Content
        scheduleController.clearIfIdentityChanged(item)
        val mapped = scheduleController.map(item = item, isInWatchlist = isInWatchlist)
        currentItem = item
        updateViewState(
            preserveCastPhotos(mapped, state?.info?.castCards.orEmpty()).copy(
                isInWatchlist = isInWatchlist,
                isBookmarked = isBookmarked ?: state?.isBookmarked ?: mapped.isBookmarked,
                bookmarkMode = bookmarkPreferencesRepository.mode.value,
                isWatched = isWatched ?: mapped.isWatched,
                seasonsPanelVisible = state?.seasonsPanelVisible ?: false,
                similarItems = state?.similarItems.orEmpty(),
                trailerUrl = state?.trailerUrl,
            )
        )
        if (refreshCastEnrichment) {
            startCastEnrichment(item)
        }
    }

    private fun preserveCastPhotos(
        mapped: DetailsScreenState.Content,
        previousCastCards: List<DetailsCastMemberUIState>,
    ): DetailsScreenState.Content {
        if (previousCastCards.isEmpty()) return mapped
        val previousPhotos = previousCastCards
            .filter { card -> card.photoUrl != null }
            .associateBy { card -> card.actorQuery }
        if (previousPhotos.isEmpty()) return mapped
        return mapped.copy(
            info = mapped.info.copy(
                castCards = mapped.info.castCards.map { card ->
                    previousPhotos[card.actorQuery]?.photoUrl?.let { photoUrl ->
                        card.copy(photoUrl = photoUrl)
                    } ?: card
                },
            ),
        )
    }

    private fun startCastEnrichment(item: Item) {
        val generation = ++castEnrichmentGeneration
        castEnrichmentJob?.cancel()
        val imdbId = item.imdb?.trim()?.takeIf(String::isNotEmpty) ?: return
        val castCards = (stateValue as? DetailsScreenState.Content)
            ?.info
            ?.castCards
            .orEmpty()
        if (castCards.isEmpty()) return

        castEnrichmentJob = launch {
            try {
                val tmdbCast = interactor.getTmdbCast(imdbId)
                if (tmdbCast.isEmpty()) return@launch
                if (
                    generation != castEnrichmentGeneration ||
                    currentItem?.id != item.id ||
                    currentItem?.imdb?.trim() != imdbId
                ) {
                    return@launch
                }
                updateViewState<DetailsScreenState.Content> {
                    copy(
                        info = info.copy(
                            castCards = mapper.enrichCastCards(castCards, tmdbCast),
                        ),
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // TMDB enrichment is optional; keep the KinoPub details content intact.
            }
        }
    }

    private fun openActorItems(actorQuery: String) {
        router.navigateTo(router.screens.actorItems(actorQuery))
    }

    private fun showTrailer() {
        val trailerUrl = currentItem?.trailer?.url ?: currentItem?.trailer?.file ?: return
        updateViewState<DetailsScreenState.Content> {
            copy(trailerUrl = trailerUrl)
        }
    }

    private fun hideTrailer() {
        updateViewState<DetailsScreenState.Content> {
            copy(trailerUrl = null)
        }
    }

    override fun onBackPressed() {
        if (closing) {
            router.addBackDispatcher(this)
            return
        }
        val state = stateValue as? DetailsScreenState.Content
        when {
            state?.trailerUrl != null -> hideTrailer()
            state?.seasonsPanelVisible == true -> hideSeasonsPanel()
            else -> {
                closeDetails()
                return
            }
        }
        router.addBackDispatcher(this)
    }

    private fun onWatchlistToggle() {
        if (currentItem?.type?.isSeriesLike() != true) return
        val previous = (stateValue as? DetailsScreenState.Content)?.isInWatchlist ?: return
        val desired = !previous
        updateViewState<DetailsScreenState.Content> {
            copy(isInWatchlist = desired)
        }
        launchMutation {
            try {
                updateSeriesWatchlist(desired)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                updateViewState<DetailsScreenState.Content> {
                    copy(isInWatchlist = previous)
                }
                throw error
            }
        }
    }

    private fun onBookmarkToggle() {
        val item = currentItem
        when {
            item == null -> Unit
            bookmarkPreferencesRepository.mode.value == BookmarkMode.Extended ->
                router.openBookmarkPicker(
                    itemId = item.id,
                    listener = ::onCurrentBookmarkPickerResult,
                )
            !item.type.isSeriesLike() -> toggleSimpleMovieBookmark()
        }
    }

    private fun toggleSimpleMovieBookmark() {
        val previous = (stateValue as? DetailsScreenState.Content)?.isBookmarked ?: return
        updateViewState<DetailsScreenState.Content> { copy(isBookmarked = !previous) }
        launchMutation {
            try {
                updateMovieBookmark(previous)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                updateViewState<DetailsScreenState.Content> { copy(isBookmarked = previous) }
                throw error
            }
        }
    }

    private suspend fun updateSeriesWatchlist(desired: Boolean) {
        val isInWatchlist = savedItemInteractor.setSaved(
            itemId = params.itemId,
            isSeriesLike = true,
            saved = desired,
        ).getOrThrow()
        contentChanges = contentChanges.withChange(params.itemId, ContentChangeType.Watchlist)
        updateViewState<DetailsScreenState.Content> {
            copy(isInWatchlist = isInWatchlist)
        }
        currentItem = currentItem?.copy(inWatchlist = isInWatchlist)
        refreshAfterMutation(isInWatchlist = isInWatchlist)
        showMessage(
            resources.getString(
                if (isInWatchlist) {
                    R.string.video_details_watchlist_added
                } else {
                    R.string.video_details_watchlist_removed
                }
            )
        )
    }

    private suspend fun updateMovieBookmark(previous: Boolean) {
        val update = interactor.setMovieBookmarked(params.itemId, bookmarked = !previous)
        contentChanges = contentChanges.withChange(params.itemId, ContentChangeType.Bookmark)
        updateViewState<DetailsScreenState.Content> {
            copy(isBookmarked = update.isBookmarked)
        }
        refreshAfterMutation(isBookmarked = update.isBookmarked)
        showMessage(
            resources.getString(
                if (update.isBookmarked) {
                    R.string.video_details_bookmark_added_to_folder
                } else {
                    R.string.video_details_bookmark_removed_from_folder
                },
                update.folderTitle ?: WatchLaterBookmarkInteractor.FOLDER_TITLE,
            )
        )
    }

    private fun onWatchedToggle() {
        if (currentItem?.type?.isSeriesLike() == true) return
        val previous = (stateValue as? DetailsScreenState.Content)?.isWatched ?: return
        updateViewState<DetailsScreenState.Content> {
            copy(isWatched = !isWatched)
        }
        launchMutation {
            try {
                val update = interactor.setMovieWatched(params.itemId, watched = !previous)
                contentChanges = contentChanges.withChange(params.itemId, ContentChangeType.Watched)
                updateViewState<DetailsScreenState.Content> {
                    copy(isWatched = update.isWatched)
                }
                currentItem = currentItem?.copy(watched = update.isWatched.toStatus())
                refreshAfterMutation(isWatched = update.isWatched)
                val messageRes = if (update.isWatched) {
                    R.string.video_details_watched_added
                } else {
                    R.string.video_details_watched_removed
                }
                showMessage(resources.getString(messageRes))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                updateViewState<DetailsScreenState.Content> {
                    copy(isWatched = previous)
                }
                throw error
            }
        }
    }

    private fun setSimilarItemSaved(item: VideoItemUIState, saved: Boolean) {
        updateSimilarItemSaved(item.id, saved)
        launchMutation {
            try {
                val actualSaved = savedItemInteractor.setSaved(
                    itemId = item.id,
                    isSeriesLike = item.isSeriesLike,
                    saved = saved,
                ).getOrThrow()
                contentChanges = contentChanges.withChange(
                    itemId = item.id,
                    type = if (item.isSeriesLike) {
                        ContentChangeType.Watchlist
                    } else {
                        ContentChangeType.Bookmark
                    },
                )
                updateSimilarItemSaved(item.id, actualSaved)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                updateSimilarItemSaved(item.id, item.isSaved)
                throw error
            }
        }
    }

    private fun updateSimilarItemSaved(itemId: Int, saved: Boolean) {
        updateViewState<DetailsScreenState.Content> {
            copy(
                similarItems = similarItems.map { item ->
                    if (item.id == itemId) item.copy(isSaved = saved) else item
                },
            )
        }
    }

    private fun onCurrentBookmarkPickerResult(result: BookmarkPickerResult?) {
        if (result == null || result.itemId != params.itemId) return
        updateViewState<DetailsScreenState.Content> { copy(isBookmarked = result.isBookmarked) }
        contentChanges = contentChanges.withChange(result.itemId, ContentChangeType.Bookmark)
    }

    private fun onSimilarBookmarkPickerResult(result: BookmarkPickerResult?) {
        result ?: return
        updateViewState<DetailsScreenState.Content> { withBookmarkPickerResult(result) }
        contentChanges = contentChanges.withChange(result.itemId, ContentChangeType.Bookmark)
    }

    private fun openPlayer(itemId: Int, seasonNumber: Int? = null, episodeNumber: Int? = null) {
        router.navigateForResult<ContentChangeSet>(
            screen = router.screens.player(itemId, seasonNumber, episodeNumber),
            requestCode = RESULT_CONTENT_CHANGED,
            listener = ::onReturnedContentChanges,
        )
    }

    private fun openEpisodeSchedule() {
        val item = currentItem ?: return
        val scheduleParams = scheduleController.screenParams(item) ?: return
        router.navigateTo(
            router.screens.episodeSchedule(scheduleParams),
        )
    }

    private fun openDetails(itemId: Int) {
        router.navigateForResult<ContentChangeSet>(
            screen = router.screens.details(itemId),
            requestCode = RESULT_CONTENT_CHANGED,
            listener = ::onReturnedContentChanges,
        )
    }

    private fun onReturnedContentChanges(changes: ContentChangeSet?) {
        if (changes == null || changes.isEmpty) return
        contentChanges = contentChanges.merge(changes)
        if (changes.affectsItem(params.itemId)) {
            loadData(forceRefresh = true)
            return
        }

        val content = stateValue as? DetailsScreenState.Content ?: return
        if (content.similarItems.any { item -> changes.affectsItem(item.id) }) {
            loadSimilarItems()
        }
    }

    private fun launchMutation(block: suspend CoroutineScope.() -> Unit): Job {
        lateinit var job: Job
        job = launch(start = CoroutineStart.LAZY) {
            try {
                mutationMutex.withLock {
                    block()
                }
            } finally {
                pendingMutations.remove(job)
            }
        }
        pendingMutations += job
        job.start()
        return job
    }

    private suspend fun awaitPendingMutations() {
        while (true) {
            val activeJobs = pendingMutations.filter(Job::isActive)
            if (activeJobs.isEmpty()) return
            activeJobs.joinAll()
        }
    }

    private fun closeDetails() {
        if (closeJob != null) return
        closing = true
        router.addBackDispatcher(this)
        closeJob = launch {
            awaitPendingMutations()
            router.removeBackDispatcher(this@DetailsVM)
            router.back(RESULT_CONTENT_CHANGED, contentChanges)
        }
    }

    private suspend fun refreshAfterMutation(
        isInWatchlist: Boolean? = null,
        isBookmarked: Boolean? = null,
        isWatched: Boolean? = null,
    ) {
        val item = try {
            interactor.refreshItemDetails(params.itemId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            return
        }
        val resolvedWatchlist = if (item.type.isSeriesLike() && isInWatchlist != null) {
            isInWatchlist
        } else {
            try {
                interactor.getBookmarkState(item, bookmarkPreferencesRepository.mode.value)
                    .isInWatchLaterFolder
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                isInWatchlist ?: (stateValue as? DetailsScreenState.Content)?.isInWatchlist ?: false
            }
        }
        updateCurrentItem(
            item = item,
            isInWatchlist = resolvedWatchlist,
            isBookmarked = isBookmarked,
            isWatched = isWatched,
        )
    }

    private fun applyEpisodeWatched(seasonNumber: Int, episodeNumber: Int, watched: Boolean) {
        val item = currentItem ?: return
        currentItem = item.copy(
            seasons = item.seasons?.map { season ->
                if (season.number != seasonNumber) {
                    season
                } else {
                    season.copy(
                        episodes = season.episodes?.map { episode ->
                            if (episode.number == episodeNumber) {
                                episode.copy(watched = watched.toStatus())
                            } else {
                                episode
                            }
                        }
                    )
                }
            }
        )
        remapCurrentItem()
    }

    private fun applySeasonWatched(seasonNumber: Int, watched: Boolean) {
        val item = currentItem ?: return
        currentItem = item.copy(
            seasons = item.seasons?.map { season ->
                if (season.number != seasonNumber) {
                    season
                } else {
                    season.copy(
                        episodes = season.episodes?.map { episode ->
                            episode.copy(watched = watched.toStatus())
                        }
                    )
                }
            }
        )
        remapCurrentItem()
    }

    private fun remapCurrentItem(refreshCastEnrichment: Boolean = true) {
        val item = currentItem ?: return
        val watchlist = (stateValue as? DetailsScreenState.Content)?.isInWatchlist ?: false
        updateCurrentItem(
            item = item,
            isInWatchlist = watchlist,
            refreshCastEnrichment = refreshCastEnrichment,
        )
    }

}

private const val WATCHED_STATUS = 1
private const val UNWATCHED_STATUS = 0

private fun Boolean.toStatus(): Int = if (this) WATCHED_STATUS else UNWATCHED_STATUS

private fun DetailsScreenState.Content.withBookmarkPickerResult(
    result: BookmarkPickerResult,
): DetailsScreenState.Content {
    return copy(similarItems = similarItems.map { item -> item.withBookmarkResult(result) })
}

private fun ContentChangeSet.withChange(itemId: Int, type: ContentChangeType): ContentChangeSet {
    return merge(ContentChange(itemId, type))
}
