package com.kino.puber.data.api

import com.kino.puber.core.logger.log
import com.kino.puber.data.api.models.IntroDbAppResponse
import com.kino.puber.data.api.models.IntroDbAppSegment
import com.kino.puber.data.api.models.SkipSegment
import com.kino.puber.data.api.models.SkipSegmentType
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

class IntroDbAppApiClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val httpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
        install(DefaultRequest) {
            url("https://api.introdb.app/")
        }
    }

    suspend fun getSegments(imdbId: String, season: Int?, episode: Int?): Result<List<SkipSegment>> = runCatching {
        val formattedId = if (imdbId.startsWith("tt", ignoreCase = true)) imdbId else "tt$imdbId"
        log("IntroDbApp: GET /segments?imdb_id=$formattedId&season=$season&episode=$episode")
        val response = httpClient.get("segments") {
            parameter("imdb_id", formattedId)
            if (season != null) parameter("season", season)
            if (episode != null) parameter("episode", episode)
        }
        if (!response.status.isSuccess()) {
            log("IntroDbApp: failed with status=${response.status}")
            return@runCatching emptyList()
        }
        val body = response.body<IntroDbAppResponse>()
        mapToSegments(body)
    }

    private fun mapToSegments(response: IntroDbAppResponse): List<SkipSegment> {
        val segments = mutableListOf<SkipSegment>()
        response.intro?.toSegment(SkipSegmentType.INTRO)?.let { segments.add(it) }
        response.recap?.toSegment(SkipSegmentType.RECAP)?.let { segments.add(it) }
        response.outro?.toSegment(SkipSegmentType.CREDITS)?.let { segments.add(it) }
        return segments
    }

    private fun IntroDbAppSegment.toSegment(type: SkipSegmentType): SkipSegment? {
        val start = startMs ?: return null
        return SkipSegment(type = type, startMs = start, endMs = endMs)
    }
}
