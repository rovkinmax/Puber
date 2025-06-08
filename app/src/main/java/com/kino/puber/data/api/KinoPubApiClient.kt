package com.kino.puber.data.api

import com.kino.puber.BuildConfig
import com.kino.puber.data.api.auth.DeviceCodeResponse
import com.kino.puber.data.api.auth.DeviceFlowResult
import com.kino.puber.data.api.auth.OAuthError
import com.kino.puber.data.api.auth.TokenResponse
import com.kino.puber.data.api.config.KinoPubConfig
import com.kino.puber.data.api.config.UserAgentBuilder
import com.kino.puber.data.api.models.Bookmark
import com.kino.puber.data.api.models.BookmarkFolder
import com.kino.puber.data.api.models.BookmarkToggleResult
import com.kino.puber.data.api.models.Comment
import com.kino.puber.data.api.models.Country
import com.kino.puber.data.api.models.DeviceInfo
import com.kino.puber.data.api.models.DeviceSettings
import com.kino.puber.data.api.models.Episode
import com.kino.puber.data.api.models.Genre
import com.kino.puber.data.api.models.History
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemFiles
import com.kino.puber.data.api.models.KCollection
import com.kino.puber.data.api.models.MediaLinks
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.api.models.QualityType
import com.kino.puber.data.api.models.Season
import com.kino.puber.data.api.models.ServerLocation
import com.kino.puber.data.api.models.StreamingType
import com.kino.puber.data.api.models.TVChannel
import com.kino.puber.data.api.models.TranslationType
import com.kino.puber.data.api.models.UserInfo
import com.kino.puber.data.api.models.VoiceAuthor
import com.kino.puber.data.api.models.VoteResult
import com.kino.puber.data.api.models.WatchingStatus
import com.kino.puber.data.repository.ICryptoPreferenceRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient


