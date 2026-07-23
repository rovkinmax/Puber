package com.kino.puber.domain.interactor.bookmarks

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.repository.ItemDetailsRepository
import kotlinx.coroutines.CancellationException

class SavedItemInteractor(
    private val api: KinoPubApiClient,
    private val watchLaterBookmarkInteractor: WatchLaterBookmarkInteractor,
    private val itemDetailsRepository: ItemDetailsRepository,
) {

    suspend fun setSaved(itemId: Int, isSeriesLike: Boolean, saved: Boolean): Result<Boolean> {
        return resultOf {
            when {
                isSeriesLike -> setSeriesSaved(itemId, saved)
                saved -> {
                    watchLaterBookmarkInteractor.add(itemId).getOrThrow()
                    itemDetailsRepository.invalidate(itemId)
                    true
                }
                else -> removeFromAnyBookmark(itemId)
            }
        }
    }

    private suspend fun setSeriesSaved(itemId: Int, saved: Boolean): Boolean {
        val response = api.getWatchingList(onlySubscribed = true).getOrThrow()
        val current = response.items.orEmpty().any { item -> item.id == itemId }
        return if (current == saved) {
            saved
        } else {
            api.toggleWatchlist(itemId).getOrThrow().watching.also {
                itemDetailsRepository.invalidate(itemId)
            }
        }
    }

    private suspend fun removeFromAnyBookmark(itemId: Int): Boolean {
        val folders = api.getItemBookmarkFolders(itemId).getOrThrow()
        val folder = folders.firstOrNull() ?: return false
        api.removeBookmarkItem(itemId = itemId, folderId = folder.id).getOrThrow()
        itemDetailsRepository.invalidate(itemId)

        val verification = api.getItemBookmarkFolders(itemId)
        verification.exceptionOrNull()?.let { error ->
            if (error is CancellationException) throw error
            return folders.size > 1
        }
        return verification.getOrThrow().isNotEmpty()
    }

    private suspend fun <T> resultOf(block: suspend () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }
}
