package com.kino.puber.ui.feature.details.vm

import com.kino.puber.R
import com.kino.puber.core.content.ContentChange
import com.kino.puber.core.content.ContentChangeSet
import com.kino.puber.core.content.ContentChangeType
import com.kino.puber.core.error.ErrorEntity
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.system.ResourceProvider
import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.RESULT_CONTENT_CHANGED
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.isSeriesLike
import com.kino.puber.domain.interactor.bookmarks.SavedItemInteractor
import com.kino.puber.domain.interactor.bookmarks.WatchLaterBookmarkInteractor
import com.kino.puber.domain.interactor.details.DetailsInteractor
import com.kino.puber.ui.feature.details.model.DetailsAction
import com.kino.puber.ui.feature.details.model.DetailsScreenParams
import com.kino.puber.ui.feature.details.model.DetailsScreenState
import com.kino.puber.ui.feature.details.model.DetailsScreenUIMapper
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
    private val savedItemInteractor: SavedItemInteractor,
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
    private var contentChanges = ContentChangeSet.empty()
    private val pendingMutations = mutableSetOf<Job>()
    private val mutationMutex = Mutex()
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
            currentItem = item
            updateViewState(mapper.map(item, isInWatchlist = interactor.isInWatchLaterFolder(item)))
            loadSimilarItems()
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
            is DetailsAction.WatchlistToggleClicked -> onWatchlistToggle()
            is DetailsAction.WatchedToggleClicked -> onWatchedToggle()
            is DetailsAction.EpisodeSelected -> onEpisodeSelected(action.item)
            is DetailsAction.EpisodeWatchedChanged -> onEpisodeWatchedChanged(action.item, action.watched)
            is DetailsAction.SeasonWatchedChanged -> onSeasonWatchedChanged(action.item, action.watched)
            is DetailsAction.SimilarSelected -> openDetails(action.item.id)
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
            markContentChanged(params.itemId, ContentChangeType.Watched)
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
            markContentChanged(params.itemId, ContentChangeType.Watched)
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
        isWatched: Boolean? = null,
    ) {
        val state = stateValue as? DetailsScreenState.Content
        val mapped = mapper.map(item, isInWatchlist = isInWatchlist)
        currentItem = item
        updateViewState(
            mapped.copy(
                isInWatchlist = isInWatchlist,
                isWatched = isWatched ?: mapped.isWatched,
                seasonsPanelVisible = state?.seasonsPanelVisible ?: false,
                similarItems = state?.similarItems.orEmpty(),
                trailerUrl = state?.trailerUrl,
            )
        )
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
        val previous = (stateValue as? DetailsScreenState.Content)?.isInWatchlist ?: return
        val desired = !previous
        updateViewState<DetailsScreenState.Content> {
            copy(isInWatchlist = desired)
        }
        launchMutation {
            try {
                if (itemIsSeriesLike()) {
                    updateSeriesWatchlist(desired)
                } else {
                    updateMovieBookmark(previous)
                }
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

    private suspend fun updateSeriesWatchlist(desired: Boolean) {
        val isInWatchlist = savedItemInteractor.setSaved(
            itemId = params.itemId,
            isSeriesLike = true,
            saved = desired,
        ).getOrThrow()
        markContentChanged(params.itemId, ContentChangeType.Watchlist)
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
        markContentChanged(params.itemId, ContentChangeType.Bookmark)
        updateViewState<DetailsScreenState.Content> {
            copy(isInWatchlist = update.isBookmarked)
        }
        refreshAfterMutation(isInWatchlist = update.isBookmarked)
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
        if (itemIsSeriesLike()) return
        val previous = (stateValue as? DetailsScreenState.Content)?.isWatched ?: return
        updateViewState<DetailsScreenState.Content> {
            copy(isWatched = !isWatched)
        }
        launchMutation {
            try {
                val update = interactor.setMovieWatched(params.itemId, watched = !previous)
                markContentChanged(params.itemId, ContentChangeType.Watched)
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
                markContentChanged(
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

    private fun itemIsSeriesLike(): Boolean {
        return currentItem?.type?.isSeriesLike() ?: false
    }

    private fun openPlayer(itemId: Int, seasonNumber: Int? = null, episodeNumber: Int? = null) {
        router.navigateForResult<ContentChangeSet>(
            screen = router.screens.player(itemId, seasonNumber, episodeNumber),
            requestCode = RESULT_CONTENT_CHANGED,
            listener = ::onReturnedContentChanges,
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

    private fun markContentChanged(itemId: Int, type: ContentChangeType) {
        contentChanges = contentChanges.merge(ContentChange(itemId, type))
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
                interactor.isInWatchLaterFolder(item)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                isInWatchlist ?: (stateValue as? DetailsScreenState.Content)?.isInWatchlist ?: false
            }
        }
        updateCurrentItem(
            item = item,
            isInWatchlist = resolvedWatchlist,
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

    private fun remapCurrentItem() {
        val item = currentItem ?: return
        val watchlist = (stateValue as? DetailsScreenState.Content)?.isInWatchlist ?: false
        updateCurrentItem(item, watchlist)
    }

    private fun Boolean.toStatus(): Int = if (this) WATCHED_STATUS else UNWATCHED_STATUS

    private companion object {
        const val WATCHED_STATUS = 1
        const val UNWATCHED_STATUS = 0
    }
}
