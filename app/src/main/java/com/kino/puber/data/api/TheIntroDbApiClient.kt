package com.kino.puber.data.api

import com.kino.puber.core.logger.log
import com.kino.puber.data.api.models.SkipSegment
import com.kino.puber.data.api.models.SkipSegmentType
import com.kino.puber.data.api.models.TheIntroDbMediaResponse
import com.kino.puber.data.api.models.TheIntroDbSegment
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class TheIntroDbApiClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val httpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
        install(DefaultRequest) {
            url("https://api.theintrodb.org/v2/")
        }
    }

    suspend fun getSegments(tmdbId: Int, season: Int?, episode: Int?): Result<List<SkipSegment>> = runCatching {
        log("TheIntroDB: GET /media?tmdb_id=$tmdbId&season=$season&episode=$episode")
        val response = httpClient.get("media") {
            parameter("tmdb_id", tmdbId)
            if (season != null) parameter("season", season)
            if (episode != null) parameter("episode", episode)
        }
        log("TheIntroDB: status=${response.status}")
        when (response.status) {
            HttpStatusCode.OK -> {
                val body = response.body<TheIntroDbMediaResponse>()
                val segments = mapToSegments(body)
                log("TheIntroDB: parsed ${segments.size} segments")
                segments
            }
            HttpStatusCode.NoContent, HttpStatusCode.NotFound -> emptyList()
            HttpStatusCode.TooManyRequests -> {
                log("TheIntroDB rate limited")
                emptyList()
            }
            else -> {
                log("TheIntroDB unexpected status: ${response.status}")
                emptyList()
            }
        }
    }

    private fun mapToSegments(response: TheIntroDbMediaResponse): List<SkipSegment> {
        val segments = mutableListOf<SkipSegment>()
        response.intro?.mapTo(segments, SkipSegmentType.INTRO)
        response.recap?.mapTo(segments, SkipSegmentType.RECAP)
        response.credits?.mapTo(segments, SkipSegmentType.CREDITS)
        response.preview?.mapTo(segments, SkipSegmentType.PREVIEW)
        return segments
    }

    private fun List<TheIntroDbSegment>.mapTo(
        target: MutableList<SkipSegment>,
        type: SkipSegmentType,
    ) {
        for (segment in this) {
            val startMs = segment.startMs ?: continue
            target.add(SkipSegment(type = type, startMs = startMs, endMs = segment.endMs))
        }
    }
}
