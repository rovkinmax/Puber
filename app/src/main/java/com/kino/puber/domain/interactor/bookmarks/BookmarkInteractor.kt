package com.kino.puber.domain.interactor.bookmarks

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.Bookmark
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.PaginatedResponse

class BookmarkInteractor(private val api: KinoPubApiClient) {

    suspend fun getBookmarks(): Result<List<Bookmark>> {
        return api.getBookmarks()
    }

    suspend fun getBookmarkItems(id: Int, page: Int): Result<PaginatedResponse<Item>> {
        return api.getBookmarkItems(id, page)
    }
}
