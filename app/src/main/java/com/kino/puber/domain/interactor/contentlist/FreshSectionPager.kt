package com.kino.puber.domain.interactor.contentlist

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.api.models.Pagination
import com.kino.puber.data.api.models.isAnime
import com.kino.puber.ui.feature.contentlist.model.AnimeFilterMode
import com.kino.puber.ui.feature.contentlist.model.SectionConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class FreshSectionPager(
    private val api: KinoPubApiClient,
    private val config: SectionConfig,
) {

    private val mutex = Mutex()
    private var committedState = initialState()

    suspend fun loadPage(requestedPage: Int): PaginatedResponse<Item> = mutex.withLock {
        require(requestedPage > 0) { "Fresh logical page must be positive" }
        val workingState = if (requestedPage == FIRST_PAGE) {
            initialState()
        } else {
            committedState.deepCopy()
        }
        check(requestedPage == workingState.nextLogicalPage) {
            "Fresh logical page $requestedPage did not match expected page " +
                workingState.nextLogicalPage
        }

        ensureTargetSize(workingState)
        val targetSize = checkNotNull(workingState.targetPageSize)
        check(targetSize > 0) {
            "Fresh source page capacity must be positive"
        }
        val pageItems = mutableListOf<Item>()
        while (pageItems.size < targetSize) {
            drainBuffers(workingState, pageItems, targetSize)
            if (pageItems.size >= targetSize || workingState.sources.values.all(SourceState::isDone)) {
                break
            }
            fetchEmptySources(workingState)
        }

        val hasMore = workingState.sources.values.any { source ->
            source.buffer.isNotEmpty() || source.exhausted.not()
        }
        workingState.nextLogicalPage += 1
        committedState = workingState
        PaginatedResponse(
            items = pageItems,
            pagination = Pagination(
                current = requestedPage,
                perpage = targetSize,
                total = if (hasMore) requestedPage + 1 else requestedPage,
            ),
        )
    }

    suspend fun reset() = mutex.withLock {
        committedState = initialState()
    }

    private suspend fun ensureTargetSize(state: PagerState) {
        if (state.targetPageSize != null) return
        fetchSources(state, config.shortcutTypes)
        val firstSource = state.sources.getValue(config.shortcutTypes.first())
        state.targetPageSize = firstSource.pageSize
    }

    private suspend fun fetchEmptySources(state: PagerState) {
        val types = config.shortcutTypes.filter { type ->
            state.sources.getValue(type).let { source ->
                source.buffer.isEmpty() && source.exhausted.not()
            }
        }
        check(types.isNotEmpty()) {
            "Fresh pager could not refill an incomplete logical page"
        }
        fetchSources(state, types)
    }

    private suspend fun fetchSources(state: PagerState, types: List<ItemType>) {
        val shortcut = checkNotNull(config.shortcut)
        val results = types.associateWith { type ->
            val source = state.sources.getValue(type)
            api.getItemsByShortcut(shortcut, type.value, source.nextPage, null)
        }
        types.forEach { type ->
            val source = state.sources.getValue(type)
            val requestedSourcePage = source.nextPage
            val response = results.getValue(type).getOrThrow()
            check(response.pagination.current == requestedSourcePage) {
                "Fresh ${type.value} pagination current ${response.pagination.current} " +
                    "did not match requested page $requestedSourcePage"
            }
            check(response.pagination.total >= response.pagination.current) {
                "Fresh ${type.value} pagination total ${response.pagination.total} " +
                    "was before current page ${response.pagination.current}"
            }
            source.pageSize = response.pagination.perpage.coerceAtLeast(response.items.size)
            response.items
                .asSequence()
                .filter(::matchesSection)
                .filter { state.seenIds.add(it.id) }
                .forEach(source.buffer::add)
            source.nextPage = response.pagination.current + 1
            source.exhausted = response.pagination.current >= response.pagination.total
        }
    }

    private fun drainBuffers(
        state: PagerState,
        destination: MutableList<Item>,
        targetSize: Int,
    ) {
        while (destination.size < targetSize) {
            val preferredIndex = state.nextSourceIndex
            val sourceIndex = config.shortcutTypes.indices.firstOrNull { offset ->
                val index = (preferredIndex + offset) % config.shortcutTypes.size
                state.sources.getValue(config.shortcutTypes[index]).buffer.isNotEmpty()
            }?.let { offset ->
                (preferredIndex + offset) % config.shortcutTypes.size
            } ?: return
            val source = state.sources.getValue(config.shortcutTypes[sourceIndex])
            destination += source.buffer.removeAt(0)
            state.nextSourceIndex = (sourceIndex + 1) % config.shortcutTypes.size
        }
    }

    private fun matchesSection(item: Item): Boolean {
        val hasRequiredGenre = config.requiredGenreId?.let { requiredGenreId ->
            item.genres.orEmpty().any { genre -> genre.id == requiredGenreId }
        } ?: true
        if (hasRequiredGenre.not()) return false
        return when (config.animeFilterMode) {
            AnimeFilterMode.None,
            AnimeFilterMode.FollowPreference -> true
            AnimeFilterMode.Exclude -> item.isAnime().not()
            AnimeFilterMode.Only -> item.isAnime()
        }
    }

    private fun initialState(): PagerState {
        require(config.shortcut != null) { "Fresh pager requires a shortcut" }
        require(config.shortcutTypes.isNotEmpty()) { "Fresh pager requires shortcut types" }
        require(config.shortcutTypes.distinct().size == config.shortcutTypes.size) {
            "Fresh pager shortcut types must be unique"
        }
        require(config.requiredGenreId != null) { "Fresh pager requires a local genre" }
        return PagerState(
            sources = config.shortcutTypes.associateWith { SourceState() }.toMutableMap(),
        )
    }

    private data class PagerState(
        val sources: MutableMap<ItemType, SourceState>,
        val seenIds: MutableSet<Int> = linkedSetOf(),
        var nextSourceIndex: Int = 0,
        var nextLogicalPage: Int = FIRST_PAGE,
        var targetPageSize: Int? = null,
    ) {
        fun deepCopy() = PagerState(
            sources = sources
                .mapValuesTo(linkedMapOf()) { (_, source) -> source.deepCopy() },
            seenIds = seenIds.toMutableSet(),
            nextSourceIndex = nextSourceIndex,
            nextLogicalPage = nextLogicalPage,
            targetPageSize = targetPageSize,
        )
    }

    private data class SourceState(
        var nextPage: Int = FIRST_PAGE,
        var exhausted: Boolean = false,
        var pageSize: Int = 0,
        val buffer: MutableList<Item> = mutableListOf(),
    ) {
        val isDone: Boolean
            get() = exhausted && buffer.isEmpty()

        fun deepCopy() = copy(buffer = buffer.toMutableList())
    }

    private companion object {
        const val FIRST_PAGE = 1
    }
}
