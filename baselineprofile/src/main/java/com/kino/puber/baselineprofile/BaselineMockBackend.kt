package com.kino.puber.baselineprofile

import android.content.Context
import java.io.Closeable
import java.util.concurrent.atomic.AtomicLong
import mockwebserver3.RecordedRequest
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject
import com.kino.puber.playertestfixtures.server.HermeticRequestJournal
import com.kino.puber.playertestfixtures.server.HermeticTestServer
import com.kino.puber.playertestfixtures.server.QueryMatchMode as HermeticQueryMatchMode
import com.kino.puber.playertestfixtures.server.ResponsePlan

/**
 * A route-based backend for profile and macrobenchmark journeys.
 *
 * The server is deliberately owned by the benchmark process. The target
 * application can therefore be force-stopped or reinstalled without taking
 * down the mock or losing its request journal.
 */
class BaselineMockBackend(
    private val port: Int,
    private val fixtures: BaselineFixtures = BaselineFixtures.synthetic(),
) : Closeable {

    private val server = HermeticTestServer(port)
    private val generation = AtomicLong(0)
    private val lock = Any()
    private var activeScenario = BaselineScenario.Startup
    private var activeRoutes: List<BaselineMockRoute> = emptyList()
    private var started = false

    val baseUrl: String
        get() = server.baseUrl

    val generationId: Long
        get() = generation.get()

    val requiredRoutes: List<BaselineMockRoute>
        get() = synchronized(lock) {
            activeRoutes.filter { it.required }
        }

    val requestJournal: BaselineRequestJournal
        get() = synchronized(lock) {
            val snapshot = server.requestJournal
            BaselineRequestJournal(
                generationId = generationId,
                matched = activeRoutes.associateWith { route ->
                    snapshot.matchedRoutes[route.description] ?: 0
                },
                unknown = snapshot.unknownRequests.map(BaselineRequest::from),
            )
        }

    fun start() {
        synchronized(lock) {
            check(!started) { "BaselineMockBackend is already started" }
            fixtures.validate()
            server.start()
            started = true
            reset(activeScenario)
        }
    }

    fun awaitReady(timeoutMs: Int = 2_000) {
        val connection = java.net.URL("${baseUrl}__baseline/ready")
            .openConnection() as java.net.HttpURLConnection
        try {
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            check(connection.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                "Baseline mock readiness failed with HTTP ${connection.responseCode}"
            }
        } finally {
            connection.disconnect()
        }
    }

    fun awaitStartupHomeRequest(timeoutMs: Int = 10_000) {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        while (System.nanoTime() < deadline) {
            val observed = synchronized(lock) {
                val route = activeRoutes.firstOrNull(::isStartupHomeRoute)
                route != null &&
                    (server.requestJournal.matchedRoutes[route.description] ?: 0) > 0
            }
            if (observed) {
                return
            }
            Thread.sleep(LIVENESS_POLL_MS)
        }
        error("Target-originated Home request was not observed within ${timeoutMs}ms")
    }

    private fun isStartupHomeRoute(route: BaselineMockRoute): Boolean =
        route.path == STARTUP_HOME_PATH &&
            route.query == mapOf(STARTUP_HOME_TYPE_QUERY to STARTUP_HOME_TYPE)

    fun reset(scenario: BaselineScenario) {
        synchronized(lock) {
            check(started) { "Call start() before reset()" }
            activeScenario = scenario
            activeRoutes = routesFor(scenario)
            server.reset(
                activeRoutes.map { route ->
                    server.route(
                        id = route.description,
                        method = route.method,
                        path = route.path,
                        query = route.query,
                        queryMode = route.queryMode.toHermetic(),
                        response = ResponsePlan.Text(
                            status = 200,
                            body = route.body,
                            contentType = "application/json; charset=utf-8",
                        ),
                        required = route.required,
                        minimumRequests = route.minimumRequests,
                    )
                },
            )
            generation.incrementAndGet()
        }
    }

    fun verify(): BaselineVerification {
        synchronized(lock) {
            val snapshot = server.requestJournal
            val matched = activeRoutes.associateWith { route ->
                snapshot.matchedRoutes[route.description] ?: 0
            }
            val missing = activeRoutes
                .filter { it.required && (matched[it] ?: 0) < it.minimumRequests }
                .map { it.description }
                .toMutableList()
            if (activeScenario == BaselineScenario.BrowseAndDetails) {
                val detailIds = matched
                    .filter { (route, count) ->
                        count > 0 && route.path.startsWith("/v1/items/") &&
                            route.path.removePrefix("/v1/items/").toIntOrNull() != null
                    }
                    .map { it.key.path.removePrefix("/v1/items/").toInt() }
                    .toSet()
                val similarIds = matched
                    .filter { (route, count) ->
                        count > 0 && route.path == "/v1/items/similar"
                    }
                    .mapNotNull { it.key.query["id"]?.toIntOrNull() }
                    .toSet()
                if (detailIds.intersect(similarIds).isEmpty()) {
                    missing += "GET /v1/items/{id} + GET /v1/items/similar?id={id}"
                }
            }
            return BaselineVerification(
                generationId = generationId,
                scenario = activeScenario,
                matchedRequests = matched.values.sum(),
                matchedRoutes = matched
                    .filterValues { it > 0 }
                    .entries
                    .associate { (route, count) -> route.description to count },
                unknownRequests = snapshot.unknownRequests.map(BaselineRequest::from),
                missingRequiredRoutes = missing,
            )
        }
    }

    fun url(route: BaselineMockRoute): String {
        check(started) { "Call start() before url()" }
        return server.url(route.path, route.query)
    }

    override fun close() {
        synchronized(lock) {
            if (!started) return
            started = false
            server.close()
        }
    }

    private fun routesFor(scenario: BaselineScenario): List<BaselineMockRoute> {
        val routes = mutableListOf<BaselineMockRoute>()
        fun route(
            method: String = "GET",
            path: String,
            query: Map<String, String> = emptyMap(),
            body: String,
            minimumRequests: Int = 1,
            required: Boolean = false,
            queryMode: QueryMatchMode = QueryMatchMode.Exact,
        ) {
            routes += BaselineMockRoute(
                method = method,
                path = path,
                query = query,
                body = body,
                minimumRequests = minimumRequests,
                required = required,
                queryMode = queryMode,
            )
        }

        val homeJourney = scenario != BaselineScenario.Startup
        val contentJourney = scenario == BaselineScenario.TabNavigation
        val detailsJourney = scenario == BaselineScenario.BrowseAndDetails

        route(
            path = "/__baseline/ready",
            body = """{"status":"ready"}""",
            required = true,
        )
        route(
            path = "/v1/watching/serials",
            query = mapOf("subscribed" to "1"),
            body = fixtures.emptyList,
            required = homeJourney,
        )
        route(path = "/v1/bookmarks", body = fixtures.bookmarks, required = homeJourney)
        route(
            path = "/v1/bookmarks/1",
            body = fixtures.bookmarkItems,
            required = homeJourney,
        )
        listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 101, 102).forEach { itemId ->
            route(
                path = "/v1/bookmarks/get-item-folders",
                query = mapOf("item" to itemId.toString()),
                body = fixtures.emptyBookmarkFolders,
                required = false,
            )
        }
        route(
            path = "/v1/collections",
            query = mapOf("page" to "1"),
            body = fixtures.collections,
            required = homeJourney,
        )
        route(
            path = "/v1/genres",
            query = mapOf("type" to "movie"),
            body = fixtures.genres,
            required = contentJourney,
        )
        route(
            path = "/v1/genres",
            query = mapOf("type" to "serial"),
            body = fixtures.genres,
            required = contentJourney,
        )

        listOf("movie", "serial").forEach { type ->
            listOf("hot", "fresh", "popular").forEach { shortcut ->
                listOf(emptyMap(), mapOf("page" to "1")).forEach { page ->
                    route(
                        path = "/v1/items/$shortcut",
                        query = mapOf("type" to type) + page,
                        body = fixtures.items,
                        required = (homeJourney && page.isEmpty()) ||
                            (contentJourney && page["page"] == "1") ||
                            (
                                scenario == BaselineScenario.Startup &&
                                    type == STARTUP_HOME_TYPE &&
                                    shortcut == STARTUP_HOME_SHORTCUT &&
                                    page.isEmpty()
                                ),
                    )
                }
            }
            listOf(
                mapOf("type" to type, "quality" to "4k", "sort" to "updated-"),
                mapOf("type" to type, "sort" to "updated-"),
            ).forEach { query ->
                listOf(emptyMap(), mapOf("page" to "1")).forEach { page ->
                    route(
                        path = "/v1/items",
                        query = query + page,
                        body = fixtures.items,
                        required = contentJourney && page["page"] == "1",
                    )
                }
            }
        }

        listOf("movie", "serial").forEach { type ->
            listOf(emptyMap(), mapOf("page" to "1")).forEach { page ->
                route(
                    path = "/v1/items",
                    query = mapOf("type" to type, "sort" to "views-") + page,
                    body = fixtures.items,
                    required = false,
                )
            }
        }
        (1..12).forEach { itemId ->
            route(
                path = "/v1/items/$itemId",
                body = fixtures.detailsFor(itemId),
                required = false,
            )
            route(
                path = "/v1/items/similar",
                query = mapOf("id" to itemId.toString()),
                body = fixtures.similar,
                required = false,
            )
        }
        route(path = "/v1/items/101", body = fixtures.details)
        route(path = "/v1/items/102", body = fixtures.details)
        route(
            path = "/api2/v1.1/items/collections/101",
            body = fixtures.emptyList,
            required = false,
        )
        route(
            method = "POST",
            path = "/oauth2/device",
            query = mapOf("grant_type" to "refresh_token"),
            body = fixtures.token,
            required = false,
            queryMode = QueryMatchMode.Contains,
        )
        return routes
    }

    private companion object {
        const val STARTUP_HOME_PATH = "/v1/items/fresh"
        const val STARTUP_HOME_SHORTCUT = "fresh"
        const val STARTUP_HOME_TYPE_QUERY = "type"
        const val STARTUP_HOME_TYPE = "movie"
        const val LIVENESS_POLL_MS = 50L
    }
}

