package com.kino.puber.data.api

import com.kino.puber.BuildConfig
import com.kino.puber.data.api.models.TmdbAggregateCreditsResponse
import com.kino.puber.data.api.models.TmdbCreditsResponse
import com.kino.puber.data.api.models.TmdbFindResponse
import com.kino.puber.data.api.models.TmdbMediaKind
import com.kino.puber.data.api.models.TmdbMediaRef
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

private val IMDB_TITLE_ID_REGEX = Regex("(?:tt)?([0-9]{7,})", RegexOption.IGNORE_CASE)

internal fun String.normalizedImdbTitleIdOrNull(): String? =
    IMDB_TITLE_ID_REGEX
        .matchEntire(trim())
        ?.groupValues
        ?.get(1)
        ?.let { digits -> "tt$digits" }

class TmdbApiClient {

    val isConfigured: Boolean
        get() = BuildConfig.TMDB_READ_ACCESS_TOKEN.isNotBlank()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val httpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
        install(DefaultRequest) {
            url("https://api.themoviedb.org/3/")
            headers.append("Authorization", "Bearer ${BuildConfig.TMDB_READ_ACCESS_TOKEN}")
            headers.append("Accept", "application/json")
        }
    }

    suspend fun findByImdbId(imdbId: String): Result<Int?> = runCatching {
        val formattedId = if (imdbId.startsWith("tt", ignoreCase = true)) imdbId else "tt$imdbId"
        val response = httpClient.get("find/$formattedId") {
            parameter("external_source", "imdb_id")
        }
        if (!response.status.isSuccess()) {
            return@runCatching null
        }
        val body = response.body<TmdbFindResponse>()
        body.tvResults?.firstOrNull()?.id ?: body.movieResults?.firstOrNull()?.id
    }

    suspend fun findMediaByImdbId(imdbId: String): Result<TmdbMediaRef?> = try {
        val formattedId = imdbId.normalizedImdbTitleIdOrNull()
        if (!isConfigured || formattedId == null) {
            return Result.success(null)
        }

        val response = httpClient.get("find/$formattedId") {
            parameter("external_source", "imdb_id")
        }
        if (!response.status.isSuccess()) {
            return Result.success(null)
        }

        val body = response.body<TmdbFindResponse>()
        val mediaRef = body.tvResults?.firstOrNull()?.id?.let { id ->
            TmdbMediaRef(id = id, kind = TmdbMediaKind.TV)
        } ?: body.movieResults?.firstOrNull()?.id?.let { id ->
            TmdbMediaRef(id = id, kind = TmdbMediaKind.MOVIE)
        }
        Result.success(mediaRef)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        Result.failure(error)
    }

    suspend fun getMovieCredits(tmdbId: Int): Result<TmdbCreditsResponse> = try {
        val response = httpClient.get("movie/$tmdbId/credits") {
            parameter("language", "ru-RU")
        }
        check(response.status.isSuccess())
        Result.success(response.body())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        Result.failure(error)
    }

    suspend fun getTvAggregateCredits(tmdbId: Int): Result<TmdbAggregateCreditsResponse> = try {
        val response = httpClient.get("tv/$tmdbId/aggregate_credits") {
            parameter("language", "ru-RU")
        }
        check(response.status.isSuccess())
        Result.success(response.body())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        Result.failure(error)
    }

    suspend fun getConfiguration(): Result<com.kino.puber.data.api.models.TmdbConfigurationResponse> = try {
        val response = httpClient.get("configuration")
        check(response.status.isSuccess())
        Result.success(response.body())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        Result.failure(error)
    }
}
