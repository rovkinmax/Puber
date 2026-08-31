package com.kino.puber.playertestfixtures.server

import java.io.Closeable
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockResponseBody
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import okhttp3.HttpUrl
import okio.BufferedSink

/**
 * A deterministic, loopback-only HTTP server for host and instrumentation
 * player tests. Routes are matched by request identity rather than queue
 * order, so concurrent Media3 requests are safe to dispatch.
 */
class HermeticTestServer(
    private val port: Int = 0,
) : Closeable {

    private val server = MockWebServer()
    private val lock = Any()
    private val journal = HermeticRequestJournal()
    private var routes: List<HermeticRoute> = emptyList()
    private var started = false

    internal var beforeRequestAdmissionForTest: () -> Unit = {}

    val baseUrl: String
        get() {
            check(started) { "Call start() before reading baseUrl" }
            return server.url("/").toString()
        }

    val generationId: Long
        get() = journal.generationId

    val requestJournal: HermeticRequestJournal.Snapshot
        get() = journal.snapshot()

    val activeRequestCount: Int
        get() = journal.activeRequestCount

    fun start() {
        synchronized(lock) {
            check(!started) { "HermeticTestServer is already started" }
            server.dispatcher = dispatcher()
            server.start(InetAddress.getByName("127.0.0.1"), port)
            started = true
        }
    }

    fun reset(newRoutes: List<HermeticRoute>) {
        synchronized(lock) {
            check(started) { "Call start() before reset()" }
            journal.reset()
            routes = newRoutes.toList()
        }
    }

    fun addRoute(route: HermeticRoute) {
        synchronized(lock) {
            check(started) { "Call start() before addRoute()" }
            routes = routes + route
        }
    }

    fun takeRequest(
        timeout: Long,
        unit: TimeUnit,
    ): RecordedRequest? = server.takeRequest(timeout, unit)

    fun route(
        id: String,
        method: String = "GET",
        path: String,
        query: Map<String, String> = emptyMap(),
        queryMode: QueryMatchMode = QueryMatchMode.Exact,
        response: ResponsePlan,
        required: Boolean = false,
        minimumRequests: Int = 1,
    ): HermeticRoute = HermeticRoute(
        id = id,
        method = method,
        path = path,
        query = query,
        queryMode = queryMode,
        response = response,
        required = required,
        minimumRequests = minimumRequests,
    )

    fun url(path: String, query: Map<String, String> = emptyMap()): String {
        check(started) { "Call start() before url()" }
        return server.url(path).newBuilder().apply {
            query.forEach { (name, value) -> addQueryParameter(name, value) }
        }.build().toString()
    }

    fun awaitQuiescence(
        timeout: Long = 5,
        unit: TimeUnit = TimeUnit.SECONDS,
    ) {
        val deadline = System.nanoTime() + unit.toNanos(timeout)
        while (System.nanoTime() < deadline) {
            if (activeRequestCount == 0) return
            Thread.yield()
        }
        error("Hermetic server still has $activeRequestCount active request(s)")
    }

    fun verifyRequiredRoutes(): List<String> =
        synchronized(lock) {
            routes
                .filter { it.required && journal.matchedCount(it.id) < it.minimumRequests }
                .map { it.description }
        }

    override fun close() {
        synchronized(lock) {
            if (!started) return
            started = false
            server.close()
        }
    }

    private fun dispatcher() = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val (route, requestId) = synchronized(lock) {
                val selectedRoute = routes.firstOrNull { it.matches(request) }
                beforeRequestAdmissionForTest()
                selectedRoute to journal.begin(request, selectedRoute?.id)
            }
            if (route == null) {
                return ResponsePlan.Text(
                    status = 404,
                    body = """{"error":"unknown hermetic route"}""",
                    contentType = "application/json; charset=utf-8",
                ).toMockResponse(request) {
                    journal.complete(requestId, ResponseOutcome.UnknownRoute)
                }
            }

            val occurrence = journal.matchedCount(route.id)
            return route.response.responseFor(request, occurrence).toMockResponse(request) {
                journal.complete(requestId, ResponseOutcome.Completed(it))
            }
        }
    }

    companion object {
        fun bytes(
            body: ByteArray,
            status: Int = 200,
            contentType: String = "application/octet-stream",
        ): ResponsePlan = ResponsePlan.Bytes(status, body, contentType)

        fun text(
            body: String,
            status: Int = 200,
            contentType: String = "text/plain; charset=utf-8",
        ): ResponsePlan = ResponsePlan.Text(status, body, contentType)

        fun redirect(location: String, status: Int = 302): ResponsePlan =
            ResponsePlan.Redirect(status, location)

        fun sequence(vararg responses: ResponsePlan): ResponsePlan =
            ResponsePlan.Sequence(responses.toList())

        fun delayed(
            gate: CountDownLatch,
            body: ByteArray,
            status: Int = 200,
            contentType: String = "application/octet-stream",
        ): ResponsePlan = ResponsePlan.Delayed(status, body, contentType, gate)

        fun truncated(
            body: ByteArray,
            bytesToWrite: Int,
            status: Int = 200,
            contentType: String = "application/octet-stream",
        ): ResponsePlan = ResponsePlan.Truncated(status, body, contentType, bytesToWrite)

        fun disconnected(
            body: ByteArray = ByteArray(0),
            status: Int = 200,
            contentType: String = "application/octet-stream",
        ): ResponsePlan = ResponsePlan.Disconnected(status, body, contentType)
    }
}

