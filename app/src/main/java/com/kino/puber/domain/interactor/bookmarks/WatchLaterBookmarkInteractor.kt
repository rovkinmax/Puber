package com.kino.puber.domain.interactor.bookmarks

import com.kino.puber.data.api.models.Bookmark
import com.kino.puber.data.api.models.Item
import kotlinx.coroutines.CancellationException

class WatchLaterBookmarkInteractor(
    private val bookmarkFolderInteractor: BookmarkFolderInteractor,
) {

    suspend fun getItems(): Result<List<Item>> {
        return resultOf {
            bookmarkFolderInteractor.getQuickFolderItems().items
        }
    }

    suspend fun isBookmarked(itemId: Int): Result<Boolean> {
        return resultOf { bookmarkFolderInteractor.isInQuickFolder(itemId) }
    }

    suspend fun add(itemId: Int): Result<Bookmark> {
        return resultOf {
            checkNotNull(bookmarkFolderInteractor.setQuickSaved(itemId, saved = true).folder)
        }
    }

    suspend fun remove(itemId: Int): Result<Unit> {
        return resultOf { bookmarkFolderInteractor.setQuickSaved(itemId, saved = false) }.map { }
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

    companion object {
        const val FOLDER_TITLE = BookmarkFolderInteractor.LEGACY_QUICK_FOLDER_TITLE
    }
}