class KinoPubApiClient(
    private val cryptoPreferenceRepository: ICryptoPreferenceRepository,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }
    private val okHttpClient = createOkHttpClient()
    private val httpClient: HttpClient = createHttpClient(okHttpClient)

    private fun createOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder().build()
    }

    private fun createHttpClient(
        okHttpClient: OkHttpClient,
    ): HttpClient = HttpClient(OkHttp) {

        install(KinoPubParametersPlugin)

        // Request timeout configuration (Ktor level)
        install(HttpTimeout) {
            connectTimeoutMillis = TIMEOUT
            requestTimeoutMillis = TIMEOUT
            socketTimeoutMillis = TIMEOUT
        }

        // JSON serialization
        install(ContentNegotiation) {
            json(json)
        }

        // Logging
        if (BuildConfig.DEBUG) {
            install(Logging) {
                level = LogLevel.INFO
                logger = Logger.DEFAULT
            }
        }

        // Default request configuration
        install(DefaultRequest) {
            headers {
                val username = cryptoPreferenceRepository.getUsername()
                append("User-Agent", UserAgentBuilder.build(username))
                append("Accept", "application/json")
                append("Content-Type", "application/json")
            }
        }

        // Authentication if token provided
        install(Auth) {
            bearer {
                loadTokens {
                    val accessToken = cryptoPreferenceRepository.getAccessToken().orEmpty()
                    val refreshToken = cryptoPreferenceRepository.getRefreshToken().orEmpty()
                    BearerTokens(accessToken, refreshToken)
                }

                refreshTokens {
//                    val accessToken = cryptoPreferenceRepository.getAccessToken().orEmpty()
                    val refreshToken = cryptoPreferenceRepository.getRefreshToken().orEmpty()
                    val tokenUpdateResponse: Result<TokenResponse> = apiCall {
                        client.post("${KinoPubConfig.OAUTH_BASE_URL}token") {
                            parameter("grant_type", KinoPubConfig.GRANT_TYPE_REFRESH_TOKEN)
                            parameter("refresh_token", refreshToken)
                            // CLIENT_ID и CLIENT_SECRET добавляются автоматически через KinoPubParametersInterceptor
                        }
                    }
                    if (tokenUpdateResponse.isSuccess) {
                        BearerTokens(
                            tokenUpdateResponse.getOrNull()?.accessToken.orEmpty(),
                            tokenUpdateResponse.getOrNull()?.refreshToken.orEmpty()
                        )
                    } else throw IllegalStateException("No tokens data")
                }
            }
        }

        // Retry on failure
        install(HttpRequestRetry) {
            maxRetries = MAX_RETRIES
            retryIf { _, response ->
                response.status.value >= 500
            }
            exponentialDelay()
        }
    }

    fun isAuthenticated(): Boolean = cryptoPreferenceRepository.getAccessToken().isNullOrEmpty().not()

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

    // Reference data API (based on official documentation)

    /**
     * Get server locations
     */
    suspend fun getServerLocations(): Result<List<ServerLocation>> = apiCall {
        httpClient.get("${KinoPubConfig.MAIN_API_BASE_URL}server-locations")
    }

    /**
     * Get streaming types
     */
    suspend fun getStreamingTypes(): Result<List<StreamingType>> = apiCall {
        httpClient.get("${KinoPubConfig.MAIN_API_BASE_URL}streaming-types")
    }

    /**
     * Get translation types
     */
    suspend fun getTranslationTypes(): Result<List<TranslationType>> = apiCall {
        httpClient.get("${KinoPubConfig.MAIN_API_BASE_URL}translation-types")
    }

    /**
     * Get voice authors
     */
    suspend fun getVoiceAuthors(): Result<List<VoiceAuthor>> = apiCall {
        httpClient.get("${KinoPubConfig.MAIN_API_BASE_URL}voice-authors")
    }

    /**
     * Get quality types
     */
    suspend fun getQualityTypes(): Result<List<QualityType>> = apiCall {
        httpClient.get("${KinoPubConfig.MAIN_API_BASE_URL}quality-types")
    }

    // TV Broadcasting API

    /**
     * Get TV channels
     */
    suspend fun getTVChannels(): Result<List<TVChannel>> = apiCall {
        httpClient.get("${KinoPubConfig.MAIN_API_BASE_URL}tv/channels")
    }

    /**
     * Get TV channel details
     */
    suspend fun getTVChannelDetails(id: Int): Result<TVChannel> = apiCall {
        httpClient.get("${KinoPubConfig.MAIN_API_BASE_URL}tv/channels/$id")
    }

    // Media files and links API

    /**
     * Get media links (subtitles and video files) for media
     */
    suspend fun getMediaLinks(
        id: Int,
        season: Int? = null,
        episode: Int? = null
    ): Result<MediaLinks> = apiCall {
        httpClient.get("${KinoPubConfig.MAIN_API_BASE_URL}items/media-links") {
            parameter("id", id)
            season?.let { parameter("season", it) }
            episode?.let { parameter("episode", it) }
        }
    }

    /**
     * Get video file link by filename
     */
    suspend fun getVideoFileLink(filename: String): Result<String> = apiCall {
        httpClient.get("${KinoPubConfig.MAIN_API_BASE_URL}items/media-link") {
            parameter("filename", filename)
        }
    }

    /**
     * Get item files (video files with different qualities and translations)
     */
    suspend fun getItemFiles(
        id: Int,
        season: Int? = null,
        episode: Int? = null
    ): Result<ItemFiles> = apiCall {
        httpClient.get("${KinoPubConfig.MAIN_API_BASE_URL}items/files") {
            parameter("id", id)
            season?.let { parameter("season", it) }
            episode?.let { parameter("episode", it) }
        }
    }

    // Voting API

    /**
     * Vote for video content
     */
    suspend fun voteForItem(
        id: Int,
        rating: Int // 1-10
    ): Result<VoteResult> = apiCall {
        httpClient.post("${KinoPubConfig.MAIN_API_BASE_URL}items/vote") {
            setBody(
                mapOf(
                    "id" to id,
                    "rating" to rating
                )
            )
            contentType(ContentType.Application.Json)
        }
    }

    /**
     * Remove vote for item
     */
    suspend fun removeVoteForItem(id: Int): Result<VoteResult> = apiCall {
        httpClient.post("${KinoPubConfig.MAIN_API_BASE_URL}items/vote/remove") {
            setBody(mapOf("id" to id))
            contentType(ContentType.Application.Json)
        }
    }

    // Extended device API

    /**
     * Get all devices on account
     */
    suspend fun getAllDevices(): Result<List<DeviceInfo>> = apiCall {
        httpClient.get("${KinoPubConfig.MAIN_API_BASE_URL}device/devices")
    }

    /**
     * Remove specific device by ID
     */
    suspend fun removeDevice(deviceId: String): Result<Unit> = apiCall {
        httpClient.post("${KinoPubConfig.MAIN_API_BASE_URL}device/remove") {
            setBody(mapOf("device_id" to deviceId))
            contentType(ContentType.Application.Json)
        }
    }

    /**
     * Get device settings by ID
     */
    suspend fun getDeviceSettingsById(deviceId: String): Result<DeviceSettings> = apiCall {
        httpClient.get("${KinoPubConfig.MAIN_API_BASE_URL}device/$deviceId")
    }

    /**
     * Update device settings
     */
    suspend fun updateDeviceSettings(
        supportSsl: Boolean? = null,
        supportHevc: Boolean? = null,
        supportHdr: Boolean? = null,
        support4k: Boolean? = null,
        mixedPlaylist: Boolean? = null,
        streamingType: Int? = null,
        serverLocation: Int? = null
    ): Result<DeviceSettings> = apiCall {
        httpClient.post("${KinoPubConfig.MAIN_API_BASE_URL}device/settings") {
            val settings = mutableMapOf<String, Any>()
            supportSsl?.let { settings["support_ssl"] = it }
            supportHevc?.let { settings["support_hevc"] = it }
            supportHdr?.let { settings["support_hdr"] = it }
            support4k?.let { settings["support_4k"] = it }
            mixedPlaylist?.let { settings["mixed_playlist"] = it }
            streamingType?.let { settings["streaming_type"] = it }
            serverLocation?.let { settings["server_location"] = it }

            setBody(settings)
            contentType(ContentType.Application.Json)
        }
    }

    // Extended bookmarks API

    /**
     * Get bookmark folders for specific item
     */
    suspend fun getItemBookmarkFolders(itemId: Int): Result<List<BookmarkFolder>> = apiCall {
        httpClient.get("${KinoPubConfig.MAIN_API_BASE_URL}bookmarks/get-item-folders") {
            parameter("item", itemId)
        }
    }

    /**
     * Toggle bookmark (add/remove from folder)
     */
    suspend fun toggleBookmark(itemId: Int, folderId: Int): Result<BookmarkToggleResult> = apiCall {
        httpClient.post("${KinoPubConfig.MAIN_API_BASE_URL}bookmarks/toggle") {
            setBody(
                mapOf(
                    "item" to itemId,
                    "folder" to folderId
                )
            )
            contentType(ContentType.Application.Json)
        }
    }

    // Extended content API

    /**
     * Get item seasons (for TV shows)
     */
    suspend fun getItemSeasons(id: Int): Result<List<Season>> = apiCall {
        httpClient.get("${KinoPubConfig.MAIN_API_BASE_URL}items/$id/seasons")
    }

    /**
     * Get season episodes
     */
    suspend fun getSeasonEpisodes(
        itemId: Int,
        seasonNumber: Int
    ): Result<List<Episode>> = apiCall {
        httpClient.get("${KinoPubConfig.MAIN_API_BASE_URL}items/$itemId/seasons/$seasonNumber/episodes")
    }

    /**
     * Get episode details
     */
    suspend fun getEpisodeDetails(
        itemId: Int,
        seasonNumber: Int,
        episodeNumber: Int
    ): Result<Episode> = apiCall {
        httpClient.get("${KinoPubConfig.MAIN_API_BASE_URL}items/$itemId/seasons/$seasonNumber/episodes/$episodeNumber")
    }

    // Helper method for API calls

    suspend inline fun <reified T> apiCall(
        block: suspend () -> HttpResponse
    ): Result<T> = try {
        val response = block()
        Result.success(response.body<T>())
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun getDeviceCode(): Result<DeviceCodeResponse> = try {
        val response = httpClient.post("${KinoPubConfig.OAUTH_BASE_URL}device") {
            parameter("grant_type", KinoPubConfig.GRANT_TYPE_DEVICE_CODE)
            // CLIENT_ID и CLIENT_SECRET добавляются автоматически через KinoPubParametersInterceptor
        }

        handleApiResponse<DeviceCodeResponse>(response)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Exchange device code for access token
     * POST /oauth2/device?grant_type=device_token&client_id=myclient&client_secret=mysecret&code=abcdefg
     */
    suspend fun getDeviceToken(
        code: String
    ): Result<TokenResponse> = try {
        val response = httpClient.post("${KinoPubConfig.OAUTH_BASE_URL}device") {
            parameter("grant_type", KinoPubConfig.GRANT_TYPE_DEVICE_TOKEN)
            parameter("code", code)
            // CLIENT_ID и CLIENT_SECRET добавляются автоматически через KinoPubParametersInterceptor
        }

        handleApiResponse<TokenResponse>(response)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Refresh access token using refresh token
     * POST /oauth2/token?grant_type=refresh_token&client_id=myclient&client_secret=mysecret&refresh_token=qwertyu12345678
     */
    /*suspend fun refreshToken(refreshToken: String): Result<TokenResponse> = try {
        val response = httpClient.post("${KinoPubConfig.OAUTH_BASE_URL}token") {
            parameter("grant_type", KinoPubConfig.GRANT_TYPE_REFRESH_TOKEN)
            parameter("refresh_token", refreshToken)
            // CLIENT_ID и CLIENT_SECRET добавляются автоматически через KinoPubParametersInterceptor
        }

        handleApiResponse<TokenResponse>(response)
    } catch (e: Exception) {
        Result.failure(e)
    }*/

    /**
     * Complete OAuth device flow with polling
     * Implements the complete flow as described in the official documentation
     * Returns Flow to emit intermediate states during the authentication process
     */
    fun completeDeviceFlow(
        pollingInterval: Long = 5000L,
        maxAttempts: Int = 60
    ): Flow<Result<DeviceFlowResult>> = flow {
        // Step 1: Get device code
        val deviceCodeResult = getDeviceCode()
        if (deviceCodeResult.isFailure) {
            emit(Result.failure(deviceCodeResult.exceptionOrNull()!!))
            return@flow
        }

        val deviceCode = deviceCodeResult.getOrThrow()

        emit(
            Result.success(
                DeviceFlowResult(
                    deviceCode = deviceCode,
                    token = null,
                )
            )
        )

        // Step 2: Poll for token
        // Use interval from API response (converted to milliseconds) or default
        val interval = (deviceCode.interval * 1000L).coerceAtLeast(pollingInterval)
        var attempts = 0

        while (attempts < maxAttempts) {
            delay(interval)
            attempts++

            val tokenResult = getDeviceToken(
                code = deviceCode.code // Use 'code' field as per official documentation
            )

            if (tokenResult.isSuccess) {
                // Emit successful result and complete the flow
                emit(
                    Result.success(
                        DeviceFlowResult(
                            deviceCode = deviceCode,
                            token = tokenResult.getOrThrow()
                        )
                    )
                )
                return@flow
            }

            // Check if error is polling-related (authorization_pending)
            val error = tokenResult.exceptionOrNull()
            val isAuthorizationPending =
                error?.message?.contains("authorization_pending", true) ?: false

            if (!isAuthorizationPending) {
                // Emit non-recoverable error and complete the flow
                emit(Result.failure(error!!))
                return@flow
            }

            // Continue polling for authorization_pending errors
        }

        // Emit timeout error if max attempts reached
        emit(Result.failure(RuntimeException("Device flow timeout after $maxAttempts attempts")))
    }

    fun close() {
        httpClient.close()
    }

    /**
     * Safe handling of API responses with error detection
     */
    private suspend inline fun <reified T> handleApiResponse(response: HttpResponse): Result<T> {
        return try {
            if (!response.status.isSuccess()) {
                val errorText = response.bodyAsText()
                return Result.failure(
                    Exception("HTTP ${response.status.value}: $errorText")
                )
            }

            val responseText = response.bodyAsText()

            // Проверяем на наличие ошибки в JSON
            if (responseText.contains("\"error\"")) {
                try {
                    val error = json.decodeFromString<OAuthError>(responseText)
                    return Result.failure(
                        Exception("OAuth Error: ${error.error}${error.errorDescription?.let { " - $it" } ?: ""}")
                    )
                } catch (e: Exception) {
                    // Если не удалось распарсить как OAuthError, возвращаем сырой текст
                    return Result.failure(Exception("API Error: $responseText"))
                }
            }

            // Пытаемся десериализовать как ожидаемый тип
            val result = json.decodeFromString<T>(responseText)
            Result.success(result)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private const val MAX_RETRIES = 3
        private const val TIMEOUT = 60000L
    }
}
