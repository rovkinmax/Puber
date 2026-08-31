package com.kino.puber.data.api

import java.util.concurrent.TimeUnit
import mockwebserver3.MockResponse
import mockwebserver3.MockResponseBody
import mockwebserver3.RecordedRequest
import com.kino.puber.playertestfixtures.server.HermeticTestServer
import com.kino.puber.playertestfixtures.server.QueryMatchMode
import com.kino.puber.playertestfixtures.server.ResponsePlan
import okio.Buffer
import okio.BufferedSink

internal class MockWebServerTestSupport : AutoCloseable {

    private val server = HermeticTestServer()

    init {
        server.start()
    }

    val requestCount: Int
        get() = server.requestJournal.entries.size

    fun url(path: String): String = server.url(path)

    fun route(path: String, handler: (RecordedRequest) -> MockResponse) {
        val normalizedPath = normalizePath(path)
        server.addRoute(
            server.route(
                id = normalizedPath,
                method = "*",
                path = normalizedPath,
                queryMode = QueryMatchMode.Contains,
                response = ResponsePlan.Dynamic(handler),
            ),
        )
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