data class HermeticRoute(
    val id: String,
    val method: String,
    val path: String,
    val query: Map<String, String>,
    val queryMode: QueryMatchMode,
    val response: ResponsePlan,
    val required: Boolean,
    val minimumRequests: Int,
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

    fun matches(request: RecordedRequest): Boolean =
        (method == "*" || method.equals(request.method, ignoreCase = true)) &&
            matches(request.url)

    fun matches(url: HttpUrl): Boolean {
        val expectedPath = path.trimEnd('/').ifEmpty { "/" }
        val actualPath = url.encodedPath.trimEnd('/').ifEmpty { "/" }
        if (actualPath != expectedPath) return false
        val actualQuery = url.queryParameterNames.associateWith { name ->
            url.queryParameter(name).orEmpty()
        }
        return when (queryMode) {
            QueryMatchMode.Exact -> actualQuery == query
            QueryMatchMode.Contains -> query.all { actualQuery[it.key] == it.value }
        }
    }
}

enum class QueryMatchMode {
    Exact,
    Contains,
}

sealed interface ResponsePlan {
    fun responseFor(request: RecordedRequest, occurrence: Int): ResponsePlan =
        this

    fun toMockResponse(
        request: RecordedRequest,
        onComplete: (status: Int) -> Unit,
    ): MockResponse

    data class Bytes(
        val status: Int,
        val body: ByteArray,
        val contentType: String,
        val headers: Map<String, String> = emptyMap(),
    ) : ResponsePlan {
        override fun toMockResponse(
            request: RecordedRequest,
            onComplete: (status: Int) -> Unit,
        ): MockResponse =
            MockResponse.Builder()
                .code(status)
                .addHeader("Content-Type", contentType)
                .addHeader("Connection", "close")
                .apply {
                    this@Bytes.headers.forEach { (name, value) ->
                        addHeader(name, value)
                    }
                }
                .body(
                    completedBody(
                        body = body,
                        status = status,
                        onComplete = onComplete,
                        declaredLength = body.size.toLong(),
                    ),
                )
                .build()
    }

    data class Text(
        val status: Int,
        val body: String,
        val contentType: String,
        val headers: Map<String, String> = emptyMap(),
    ) : ResponsePlan {
        override fun toMockResponse(
            request: RecordedRequest,
            onComplete: (status: Int) -> Unit,
        ): MockResponse =
            Bytes(status, body.toByteArray(), contentType, headers)
                .toMockResponse(request, onComplete)
    }

    data class Redirect(
        val status: Int,
        val location: String,
    ) : ResponsePlan {
        override fun toMockResponse(
            request: RecordedRequest,
            onComplete: (status: Int) -> Unit,
        ): MockResponse =
            MockResponse.Builder()
                .code(status)
                .addHeader("Location", location)
                .addHeader("Connection", "close")
                .body(
                    completedBody(
                        body = ByteArray(0),
                        status = status,
                        onComplete = onComplete,
                        declaredLength = 0,
                    ),
                )
                .build()
    }

    data class Sequence(
        val responses: List<ResponsePlan>,
    ) : ResponsePlan {
        init {
            require(responses.isNotEmpty()) { "A response sequence cannot be empty" }
        }

        override fun responseFor(request: RecordedRequest, occurrence: Int): ResponsePlan =
            responses[(occurrence - 1).coerceAtMost(responses.lastIndex)]

        override fun toMockResponse(
            request: RecordedRequest,
            onComplete: (status: Int) -> Unit,
        ): MockResponse =
            responses.last().toMockResponse(request, onComplete)
    }

    data class Delayed(
        val status: Int,
        val body: ByteArray,
        val contentType: String,
        val gate: CountDownLatch,
    ) : ResponsePlan {
        override fun toMockResponse(
            request: RecordedRequest,
            onComplete: (status: Int) -> Unit,
        ): MockResponse =
            MockResponse.Builder()
                .code(status)
                .addHeader("Content-Type", contentType)
                .addHeader("Connection", "close")
                .body(
                    completedBody(
                        body = body,
                        status = status,
                        onComplete = onComplete,
                        gate = gate,
                        declaredLength = body.size.toLong(),
                    ),
                )
                .build()
    }

