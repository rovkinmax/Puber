package com.kino.puber.data.api

import com.kino.puber.BuildConfig
import com.kino.puber.core.logger.log
import com.kino.puber.data.api.models.TmdbFindResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class TmdbApiClient {

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
        log("TMDB: GET /find/$formattedId, token=${BuildConfig.TMDB_READ_ACCESS_TOKEN.take(10)}...")
        val response = httpClient.get("find/$formattedId") {
            parameter("external_source", "imdb_id")
        }
        if (!response.status.isSuccess()) {
            log("TMDB: /find failed with status=${response.status}")
            return@runCatching null
        }
        val body = response.body<TmdbFindResponse>()
        val id = body.tvResults?.firstOrNull()?.id ?: body.movieResults?.firstOrNull()?.id
        log("TMDB: /find result tmdbId=$id (tv=${body.tvResults?.size}, movie=${body.movieResults?.size})")
        id
    }
}
