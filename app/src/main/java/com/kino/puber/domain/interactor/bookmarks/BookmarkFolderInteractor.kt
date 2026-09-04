package com.kino.puber.domain.interactor.bookmarks

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.Bookmark
import com.kino.puber.data.api.models.BookmarkFolder
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.preferences.BookmarkPreferencesRepository
import com.kino.puber.data.repository.ItemDetailsRepository

class BookmarkFolderInteractor(
    private val api: KinoPubApiClient,
    private val preferences: BookmarkPreferencesRepository,
    private val itemDetailsRepository: ItemDetailsRepository,
) {

    suspend fun getFolders(): List<Bookmark> {
        return api.getBookmarks().getOrThrow()
    }

    suspend fun getItemFolders(itemId: Int): List<BookmarkFolder> {
        return api.getItemBookmarkFolders(itemId).getOrThrow()
    }

    suspend fun createFolder(title: String): Bookmark {
        val normalizedTitle = title.trim()
        require(normalizedTitle.isNotEmpty()) { "Bookmark folder title must not be empty" }
        return api.createBookmark(normalizedTitle).getOrThrow()
    }

    suspend fun setItemInFolder(itemId: Int, folderId: Int, selected: Boolean) {
        if (selected) {
            api.addBookmarkItem(itemId = itemId, folderId = folderId).getOrThrow()
        } else {
            api.removeBookmarkItem(itemId = itemId, folderId = folderId).getOrThrow()
        }
        itemDetailsRepository.invalidate(itemId)
    }

    suspend fun getQuickFolder(): Bookmark? {
        return resolveQuickFolder(getFolders())
    }

    /** Resolves the quick folder against an already-fetched folder list, without a further request. */
    fun resolveQuickFolder(folders: List<Bookmark>): Bookmark? {
        val configuredId = preferences.quickFolderId.value
        folders.firstOrNull { it.id == configuredId }?.let { return it }

        if (configuredId != null) {
            preferences.setQuickFolderId(null)
        }
        return folders.firstOrNull { it.title == LEGACY_QUICK_FOLDER_TITLE }
            ?.also { folder -> preferences.setQuickFolderId(folder.id) }
    }

    suspend fun ensureQuickFolder(): Bookmark {
        getQuickFolder()?.let { return it }
        return createFolder(LEGACY_QUICK_FOLDER_TITLE)
            .also { folder -> preferences.setQuickFolderId(folder.id) }
    }

    suspend fun isInQuickFolder(itemId: Int): Boolean {
        val quickFolder = getQuickFolder() ?: return false
        return getItemFolders(itemId).any { folder -> folder.id == quickFolder.id }
    }

    suspend fun setQuickSaved(itemId: Int, saved: Boolean): QuickBookmarkUpdate {
        val folder = if (saved) ensureQuickFolder() else getQuickFolder()
        if (folder == null) {
            // Un-saving with no quick folder: there is nothing to remove from, and the item was
            // never reported as saved either — `VideoItemUIMapper` and `DetailsInteractor` both
            // read "saved" as quick-folder membership. Membership of other folders is the
            // picker's business and is deliberately left alone.
            return QuickBookmarkUpdate(isSaved = false, folder = null)
        }
        setItemInFolder(itemId = itemId, folderId = folder.id, selected = saved)
        return QuickBookmarkUpdate(isSaved = saved, folder = folder)
    }

    suspend fun getQuickFolderItems(): QuickBookmarkItems {
        val folder = getQuickFolder()
            ?: return QuickBookmarkItems(folder = null, items = emptyList())
        val items = api.getBookmarkItems(folder.id).getOrThrow().items
        return QuickBookmarkItems(folder = folder, items = items)
    }

    companion object {
        const val LEGACY_QUICK_FOLDER_TITLE = "Буду смотреть"
    }
}

data class QuickBookmarkUpdate(
    val isSaved: Boolean,
    val folder: Bookmark?,
)

data class QuickBookmarkItems(
    val folder: Bookmark?,
    val items: List<Item>,
)