enum class BaselineScenario {
    Startup,
    BrowseAndDetails,
    TabNavigation,
}

enum class QueryMatchMode {
    Exact,
    Contains,
}

data class BaselineMockRoute(
    val method: String,
    val path: String,
    val query: Map<String, String>,
    val body: String,
    val minimumRequests: Int = 1,
    val required: Boolean = true,
    val queryMode: QueryMatchMode = QueryMatchMode.Exact,
) {
    val description: String
        get() = buildString {
            append(method)
            append(' ')
            append(path)
            if (query.isNotEmpty()) {
                append('?')
                append(query.entries.joinToString("&") { "${it.key}=${it.value}" })
            }
        }

    fun matches(url: HttpUrl): Boolean {
        if (url.encodedPath.trimEnd('/').ifEmpty { "/" } != path.trimEnd('/').ifEmpty { "/" }) {
            return false
        }
        val actualQuery = url.queryParameterNames.associateWith { name ->
            url.queryParameter(name).orEmpty()
        }
        return when (queryMode) {
            QueryMatchMode.Exact -> actualQuery == query
            QueryMatchMode.Contains -> query.all { (name, value) -> actualQuery[name] == value }
        }
    }

    fun matches(request: RecordedRequest): Boolean {
        return method.equals(request.method, ignoreCase = true) && matches(request.url)
    }
}

