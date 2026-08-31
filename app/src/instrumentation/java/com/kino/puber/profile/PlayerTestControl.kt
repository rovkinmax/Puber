package com.kino.puber.profile

import com.kino.puber.BuildConfig
import com.kino.puber.playertestfixtures.server.HermeticRequestJournal
import com.kino.puber.playertestfixtures.server.HermeticRoute
import com.kino.puber.playertestfixtures.server.HermeticTestServer
import com.kino.puber.playertestfixtures.server.QueryMatchMode
import com.kino.puber.playertestfixtures.server.ResponsePlan
import java.io.Closeable
import java.util.concurrent.TimeUnit
import mockwebserver3.RecordedRequest

/**
 * Instrumentation-owned lifecycle boundary for the common hermetic player
 * server. Android tests may define routes and inspect bounded evidence, but
 * they do not create or own the underlying server.
 */
internal class PlayerTestControl(
    port: Int = BuildConfig.BASELINE_MOCK_PORT,
) : Closeable {

    private val server = HermeticTestServer(port = port)

    val requestJournal: HermeticRequestJournal.Snapshot
        get() = server.requestJournal

    val activeRequestCount: Int
        get() = server.activeRequestCount

    fun start(initialRoutes: List<HermeticRoute> = emptyList()) {
        server.start()
        server.reset(initialRoutes)
    }

    fun reset(routes: List<HermeticRoute>) {
        server.reset(routes)
    }

    fun route(
        id: String,
        method: String = "GET",
        path: String,
        query: Map<String, String> = emptyMap(),
        queryMode: QueryMatchMode = QueryMatchMode.Exact,
        response: ResponsePlan,
        required: Boolean = false,
        minimumRequests: Int = 1,
    ): HermeticRoute = server.route(
        id = id,
        method = method,
        path = path,
        query = query,
        queryMode = queryMode,
        response = response,
        required = required,
        minimumRequests = minimumRequests,
    )

    fun url(path: String, query: Map<String, String> = emptyMap()): String =
        server.url(path, query)

    fun takeRequest(
        timeout: Long,
        unit: TimeUnit,
    ): RecordedRequest? = server.takeRequest(timeout, unit)

    fun awaitQuiescence(
        timeout: Long = DEFAULT_QUIESCENCE_TIMEOUT_SECONDS,
        unit: TimeUnit = TimeUnit.SECONDS,
    ) {
        server.awaitQuiescence(timeout, unit)
    }

    fun assertRequiredRoutesRequested() {
        val missing = server.verifyRequiredRoutes()
        if (missing.isNotEmpty()) {
            throw IllegalStateException("Required routes were not requested: $missing")
        }
    }

    fun assertNoUnknownRequests(
        maxDiagnostics: Int = DEFAULT_MAX_DIAGNOSTIC_REQUESTS,
    ) {
        val unknownRequests = requestJournal.unknownRequests
        if (unknownRequests.isNotEmpty()) {
            throw AssertionError(
                "Unexpected loopback requests: ${unknownRequests.takeLast(maxDiagnostics)}",
            )
        }
    }

    override fun close() {
        server.close()
    }

    private companion object {
        const val DEFAULT_QUIESCENCE_TIMEOUT_SECONDS = 10L
        const val DEFAULT_MAX_DIAGNOSTIC_REQUESTS = 12
    }
}
