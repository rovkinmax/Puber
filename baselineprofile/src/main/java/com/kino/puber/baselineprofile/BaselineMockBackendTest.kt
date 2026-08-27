package com.kino.puber.baselineprofile

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineMockBackendTest {

    private lateinit var backend: BaselineMockBackend

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().context
        backend = BaselineMockBackend(port = 0, fixtures = BaselineFixtures.from(context))
        backend.start()
    }

    @After
    fun tearDown() {
        backend.close()
    }

    @Test
    fun routesMatchMethodPathAndQueryWithoutQueueOrder() {
        backend.reset(BaselineScenario.Startup)

        val serial = request("/v1/items/hot?type=serial")
        val movie = request("/v1/items/hot?type=movie")

        assertEquals(200, serial.code)
        assertEquals(200, movie.code)
        assertTrue(backend.verify().unknownRequests.isEmpty())
    }

    @Test
    fun concurrentRequestsAreDispatchedByRoute() {
        backend.reset(BaselineScenario.TabNavigation)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val movie = executor.submit(Callable { request("/v1/items/fresh?type=movie").code })
            val series = executor.submit(Callable { request("/v1/items/fresh?type=serial").code })
            assertEquals(200, movie.get(5, TimeUnit.SECONDS))
            assertEquals(200, series.get(5, TimeUnit.SECONDS))
        } finally {
            executor.shutdownNow()
        }
        assertTrue(backend.verify().unknownRequests.isEmpty())
    }

    @Test
    fun unknownRouteIsRejectedAndRecordedWithoutSensitiveQueryData() {
        backend.reset(BaselineScenario.Startup)

        val response = request("/v1/not-a-route?token=secret-value")

        assertEquals(404, response.code)
        val unknown = backend.verify().unknownRequests.single()
        assertEquals("/v1/not-a-route", unknown.path)
        assertFalse(unknown.toString().contains("secret-value"))
    }

    @Test
    fun verificationReportsRequiredCountsAndResetStartsNewGeneration() {
        backend.reset(BaselineScenario.Startup)
        val firstGeneration = backend.generationId
        assertTrue(backend.verify().missingRequiredRoutes.isNotEmpty())

        backend.requiredRoutes.forEach { route -> request(backend.url(route)) }
        val verified = backend.verify()
        assertTrue(verified.isSuccessful)
        assertEquals(firstGeneration, verified.generationId)

        backend.reset(BaselineScenario.BrowseAndDetails)
        assertTrue(backend.generationId > firstGeneration)
        assertEquals(0, backend.verify().matchedRequests)
    }

    @Test
    fun realFixtureContractDecodesToTypedOptionalNetworkIsolation() {
        val fixtures = BaselineFixtures.from(InstrumentationRegistry.getInstrumentation().context)
        val isolation = fixtures.decodeIsolationContract()

        fixtures.validate()
        assertTrue(fixtures.items.contains("\"id\""))
        assertTrue(JSONObject(fixtures.collections).getJSONArray("items").length() >= 8)
        assertNull("IMDb identity must be absent", isolation.imdbIds)
        assertNull("Image URLs must be absent", isolation.imageUrls)
        assertNull("Video and episode playback identity must be absent", isolation.playbackIds)
        assertNull("Playback media URLs must be absent", isolation.mediaUrls)
        assertNull("Playback subtitle URLs must be absent", isolation.subtitleUrls)
        assertNull("Trailer URL/file fields must be absent", isolation.trailerUrls)
        assertFalse("TMDB must remain unreachable", isolation.tmdbBranchReachable)
        assertFalse("TheIntroDB must remain unreachable", isolation.theIntroDbBranchReachable)
        assertFalse("IntroDB.app must remain unreachable", isolation.introDbAppBranchReachable)
        assertFalse("Coil must remain unreachable", isolation.coilBranchReachable)
        assertFalse("Media3 must remain unreachable", isolation.media3BranchReachable)
        assertFalse("Trailer playback must remain unreachable", isolation.trailerBranchReachable)
    }

    private fun request(path: String): HttpResponse {
        val target = if (path.startsWith("http")) {
            path
        } else {
            backend.baseUrl.removeSuffix("/") + path
        }
        val connection = URL(target).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 2_000
            connection.readTimeout = 2_000
            HttpResponse(connection.responseCode)
        } finally {
            connection.disconnect()
        }
    }

    private data class HttpResponse(val code: Int)
}