data class BaselineRequest(
    val method: String,
    val path: String,
) {
    companion object {
        fun from(request: RecordedRequest): BaselineRequest =
            BaselineRequest(request.method, request.url.encodedPath)

        fun from(request: HermeticRequestJournal.RequestEntry): BaselineRequest =
            BaselineRequest(request.method, request.path)
    }
}

data class BaselineRequestJournal(
    val generationId: Long,
    val matched: Map<BaselineMockRoute, Int>,
    val unknown: List<BaselineRequest>,
)

data class BaselineVerification(
    val generationId: Long,
    val scenario: BaselineScenario,
    val matchedRequests: Int,
    val matchedRoutes: Map<String, Int>,
    val unknownRequests: List<BaselineRequest>,
    val missingRequiredRoutes: List<String>,
) {
    val isSuccessful: Boolean
        get() = unknownRequests.isEmpty() && missingRequiredRoutes.isEmpty()
}

data class BaselineFixtures(
    val items: String,
    val details: String,
    val similar: String,
    val collections: String,
    val bookmarks: String,
    val bookmarkItems: String,
    val emptyBookmarkFolders: String,
    val genres: String,
    val emptyList: String,
    val token: String,
) {
    fun validate() {
        val itemsObject = JSONObject(items)
        val itemsArray = itemsObject.getJSONArray("items")
        check(itemsArray.length() >= MIN_ITEMS) {
            "Baseline fixture must contain at least $MIN_ITEMS focusable items"
        }
        check(itemsObject.getJSONObject("pagination").getInt("total_items") >= MIN_ITEMS)
        check(JSONObject(collections).getJSONArray("items").length() >= MIN_COLLECTIONS) {
            "Baseline fixture must contain at least $MIN_COLLECTIONS collections"
        }
        check(JSONObject(details).getJSONObject("item").getInt("id") > 0)
        check(JSONObject(similar).getJSONArray("items").length() > 0)
        check(JSONObject(collections).getJSONArray("items").length() > 0)
        check(JSONObject(bookmarks).getJSONArray("items").length() > 0)
        check(JSONObject(bookmarkItems).getJSONArray("items").length() > 0)
        check(JSONObject(emptyBookmarkFolders).getJSONArray("folders").length() == 0)
        check(JSONArray(genres).length() > 0)
        val isolationContract = decodeIsolationContract()
        check(isolationContract.imdbIds == null) {
            "Baseline fixtures must not expose IMDb identity to TMDB or IntroDB branches"
        }
        check(isolationContract.imageUrls == null) {
            "Baseline fixtures must not expose image URLs to Coil"
        }
        check(isolationContract.playbackIds == null) {
            "Baseline fixtures must not expose video or episode playback identity"
        }
        check(isolationContract.mediaUrls == null) {
            "Baseline fixtures must not expose media URLs to Media3"
        }
        check(isolationContract.subtitleUrls == null) {
            "Baseline fixtures must not expose subtitle URLs to Media3"
        }
        check(isolationContract.trailerUrls == null) {
            "Baseline fixtures must not expose trailer URLs"
        }
        check(
            listOf(
                items,
                details,
                similar,
                collections,
                bookmarks,
                bookmarkItems,
                emptyBookmarkFolders,
                genres,
                token,
            )
            .none { REMOTE_URL_REGEX.containsMatchIn(it) }) {
            "Baseline fixtures must not contain remote image, media, trailer, or metadata URLs"
        }
    }

    fun decodeIsolationContract(): BaselineFixtureIsolationContract {
        val contentItems = buildList {
            addAll(JSONObject(items).getJSONArray("items").objects())
            add(JSONObject(details).getJSONObject("item"))
            addAll(JSONObject(similar).getJSONArray("items").objects())
            addAll(JSONObject(bookmarkItems).getJSONArray("items").objects())
        }
        val collectionsItems = JSONObject(collections).getJSONArray("items").objects()
        val imdbIds = mutableListOf<String>()
        val imageUrls = mutableListOf<String>()
        val playbackIds = mutableListOf<Int>()
        val mediaUrls = mutableListOf<String>()
        val subtitleUrls = mutableListOf<String>()
        val trailerUrls = mutableListOf<String>()

        fun collectPosterUrls(objectValue: JSONObject) {
            objectValue.optionalObject("posters")?.let { posters ->
                POSTER_FIELDS.mapNotNullTo(imageUrls, posters::optionalString)
            }
        }

        fun collectPlayback(playback: JSONObject) {
            playback.optionalInt("id")?.let(playbackIds::add)
            playback.optionalString("thumbnail")?.let(imageUrls::add)
            playback.optionalArray("subtitles")?.objects()?.forEach { subtitle ->
                subtitle.optionalString("url")?.let(subtitleUrls::add)
            }
            playback.optionalArray("files")?.objects()?.forEach { file ->
                file.optionalObject("url")?.let { urls ->
                    MEDIA_URL_FIELDS.mapNotNullTo(mediaUrls, urls::optionalString)
                }
            }
        }

        contentItems.forEach { item ->
            item.optionalString("imdb")?.let(imdbIds::add)
            collectPosterUrls(item)
            item.optionalObject("trailer")?.let { trailer ->
                TRAILER_URL_FIELDS.mapNotNullTo(trailerUrls, trailer::optionalString)
            }
            item.optionalArray("tracklist")?.objects()?.forEach { track ->
                track.optionalString("url")?.let(mediaUrls::add)
            }
            item.optionalArray("videos")?.objects()?.forEach(::collectPlayback)
            item.optionalArray("seasons")?.objects()?.forEach { season ->
                season.optionalArray("episodes")?.objects()?.forEach(::collectPlayback)
            }
        }
        collectionsItems.forEach(::collectPosterUrls)

        return BaselineFixtureIsolationContract(
            imdbIds = imdbIds.nullIfEmpty(),
            imageUrls = imageUrls.nullIfEmpty(),
            playbackIds = playbackIds.nullIfEmpty(),
            mediaUrls = mediaUrls.nullIfEmpty(),
            subtitleUrls = subtitleUrls.nullIfEmpty(),
            trailerUrls = trailerUrls.nullIfEmpty(),
        )
    }

    fun detailsFor(itemId: Int): String {
        return details.replaceFirst(DETAIL_ID_REGEX, "\"id\":$itemId")
    }

    companion object {
        fun from(context: Context): BaselineFixtures {
            fun read(name: String): String =
                context.assets.open("baseline/$name.json").bufferedReader().use { it.readText() }

            return BaselineFixtures(
                items = read("items"),
                details = read("details"),
                similar = read("similar"),
                collections = read("collections"),
                bookmarks = read("bookmarks"),
                bookmarkItems = read("bookmark_items"),
                emptyBookmarkFolders = read("empty_bookmark_folders"),
                genres = read("genres"),
                emptyList = read("empty"),
                token = read("token"),
            ).also(BaselineFixtures::validate)
        }

        fun synthetic(): BaselineFixtures {
            val items = (1..12).joinToString(",") { id ->
                """{"id":$id,"title":"Baseline item $id","type":"movie"}"""
            }
            val paginated = """{"items":[$items],"pagination":{"current":1,"perpage":12,"total":1,"total_items":12}}"""
            val details = """{"item":{"id":101,"title":"Baseline details","type":"movie","plot":"Deterministic details"}}"""
            val similar = """{"items":[{"id":102,"title":"Baseline similar","type":"movie"}]}"""
            val collectionItems = (1..8).joinToString(",") { id ->
                """{"id":$id,"title":"Baseline collection $id","count":12}"""
            }
            val collections = """{"items":[$collectionItems],"pagination":{"current":1,"perpage":8,"total":1,"total_items":8}}"""
            val bookmarks = """{"items":[{"id":1,"title":"Буду смотреть","count":12}]}"""
            val bookmarkItems = paginated
            val genres = """[{"id":1,"title":"Drama"}]"""
            return BaselineFixtures(
                items = paginated,
                details = details,
                similar = similar,
                collections = collections,
                bookmarks = bookmarks,
                bookmarkItems = bookmarkItems,
                emptyBookmarkFolders = """{"status":1,"folders":[]}""",
                genres = genres,
                emptyList = """{"items":[]}""",
                token = """{"access_token":"baseline-access-token","refresh_token":"baseline-refresh-token"}""",
            ).also(BaselineFixtures::validate)
        }

        private const val MIN_ITEMS = 8
        private const val MIN_COLLECTIONS = 8
        private val DETAIL_ID_REGEX = Regex("""\"id\":\s*101""")
        private val REMOTE_URL_REGEX = Regex("""(?i)https?://""")
        private val POSTER_FIELDS = listOf("small", "medium", "big", "wide")
        private val MEDIA_URL_FIELDS = listOf("http", "hls", "hls2", "hls4")
        private val TRAILER_URL_FIELDS = listOf("url", "file")
    }
}

