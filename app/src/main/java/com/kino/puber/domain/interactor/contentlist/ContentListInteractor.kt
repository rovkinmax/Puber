package com.kino.puber.domain.interactor.contentlist

import com.kino.puber.core.collections.TypedTtlCache
import com.kino.puber.core.collections.TypedTtlCacheImpl
import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.ui.feature.contentlist.model.SectionConfig
import kotlin.time.Duration.Companion.minutes

internal class ContentListInteractor(
    private val api: KinoPubApiClient,
) {

    private val detailedItemsCache: TypedTtlCache<Int, Item> = TypedTtlCacheImpl()

    suspend fun loadPage(config: SectionConfig, page: Int): PaginatedResponse<Item> {
        if (page == 1) {
            val cacheKey = "${config.id}_${config.genre.orEmpty()}"
            return firstPageCache.getOrPut(cacheKey) { fetchPage(config, page) }
        }
        return fetchPage(config, page)
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
        return detailedItemsCache.getOrPut(id) {
            api.getItemDetails(id).getOrThrow().item!!
        }
    }

    companion object {
        private val firstPageCache = TypedTtlCacheImpl<String, PaginatedResponse<Item>>(
            defaultTtl = 3.minutes,
        )
    }
}
