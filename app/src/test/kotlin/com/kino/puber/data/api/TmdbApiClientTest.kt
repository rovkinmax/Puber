package com.kino.puber.data.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class TmdbApiClientTest {

    @Test
    fun findTvByImdbId_usesNormalizedIdAndOnlyTvResults() = runTest {
        val client = client { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/3/find/tt123", request.url.encodedPath)
            assertEquals("imdb_id", request.url.parameters["external_source"])
            respondJson("""{"tv_results":[{"id":101}],"movie_results":[{"id":202}]}""")
        }

        assertEquals(101, TmdbApiClient.forTesting(client).findTvByImdbId("123").getOrThrow())
    }

    @Test
    fun findTvByImdbId_returnsNoMatchWithoutFallingBackToMovieResults() = runTest {
        val api = TmdbApiClient.forTesting(
            client { respondJson("""{"tv_results":[],"movie_results":[{"id":202}]}""") },
        )

        assertNull(api.findTvByImdbId("tt123").getOrThrow())
    }

    @Test
    fun tvEndpoints_useExpectedPathsAndDecodeNullableDates() = runTest {
        var requestNumber = 0
        val api = TmdbApiClient.forTesting(
            client { request ->
                requestNumber += 1
                when (requestNumber) {
                    1 -> {
                        assertEquals("/3/tv/101", request.url.encodedPath)
                        respondJson(
                            """
                            {
                              "id":101,
                              "next_episode_to_air":{
                                "id":303,
                                "season_number":2,
                                "episode_number":4,
                                "name":"Next",
                                "air_date":null
                              },
                              "seasons":[
                                {"id":11,"season_number":2,"air_date":"2026-09-01"}
                              ]
                            }
                            """.trimIndent(),
                        )
                    }

                    else -> {
                        assertEquals("/3/tv/101/season/2", request.url.encodedPath)
                        respondJson(
                            """
                            {
                              "id":11,
                              "season_number":2,
                              "air_date":"not-a-date",
                              "episodes":[
                                {"id":404,"episode_number":4,"name":"Episode","air_date":null}
                              ]
                            }
                            """.trimIndent(),
                        )
                    }
                }
            },
        )

        val details = api.getTvDetails(101).getOrThrow()
        val season = api.getTvSeasonDetails(101, 2).getOrThrow()

        assertEquals(null, details.nextEpisodeToAir?.airDate)
        assertEquals("not-a-date", season.airDate)
        assertEquals(null, season.episodes.single().airDate)
    }

    @Test
    fun newScheduleEndpoints_returnFailureForHttpErrorsIncludingRateLimit() = runTest {
        val api = TmdbApiClient.forTesting(
            client { respondJson("{}", HttpStatusCode.TooManyRequests) },
        )

        val result = api.getTvDetails(101)

        assertTrue(result.isFailure)
        assertEquals(429, assertInstanceOf(TmdbApiException::class.java, result.exceptionOrNull()).statusCode)
    }

    @Test
    fun existingFindByImdbId_preservesTvFirstMovieFallbackAndNonSuccessBehavior() = runTest {
        val responses = ArrayDeque(
            listOf(
                """{"tv_results":[{"id":101}],"movie_results":[{"id":202}]}""" to HttpStatusCode.OK,
                """{"tv_results":[],"movie_results":[{"id":202}]}""" to HttpStatusCode.OK,
                "{}" to HttpStatusCode.BadGateway,
            ),
        )
        val api = TmdbApiClient.forTesting(
            client {
                val (body, status) = responses.removeFirst()
                respondJson(body, status)
            },
        )

        assertEquals(101, api.findByImdbId("tt123").getOrThrow())
        assertEquals(202, api.findByImdbId("123").getOrThrow())
        assertNull(api.findByImdbId("123").getOrThrow())
    }

    @Test
    fun castAndConfigurationEndpoints_preservePathsLocalizationAndDecoding() = runTest {
        var requestNumber = 0
        val api = TmdbApiClient.forTesting(
            client { request ->
                requestNumber += 1
                when (requestNumber) {
                    1 -> {
                        assertEquals("/3/movie/7/credits", request.url.encodedPath)
                        assertEquals("ru-RU", request.url.parameters["language"])
                        respondJson("""{"cast":[{"name":"Actor","profile_path":"/actor.jpg"}]}""")
                    }

                    2 -> {
                        assertEquals("/3/tv/8/aggregate_credits", request.url.encodedPath)
                        assertEquals("ru-RU", request.url.parameters["language"])
                        respondJson("""{"cast":[{"name":"Actor TV","profile_path":null}]}""")
                    }

                    else -> {
                        assertEquals("/3/configuration", request.url.encodedPath)
                        respondJson(
                            """
                            {
                              "images":{
                                "secure_base_url":"https://image.tmdb.org/t/p/",
                                "profile_sizes":["w92","w185"]
                              }
                            }
                            """.trimIndent(),
                        )
                    }
                }
            },
        )

        assertEquals("Actor", api.getMovieCredits(7).getOrThrow().cast.single().name)
        assertEquals("Actor TV", api.getTvAggregateCredits(8).getOrThrow().cast.single().name)
        assertEquals(
            listOf("w92", "w185"),
            api.getConfiguration().getOrThrow().images?.profileSizes,
        )
    }

    @Test
    fun scheduleEndpoint_rethrowsCancellation() = runTest {
        val cancellation = kotlinx.coroutines.CancellationException("cancelled")
        val api = TmdbApiClient.forTesting(
            client { throw cancellation },
        )

        val thrown = try {
            api.getTvDetails(101)
            null
        } catch (error: kotlinx.coroutines.CancellationException) {
            error
        }

        assertEquals(cancellation.message, thrown?.message)
    }

    private fun client(
        handler: MockRequestHandler =
            { respondJson("{}") },
    ): HttpClient {
        val engine = MockEngine { request -> handler(request) }
        return HttpClient(engine) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                    },
                )
            }
        }
    }

    private fun MockRequestHandleScope.respondJson(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = respond(
        content = body,
        status = status,
        headers = headersOf(
            HttpHeaders.ContentType,
            ContentType.Application.Json.toString(),
        ),
    )
}