data class BaselineFixtureIsolationContract(
    val imdbIds: List<String>?,
    val imageUrls: List<String>?,
    val playbackIds: List<Int>?,
    val mediaUrls: List<String>?,
    val subtitleUrls: List<String>?,
    val trailerUrls: List<String>?,
) {
    val tmdbBranchReachable: Boolean
        get() = !imdbIds.isNullOrEmpty()

    val theIntroDbBranchReachable: Boolean
        get() = !imdbIds.isNullOrEmpty() && !playbackIds.isNullOrEmpty()

    val introDbAppBranchReachable: Boolean
        get() = !imdbIds.isNullOrEmpty() && !playbackIds.isNullOrEmpty()

    val coilBranchReachable: Boolean
        get() = !imageUrls.isNullOrEmpty()

    val media3BranchReachable: Boolean
        get() = !playbackIds.isNullOrEmpty() ||
            !mediaUrls.isNullOrEmpty() ||
            !subtitleUrls.isNullOrEmpty()

    val trailerBranchReachable: Boolean
        get() = !trailerUrls.isNullOrEmpty()
}

private fun JSONArray.objects(): List<JSONObject> =
    (0 until length()).map(::getJSONObject)

private fun JSONObject.optionalArray(name: String): JSONArray? =
    if (has(name) && !isNull(name)) getJSONArray(name) else null

private fun JSONObject.optionalObject(name: String): JSONObject? =
    if (has(name) && !isNull(name)) getJSONObject(name) else null

private fun JSONObject.optionalInt(name: String): Int? =
    if (has(name) && !isNull(name)) getInt(name) else null

private fun JSONObject.optionalString(name: String): String? =
    if (has(name) && !isNull(name)) {
        getString(name).takeIf(String::isNotBlank)
    } else {
        null
    }

private fun <T> List<T>.nullIfEmpty(): List<T>? = takeIf(List<T>::isNotEmpty)

private fun QueryMatchMode.toHermetic(): HermeticQueryMatchMode =
    when (this) {
        QueryMatchMode.Exact -> HermeticQueryMatchMode.Exact
        QueryMatchMode.Contains -> HermeticQueryMatchMode.Contains
    }
