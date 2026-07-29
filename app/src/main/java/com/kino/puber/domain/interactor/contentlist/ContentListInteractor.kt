package com.kino.puber.domain.interactor.contentlist

import com.kino.puber.core.collections.TypedTtlCache
import com.kino.puber.core.collections.TypedTtlCacheImpl
import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.config.KinoPubConfig
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.api.models.isAnime
import com.kino.puber.data.preferences.NavigationPreferencesRepository
import com.kino.puber.ui.feature.contentlist.model.AnimeFilterMode
import com.kino.puber.ui.feature.contentlist.model.SectionConfig
import kotlin.time.Duration.Companion.minutes

internal class ContentListInteractor(
    private val api: KinoPubApiClient,
    private val navigationPreferencesRepository: NavigationPreferencesRepository,
) {

    private val detailedItemsCache: TypedTtlCache<String, Item> = TypedTtlCacheImpl()

    suspend fun loadPage(config: SectionConfig, page: Int): PaginatedResponse<Item> {
        val showAnime = navigationPreferencesRepository.contentPreferences.value.showAnime
        if (page == 1) {
            val cacheKey = listOf(
                KinoPubConfig.CURRENT_API_DOMAIN,
                config.id,
                config.shortcut.orEmpty(),
                config.type,
                config.sort,
                config.quality,
                config.genre.orEmpty(),
                config.animeFilterMode,
                showAnime,
            ).joinToString(separator = "_")
            return firstPageCache.getOrPut(cacheKey) {
                fetchFilteredPage(config, page, showAnime)
            }
        }
        return fetchFilteredPage(config, page, showAnime)
    }

    private suspend fun fetchFilteredPage(
        config: SectionConfig,
        requestedPage: Int,
        showAnime: Boolean,
    ): PaginatedResponse<Item> {
        val filterMode = config.animeFilterMode
        if (filterMode == AnimeFilterMode.None ||
            filterMode == AnimeFilterMode.FollowPreference && showAnime
        ) {
            return fetchPage(config, requestedPage)
        }

        var response = fetchPage(config, requestedPage)
        val targetSize = response.pagination.perpage.coerceAtLeast(response.items.size)
        val visibleItems = linkedMapOf<Int, Item>()
        while (true) {
            response.items
                .asSequence()
                .filter { item ->
                    when (filterMode) {
                        AnimeFilterMode.None -> true
                        AnimeFilterMode.FollowPreference,
                        AnimeFilterMode.Exclude -> item.isAnime().not()
                        AnimeFilterMode.Only -> item.isAnime()
                    }
                }
                .forEach { item ->
                    visibleItems.putIfAbsent(item.id, item)
                }

            if (visibleItems.size >= targetSize ||
                response.pagination.current >= response.pagination.total ||
                targetSize == 0
            ) {
                return response.copy(items = visibleItems.values.take(targetSize))
            }
            response = fetchPage(config, response.pagination.current + 1)
        }
    }

    private suspend fun fetchPage(config: SectionConfig, page: Int): PaginatedResponse<Item> {
        val result = when {
            config.shortcut != null ->
                api.getItemsByShortcut(config.shortcut, config.type, page, config.genre)
            else ->
                api.getItems(config.type, config.sort, page, config.quality, config.genre)
        }
        return result.getOrThrow()
    }

    suspend fun getItemDetails(id: Int): Item {
        return detailedItemsCache.getOrPut(itemDetailsCacheKey(id)) {
            api.getItemDetails(id).getOrThrow().item!!
        }
    }

    fun invalidateFirstPageCache() {
        firstPageCache.clear()
    }

    fun invalidateItemDetails(id: Int) {
        detailedItemsCache.remove(itemDetailsCacheKey(id))
    }

    private fun itemDetailsCacheKey(id: Int): String {
        return "${KinoPubConfig.CURRENT_API_DOMAIN}_$id"
    }

    companion object {
        private val firstPageCache = TypedTtlCacheImpl<String, PaginatedResponse<Item>>(
            defaultTtl = 3.minutes,
        )
    }
}
