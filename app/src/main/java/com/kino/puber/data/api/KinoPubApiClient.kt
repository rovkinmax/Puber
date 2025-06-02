package com.kino.puber.data.api

import com.kino.puber.data.api.config.KinoPubClientConfig
import com.kino.puber.data.api.config.KinoPubConfig
import com.kino.puber.data.api.config.createHttpClient
import com.kino.puber.data.api.config.createOkHttpClient
import com.kino.puber.data.api.models.Bookmark
import com.kino.puber.data.api.models.Comment
import com.kino.puber.data.api.models.Country
import com.kino.puber.data.api.models.DeviceSettings
import com.kino.puber.data.api.models.Genre
import com.kino.puber.data.api.models.History
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.KCollection
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.api.models.UserInfo
import com.kino.puber.data.api.models.WatchingStatus
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType

class KinoPubApiClient(
    private val accessToken: String,
    private val config: KinoPubClientConfig = KinoPubClientConfig()
) {
    private val okHttpClient = createOkHttpClient(config)
    private val httpClient: HttpClient = createHttpClient(config, okHttpClient, accessToken)

    // Content API

    /**
     * Get items (movies, tv-shows, etc.)
     */
    suspend fun getItems(
        type: String? = null,
        sort: String? = null,
        page: Int? = null,
        quality: String? = null,
        genre: String? = null,
        conditions: List<String>? = null
    ): Result<PaginatedResponse<Item>> = apiCall {
        httpClient.get("${KinoPubConfig.MAIN_API_BASE_URL}items") {
            type?.let { parameter("type", it) }
            sort?.let { parameter("sort", it) }
            page?.let { parameter("page", it) }
            quality?.let { parameter("quality", it) }
            genre?.let { parameter("genre", it) }
            conditions?.forEach { parameter("conditions[]", it) }
        }
    }

    /**
     * Get item details by ID
     */
    suspend fun getItemDetails(id: Int): Result<Item> = apiCall {
        httpClient.get("${KinoPubConfig.MAIN_API_BASE_URL}items/$id")
    }

    /**
     * Get items by shortcut
     */
    suspend fun getItemsByShortcut(
        shortcut: String,
        type: String? = null,
        page: Int? = null,
        genre: String? = null
    ): Result<PaginatedResponse<Item>> = apiCall {
        httpClient.get("${KinoPubConfig.MAIN_API_BASE_URL}items/$shortcut") {
            type?.let { parameter("type", it) }
            page?.let { parameter("page", it) }
            genre?.let { parameter("genre", it) }
        }
    }

    /**
     * Search items
     */
    suspend fun searchItems(
        query: String,
        field: String? = null,
        perpage: Int? = null
    ): Result<PaginatedResponse<Item>> = apiCall {
        httpClient.get("${KinoPubConfig.MAIN_API_BASE_URL}items/search") {
            parameter("q", query)
            field?.let { parameter("field", it) }
            perpage?.let { parameter("perpage", it) }
        }
    }

    /**
     * Search by title
     */
    suspend fun searchByTitle(
        title: String,
        type: String? = null,
        perpage: Int? = null
    ): Result<PaginatedResponse<Item>> = apiCall {
        httpClient.get("${KinoPubConfig.MAIN_API_BASE_URL}items") {
            parameter("title", title)
            type?.let { parameter("type", it) }
            perpage?.let { parameter("perpage", it) }
        }
    }

    /**
     * Search by director
     */
    suspend fun searchByDirector(
        director: String,
        sort: String? = null,
        perpage: Int? = null
    ): Result<PaginatedResponse<Item>> = apiCall {
        httpClient.get("${KinoPubConfig.MAIN_API_BASE_URL}items") {
            parameter("director", director)
            sort?.let { parameter("sort", it) }
            perpage?.let { parameter("perpage", it) }
        }
    }

    /**
     * Search by actor
     */
    suspend fun searchByActor(
        actor: String,
        sort: String? = null,
        perpage: Int? = null
    ): Result<PaginatedResponse<Item>> = apiCall {
        httpClient.get("${KinoPubConfig.MAIN_API_BASE_URL}items") {
            parameter("actor", actor)
            sort?.let { parameter("sort", it) }
            perpage?.let { parameter("perpage", it) }
        }
    }

    /**
     * Get similar items
     */
    suspend fun getSimilarItems(id: Int): Result<PaginatedResponse<Item>> = apiCall {
        httpClient.get("${KinoPubConfig.MAIN_API_BASE_URL}items/similar") {
            parameter("id", id)
        }
    }

    /**
     * Get item comments
     */
    suspend fun getItemComments(id: Int): Result<List<Comment>> = apiCall {
        httpClient.get("${KinoPubConfig.MAIN_API_BASE_URL}items/comments") {
            parameter("id", id)
        }
    }

    /**
     * Get trailer URL
     */
    suspend fun getTrailerUrl(sid: String): Result<String> = apiCall {
        httpClient.get("${KinoPubConfig.MAIN_API_BASE_URL}items/trailer") {
            parameter("sid", sid)
        }
    }

    // History & Watching API

    /**
     * Get watching history
     */
    suspend fun getHistory(
        type: String,
        subscribed: Int? = null
    ): Result<List<History>> = apiCall {
        httpClient.get("${KinoPubConfig.MAIN_API_BASE_URL}watching/$type") {
            subscribed?.let { parameter("subscribed", it) }
        }
    }

    /**
     * Get history data with pagination
     */
    suspend fun getHistoryData(page: Int): Result<PaginatedResponse<History>> = apiCall {
        httpClient.get("${KinoPubConfig.MAIN_API_BASE_URL}history") {
            parameter("page", page)
        }
    }

    /**
     * Clear item history
     */
    suspend fun clearItemHistory(id: Int): Result<Unit> = apiCall {
        httpClient.post("${KinoPubConfig.MAIN_API_BASE_URL}history/clear-for-item") {
            parameter("id", id)
        }
    }

    /**
     * Clear media history
     */
    suspend fun clearMediaHistory(id: Int): Result<Unit> = apiCall {
        httpClient.post("${KinoPubConfig.MAIN_API_BASE_URL}history/clear-for-media") {
            parameter("id", id)
        }
    }

    /**
     * Clear season history
     */
    suspend fun clearSeasonHistory(id: Int): Result<Unit> = apiCall {
        httpClient.post("${KinoPubConfig.MAIN_API_BASE_URL}history/clear-for-season") {
            parameter("id", id)
        }
    }

    /**
     * Toggle watching status
     */
    suspend fun toggleWatchingStatus(
        id: Int,
        status: Int? = null,
        season: Int? = null,
        video: Int? = null
    ): Result<WatchingStatus> = apiCall {
        httpClient.get("${KinoPubConfig.MAIN_API_BASE_URL}watching/toggle") {
            parameter("id", id)
            status?.let { parameter("status", it) }
            season?.let { parameter("season", it) }
            video?.let { parameter("video", it) }
        }
    }

    /**
     * Set watching time
     */
    suspend fun setWatchingTime(
        id: Int,
        videoId: Int,
        time: Int,
        season: Int? = null
    ): Result<WatchingStatus> = apiCall {
        httpClient.get("${KinoPubConfig.MAIN_API_BASE_URL}watching/marktime") {
            parameter("id", id)
            parameter("video", videoId)
            parameter("time", time)
            season?.let { parameter("season", it) }
        }
    }

    /**
     * Toggle watchlist
     */
    suspend fun toggleWatchlist(id: Int): Result<WatchingStatus> = apiCall {
        httpClient.get("${KinoPubConfig.MAIN_API_BASE_URL}watching/togglewatchlist") {
            parameter("id", id)
        }
    }

    // Bookmarks API

    /**
     * Get bookmarks
     */
    suspend fun getBookmarks(): Result<List<Bookmark>> = apiCall {
        httpClient.get("${KinoPubConfig.MAIN_API_BASE_URL}bookmarks")
    }

    /**
     * Get bookmark items
     */
    suspend fun getBookmarkItems(
        id: Int,
        page: Int? = null
    ): Result<PaginatedResponse<Item>> = apiCall {
        httpClient.get("${KinoPubConfig.MAIN_API_BASE_URL}bookmarks/$id") {
            page?.let { parameter("page", it) }
        }
    }

    /**
     * Create bookmark
     */
    suspend fun createBookmark(title: String): Result<Bookmark> = apiCall {
        httpClient.post("${KinoPubConfig.MAIN_API_BASE_URL}bookmarks/create") {
            setBody(mapOf("title" to title))
            contentType(ContentType.Application.Json)
        }
    }

    /**
     * Add item to bookmark
     */
    suspend fun addBookmarkItem(itemId: Int, folderId: Int): Result<Unit> = apiCall {
        httpClient.post("${KinoPubConfig.MAIN_API_BASE_URL}bookmarks/add") {
            setBody(
                mapOf(
                    "item" to itemId,
                    "folder" to folderId
                )
            )
            contentType(ContentType.Application.Json)
        }
    }

    /**
     * Remove item from bookmark
     */
    suspend fun removeBookmarkItem(itemId: Int, folderId: Int): Result<Unit> = apiCall {
        httpClient.post("${KinoPubConfig.MAIN_API_BASE_URL}bookmarks/remove-item") {
            setBody(
                mapOf(
                    "item" to itemId,
                    "folder" to folderId
                )
            )
            contentType(ContentType.Application.Json)
        }
    }

    /**
     * Delete bookmark
     */
    suspend fun deleteBookmark(folderId: Int): Result<Unit> = apiCall {
        httpClient.post("${KinoPubConfig.MAIN_API_BASE_URL}bookmarks/remove-folder") {
            setBody(mapOf("folder" to folderId))
            contentType(ContentType.Application.Json)
        }
    }

    // Collections API

    /**
     * Get collections
     */
    suspend fun getCollections(
        sort: String? = null,
        page: Int? = null
    ): Result<PaginatedResponse<KCollection>> = apiCall {
        httpClient.get("${KinoPubConfig.MAIN_API_BASE_URL}collections") {
            sort?.let { parameter("sort", it) }
            page?.let { parameter("page", it) }
        }
    }

    /**
     * Get collection items
     */
    suspend fun getCollectionItems(id: Int): Result<PaginatedResponse<Item>> = apiCall {
        httpClient.get("${KinoPubConfig.MAIN_API_BASE_URL}collections/view") {
            parameter("id", id)
        }
    }

    // User & Device API

    /**
     * Get account info
     */
    suspend fun getAccountInfo(): Result<UserInfo> = apiCall {
        httpClient.get("${KinoPubConfig.MAIN_API_BASE_URL}user")
    }

    /**
     * Get device settings
     */
    suspend fun getDeviceSettings(): Result<DeviceSettings> = apiCall {
        httpClient.get("${KinoPubConfig.MAIN_API_BASE_URL}device/info")
    }

    /**
     * Get devices info
     */
    suspend fun getDevicesInfo(): Result<List<DeviceSettings>> = apiCall {
        httpClient.get("${KinoPubConfig.MAIN_API_BASE_URL}device/device")
    }

    /**
     * Update device info
     */
    suspend fun updateDeviceInfo(
        title: String,
        hardware: String,
        software: String
    ): Result<Unit> = apiCall {
        httpClient.post("${KinoPubConfig.MAIN_API_BASE_URL}device/notify") {
            setBody(
                mapOf(
                    "title" to title,
                    "hardware" to hardware,
                    "software" to software
                )
            )
            contentType(ContentType.Application.Json)
        }
    }

    /**
     * Unlink device
     */
    suspend fun unlinkDevice(): Result<Unit> = apiCall {
        httpClient.post("${KinoPubConfig.MAIN_API_BASE_URL}device/unlink")
    }

    // Metadata API

    /**
     * Get genres
     */
    suspend fun getGenres(): Result<List<Genre>> = apiCall {
        httpClient.get("${KinoPubConfig.MAIN_API_BASE_URL}genres")
    }

    /**
     * Get countries
     */
    suspend fun getCountries(): Result<List<Country>> = apiCall {
        httpClient.get("${KinoPubConfig.MAIN_API_BASE_URL}countries")
    }

    // Helper method for API calls
    private suspend inline fun <reified T> apiCall(
        block: suspend () -> HttpResponse
    ): Result<T> = try {
        val response = block()
        Result.success(response.body<T>())
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun close() {
        httpClient.close()
    }
} 