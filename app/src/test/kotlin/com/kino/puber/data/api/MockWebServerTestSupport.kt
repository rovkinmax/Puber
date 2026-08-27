package com.kino.puber.data.api

import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockResponseBody
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import okio.BufferedSink

internal class MockWebServerTestSupport : AutoCloseable {

    private val routes = ConcurrentHashMap<String, (RecordedRequest) -> MockResponse>()
    private val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                routes[request.url.encodedPath]?.invoke(request)
                    ?: response(
                        status = 404,
                        body = """{"error":"unexpected test route"}""",
                    )
        }
    }

    init {
        server.start(InetAddress.getLoopbackAddress(), 0)
    }

    val requestCount: Int
        get() = server.requestCount

    fun url(path: String): String = server.url(path).toString()

    fun route(path: String, handler: (RecordedRequest) -> MockResponse) {
        routes[normalizePath(path)] = handler
    }

    fun response(status: Int, body: String): MockResponse =
        MockResponse.Builder()
            .code(status)
            .addHeader("Content-Type", "application/json")
            .body(body)
            .build()

    fun response(status: Int, body: ByteArray): MockResponse =
        MockResponse.Builder()
            .code(status)
            .body(Buffer().write(body))
            .build()

    fun streamingResponse(
        status: Int,
        chunks: List<ByteArray>,
        afterChunk: (index: Int) -> Unit = {},
    ): MockResponse {
        val body = object : MockResponseBody {
            override val contentLength: Long = chunks.sumOf { it.size.toLong() }

            override fun writeTo(sink: BufferedSink) {
                chunks.forEachIndexed { index, chunk ->
                    sink.write(chunk)
                    sink.flush()
                    afterChunk(index)
                }
            }
        }
        return MockResponse.Builder()
            .code(status)
            .body(body)
            .build()
    }

    fun takeRequest(timeout: Long = 3, unit: TimeUnit = TimeUnit.SECONDS): RecordedRequest =
        checkNotNull(server.takeRequest(timeout, unit)) {
            "Expected a request within $timeout $unit"
        }

    override fun close() {
        server.close()
    }

    private fun normalizePath(path: String): String =
        path.substringBefore('?').let { normalized ->
            if (normalized.length > 1) normalized.trimEnd('/') else normalized
        }
}

internal data class CapturedRequest(
    val method: String,
    val path: String,
    val query: String?,
    val cacheControl: String?,
    val pragma: String?,
)

internal fun RecordedRequest.captureRequest(): CapturedRequest =
    CapturedRequest(
        method = method,
        path = url.encodedPath,
        query = url.query,
        cacheControl = headers["Cache-Control"],
        pragma = headers["Pragma"],
    )