    data class Truncated(
        val status: Int,
        val body: ByteArray,
        val contentType: String,
        val bytesToWrite: Int,
    ) : ResponsePlan {
        init {
            require(bytesToWrite in 0..body.size) {
                "bytesToWrite must be between zero and body size"
            }
        }

        override fun toMockResponse(
            request: RecordedRequest,
            onComplete: (status: Int) -> Unit,
        ): MockResponse {
            val partial = body.copyOf(bytesToWrite)
            return MockResponse.Builder()
                .code(status)
                .addHeader("Content-Type", contentType)
                .addHeader("Connection", "close")
                .body(
                    completedBody(
                        body = partial,
                        status = status,
                        onComplete = onComplete,
                        declaredLength = body.size.toLong(),
                    ),
                )
                .setHeader("Content-Length", body.size)
                .onResponseEnd(SocketEffect.CloseSocket())
                .build()
        }
    }

    data class Disconnected(
        val status: Int,
        val body: ByteArray,
        val contentType: String,
    ) : ResponsePlan {
        override fun toMockResponse(
            request: RecordedRequest,
            onComplete: (status: Int) -> Unit,
        ): MockResponse =
            MockResponse.Builder()
                .code(status)
                .addHeader("Content-Type", contentType)
                .addHeader("Connection", "close")
                .body(
                    completedBody(
                        body = body,
                        status = status,
                        onComplete = onComplete,
                        declaredLength = body.size.toLong(),
                    ),
                )
                .onResponseBody(SocketEffect.CloseSocket())
                .build()
    }

    data class Range(
        val body: ByteArray,
        val contentType: String = "application/octet-stream",
        val status: Int = 200,
    ) : ResponsePlan {
        override fun responseFor(request: RecordedRequest, occurrence: Int): ResponsePlan {
            val range = parseRange(request.headers["Range"], body.size)
                ?: return Bytes(status, body, contentType)
            return Bytes(
                status = 206,
                body = body.copyOfRange(range.first, range.last + 1),
                contentType = contentType,
                headers = mapOf(
                    "Accept-Ranges" to "bytes",
                    "Content-Range" to "bytes ${range.first}-${range.last}/${body.size}",
                ),
            )
        }

        override fun toMockResponse(
            request: RecordedRequest,
            onComplete: (status: Int) -> Unit,
        ): MockResponse =
            Bytes(status, body, contentType, mapOf("Accept-Ranges" to "bytes"))
                .toMockResponse(request, onComplete)
    }

    class Dynamic(
        private val handler: (RecordedRequest) -> MockResponse,
    ) : ResponsePlan {
        override fun toMockResponse(
            request: RecordedRequest,
            onComplete: (status: Int) -> Unit,
        ): MockResponse {
            val response = handler(request)
            val responseBody = response.body
            val body = responseBody
                ?: completedBody(ByteArray(0), response.code, onComplete, declaredLength = 0)
            return response.newBuilder()
                .body(
                    if (responseBody == null) body
                    else completedBody(responseBody, response.code, onComplete),
                )
                .build()
        }
    }
}

private fun completedBody(
    body: ByteArray,
    status: Int,
    onComplete: (status: Int) -> Unit,
    gate: CountDownLatch? = null,
    declaredLength: Long,
): MockResponseBody =
    object : MockResponseBody {
        override val contentLength: Long = declaredLength

        override fun writeTo(sink: BufferedSink) {
            try {
                if (gate != null) {
                    check(gate.await(10, TimeUnit.SECONDS)) {
                        "Response gate was not released"
                    }
                }
                sink.write(body)
                sink.flush()
                onComplete(status)
            } catch (error: Throwable) {
                onComplete(599)
                throw error
            }
        }
    }

private fun completedBody(
    body: MockResponseBody,
    status: Int,
    onComplete: (status: Int) -> Unit,
): MockResponseBody =
    object : MockResponseBody {
        override val contentLength: Long = body.contentLength

        override fun writeTo(sink: BufferedSink) {
            try {
                body.writeTo(sink)
                sink.flush()
                onComplete(status)
            } catch (error: Throwable) {
                onComplete(599)
                throw error
            }
        }
    }

private fun parseRange(header: String?, size: Int): IntRange? {
    if (header == null || !header.startsWith("bytes=")) return null
    val values = header.removePrefix("bytes=").substringBefore(',').split('-', limit = 2)
    if (values.size != 2) return null
    val start = values[0].toIntOrNull() ?: return null
    val end = values[1].toIntOrNull() ?: size - 1
    if (start < 0 || start >= size || end < start) return null
    return start..end.coerceAtMost(size - 1)
}
