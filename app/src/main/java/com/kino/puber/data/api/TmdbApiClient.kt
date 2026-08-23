package com.kino.puber.data.api

import com.kino.puber.BuildConfig
import com.kino.puber.data.api.models.TmdbAggregateCreditsResponse
import com.kino.puber.data.api.models.TmdbConfigurationResponse
import com.kino.puber.data.api.models.TmdbCreditsResponse
import com.kino.puber.data.api.models.TmdbFindResponse
import com.kino.puber.data.api.models.TmdbMediaKind
import com.kino.puber.data.api.models.TmdbMediaRef
import com.kino.puber.data.api.models.TmdbSeasonDetailsResponse
import com.kino.puber.data.api.models.TmdbTvDetailsResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

private val IMDB_TITLE_ID_REGEX = Regex("(?:tt)?([0-9]{7,})", RegexOption.IGNORE_CASE)

internal fun String.normalizedImdbTitleIdOrNull(): String? =
    IMDB_TITLE_ID_REGEX
        .matchEntire(trim())
        ?.groupValues
        ?.get(1)
        ?.let { digits -> "tt$digits" }

class TmdbApiClient private constructor(
    private val httpClient: HttpClient,
) {

    constructor() : this(createHttpClient())

    val isConfigured: Boolean
        get() = BuildConfig.TMDB_READ_ACCESS_TOKEN.isNotBlank()

    suspend fun findTvByImdbId(imdbId: String): Result<Int?> = apiResult {
        val response = httpClient.get("${BASE_URL}find/${imdbId.withTmdbPrefix()}") {
            parameter("external_source", "imdb_id")
        }
        response.requireSuccess()
        response.body<TmdbFindResponse>().tvResults?.firstOrNull()?.id
    }

    suspend fun getTvDetails(seriesId: Int): Result<TmdbTvDetailsResponse> = apiResult {
        httpClient.get("${BASE_URL}tv/$seriesId")
            .also { it.requireSuccess() }
            .body()
    }

    suspend fun getTvSeasonDetails(
        seriesId: Int,
        seasonNumber: Int,
    ): Result<TmdbSeasonDetailsResponse> = apiResult {
        httpClient.get("${BASE_URL}tv/$seriesId/season/$seasonNumber")
            .also { it.requireSuccess() }
            .body()
    }

    suspend fun findByImdbId(imdbId: String): Result<Int?> = apiResult {
        val response = httpClient.get("${BASE_URL}find/${imdbId.withTmdbPrefix()}") {
            parameter("external_source", "imdb_id")
        }
        if (!response.status.isSuccess()) {
            return@apiResult null
        }
        val body = response.body<TmdbFindResponse>()
        body.tvResults?.firstOrNull()?.id ?: body.movieResults?.firstOrNull()?.id
    }

    suspend fun findMediaByImdbId(imdbId: String): Result<TmdbMediaRef?> {
        val formattedId = imdbId.normalizedImdbTitleIdOrNull()
        if (!isConfigured || formattedId == null) {
            return Result.success(null)
        }
        return apiResult {
            val response = httpClient.get("${BASE_URL}find/$formattedId") {
                parameter("external_source", "imdb_id")
            }
            if (!response.status.isSuccess()) {
                return@apiResult null
            }
            val body = response.body<TmdbFindResponse>()
            body.tvResults?.firstOrNull()?.id?.let { id ->
                TmdbMediaRef(id = id, kind = TmdbMediaKind.TV)
            } ?: body.movieResults?.firstOrNull()?.id?.let { id ->
                TmdbMediaRef(id = id, kind = TmdbMediaKind.MOVIE)
            }
        }
    }

    suspend fun getMovieCredits(tmdbId: Int): Result<TmdbCreditsResponse> = apiResult {
        val response = httpClient.get("${BASE_URL}movie/$tmdbId/credits") {
            parameter("language", "ru-RU")
        }
        check(response.status.isSuccess())
        response.body()
    }

    suspend fun getTvAggregateCredits(tmdbId: Int): Result<TmdbAggregateCreditsResponse> = apiResult {
        val response = httpClient.get("${BASE_URL}tv/$tmdbId/aggregate_credits") {
            parameter("language", "ru-RU")
        }
        check(response.status.isSuccess())
        response.body()
    }

    suspend fun getConfiguration(): Result<TmdbConfigurationResponse> = apiResult {
        val response = httpClient.get("${BASE_URL}configuration")
        check(response.status.isSuccess())
        response.body()
    }

    private suspend fun <T> apiResult(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        Result.failure(error)
    }

    private fun String.withTmdbPrefix(): String =
        if (startsWith("tt", ignoreCase = true)) this else "tt$this"

    private fun HttpResponse.requireSuccess() {
        if (!status.isSuccess()) {
            throw TmdbApiException(status.value)
        }
    }

    internal companion object {
        private const val BASE_URL = "https://api.themoviedb.org/3/"

        fun forTesting(httpClient: HttpClient): TmdbApiClient = TmdbApiClient(httpClient)

        private fun createHttpClient(): HttpClient = HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                    },
                )
            }
            install(DefaultRequest) {
                url(BASE_URL)
                headers.append("Authorization", "Bearer ${BuildConfig.TMDB_READ_ACCESS_TOKEN}")
                headers.append("Accept", "application/json")
            }
        }
    }
}

class TmdbApiException(val statusCode: Int) : IOException("TMDB request failed with HTTP $statusCode")
