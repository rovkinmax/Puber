package com.kino.puber.domain.interactor.home

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.KCollection
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.api.models.isAnime
import com.kino.puber.data.preferences.NavigationPreferencesRepository
import com.kino.puber.domain.interactor.bookmarks.WatchLaterBookmarkInteractor

class HomeInteractor(
    private val api: KinoPubApiClient,
    private val watchLaterBookmarkInteractor: WatchLaterBookmarkInteractor,
    private val navigationPreferencesRepository: NavigationPreferencesRepository,
) {

    suspend fun getHotItems(type: String = "movie", limit: Int = 10): Result<List<Item>> {
        return getDiscoveryItems(shortcut = "hot", type = type, limit = limit)
    }

    suspend fun getWatchingItems(): Result<List<Item>> {
        return api.getWatchingList(onlySubscribed = true).map { it.items.orEmpty() }
    }

    suspend fun getFreshItems(type: String): Result<List<Item>> {
        return getDiscoveryItems(shortcut = "fresh", type = type)
    }

    suspend fun getPopularByType(type: String): Result<List<Item>> {
        return getDiscoveryItems(shortcut = "popular", type = type)
    }

    suspend fun getWatchLaterItems(): Result<List<Item>> {
        return watchLaterBookmarkInteractor.getItems()
    }

    suspend fun getCollections(): Result<List<KCollection>> {
        return api.getCollections(page = 1).map { it.items }
    }

    private suspend fun getDiscoveryItems(
        shortcut: String,
        type: String,
        limit: Int? = null,
    ): Result<List<Item>> {
        val firstPageResult = api.getItemsByShortcut(shortcut, type = type)
        if (navigationPreferencesRepository.contentPreferences.value.showAnime) {
            return firstPageResult.map { response ->
                response.items.limitTo(limit)
            }
        }

        return firstPageResult.mapCatching { firstPage ->
            val targetSize = limit ?: firstPage.items.size
            val visibleItems = linkedMapOf<Int, Item>()
            var currentRequestedPage = FIRST_PAGE
            var lastPage = firstPage

            while (true) {
                check(lastPage.pagination.current == currentRequestedPage) {
                    "Home discovery pagination current ${lastPage.pagination.current} " +
                        "did not match requested page $currentRequestedPage"
                }
                lastPage.items
                    .asSequence()
                    .filterNot(Item::isAnime)
                    .forEach { item -> visibleItems.putIfAbsent(item.id, item) }
                if (visibleItems.size >= targetSize || !lastPage.hasNextPage()) break
                currentRequestedPage = lastPage.pagination.current + 1
                lastPage = api.getItemsByShortcut(
                    shortcut = shortcut,
                    type = type,
                    page = currentRequestedPage,
                ).getOrThrow()
            }

            visibleItems.values.toList().limitTo(targetSize)
        }
    }

    private fun PaginatedResponse<Item>.hasNextPage(): Boolean {
        return pagination.current < pagination.total
    }

    private fun List<Item>.limitTo(limit: Int?): List<Item> {
        return limit?.let(::take) ?: this
    }

    companion object {
        private const val FIRST_PAGE = 1
    }
}
