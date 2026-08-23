package com.kino.puber.data.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TmdbTvDetailsResponse(
    val id: Int,
    @SerialName("next_episode_to_air") val nextEpisodeToAir: TmdbEpisodeResponse? = null,
    val seasons: List<TmdbSeasonSummaryResponse> = emptyList(),
)

@Serializable
data class TmdbSeasonSummaryResponse(
    val id: Int? = null,
    @SerialName("season_number") val seasonNumber: Int? = null,
    @SerialName("air_date") val airDate: String? = null,
)

@Serializable
data class TmdbSeasonDetailsResponse(
    val id: Int,
    @SerialName("season_number") val seasonNumber: Int? = null,
    @SerialName("air_date") val airDate: String? = null,
    val episodes: List<TmdbEpisodeResponse> = emptyList(),
)

@Serializable
data class TmdbEpisodeResponse(
    val id: Int? = null,
    @SerialName("episode_number") val episodeNumber: Int? = null,
    @SerialName("season_number") val seasonNumber: Int? = null,
    val name: String? = null,
    @SerialName("air_date") val airDate: String? = null,
    @SerialName("still_path") val stillPath: String? = null,
)
