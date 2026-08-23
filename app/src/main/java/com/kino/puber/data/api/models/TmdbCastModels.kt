package com.kino.puber.data.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class TmdbMediaKind {
    MOVIE,
    TV,
}

data class TmdbMediaRef(
    val id: Int,
    val kind: TmdbMediaKind,
)

data class TmdbCastMember(
    val name: String,
    val profileUrl: String?,
)

@Serializable
data class TmdbCreditsResponse(
    val cast: List<TmdbCastCredit> = emptyList(),
)

@Serializable
data class TmdbAggregateCreditsResponse(
    val cast: List<TmdbCastCredit> = emptyList(),
)

@Serializable
data class TmdbCastCredit(
    val name: String? = null,
    @SerialName("profile_path") val profilePath: String? = null,
)

@Serializable
data class TmdbConfigurationResponse(
    val images: TmdbImageConfiguration? = null,
)

@Serializable
data class TmdbImageConfiguration(
    @SerialName("secure_base_url") val secureBaseUrl: String? = null,
    @SerialName("profile_sizes") val profileSizes: List<String> = emptyList(),
)
