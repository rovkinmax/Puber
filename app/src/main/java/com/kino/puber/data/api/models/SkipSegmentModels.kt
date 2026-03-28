package com.kino.puber.data.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// region Domain model

enum class SkipSegmentType {
    INTRO, RECAP, CREDITS, PREVIEW
}

data class SkipSegment(
    val type: SkipSegmentType,
    val startMs: Long,
    val endMs: Long?,
)

// endregion

// region TheIntroDB API models

@Serializable
data class TheIntroDbMediaResponse(
    @SerialName("tmdb_id") val tmdbId: Int? = null,
    val type: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val intro: List<TheIntroDbSegment>? = null,
    val recap: List<TheIntroDbSegment>? = null,
    val credits: List<TheIntroDbSegment>? = null,
    val preview: List<TheIntroDbSegment>? = null,
)

@Serializable
data class TheIntroDbSegment(
    @SerialName("start_ms") val startMs: Long? = null,
    @SerialName("end_ms") val endMs: Long? = null,
    val confidence: Double? = null,
    @SerialName("submission_count") val submissionCount: Int? = null,
)

// endregion

// region TMDB API models

@Serializable
data class TmdbFindResponse(
    @SerialName("tv_results") val tvResults: List<TmdbResult>? = null,
    @SerialName("movie_results") val movieResults: List<TmdbResult>? = null,
)

@Serializable
data class TmdbResult(
    val id: Int,
)

// endregion
