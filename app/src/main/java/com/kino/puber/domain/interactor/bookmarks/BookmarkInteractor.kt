package com.kino.puber.domain.interactor.bookmarks

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.Bookmark
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.repository.ItemDetailsRepository

class BookmarkInteractor(
    private val api: KinoPubApiClient,
    private val itemDetailsRepository: ItemDetailsRepository,
) {

    suspend fun getBookmarks(): List<Bookmark> {
        return api.getBookmarks().getOrThrow()
    }

    suspend fun getBookmarkItems(id: Int, page: Int): PaginatedResponse<Item> {
        return api.getBookmarkItems(id, page).getOrThrow()
    }

    suspend fun setItemSaved(itemId: Int, folderId: Int, saved: Boolean) {
        if (saved) {
            api.addBookmarkItem(itemId = itemId, folderId = folderId).getOrThrow()
        } else {
            api.removeBookmarkItem(itemId = itemId, folderId = folderId).getOrThrow()
        }
        itemDetailsRepository.invalidate(itemId)
    }
}
