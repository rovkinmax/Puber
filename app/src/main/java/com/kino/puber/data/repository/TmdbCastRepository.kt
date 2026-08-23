package com.kino.puber.data.repository

import com.kino.puber.core.collections.TypedTtlCache
import com.kino.puber.core.collections.TypedTtlCacheImpl
import com.kino.puber.data.api.TmdbApiClient
import com.kino.puber.data.api.models.TmdbCastCredit
import com.kino.puber.data.api.models.TmdbCastMember
import com.kino.puber.data.api.models.TmdbMediaKind
import com.kino.puber.data.api.normalizedImdbTitleIdOrNull
import kotlin.time.Duration.Companion.minutes

class TmdbCastRepository(
    private val apiClient: TmdbApiClient,
) {

    private val castCache: TypedTtlCache<String, List<TmdbCastMember>> =
        TypedTtlCacheImpl(defaultTtl = CAST_CACHE_TTL)
    private val configurationCache: TypedTtlCache<Unit, ImageConfiguration> =
        TypedTtlCacheImpl(defaultTtl = CONFIGURATION_CACHE_TTL)

    suspend fun getCast(imdbId: String): List<TmdbCastMember> {
        val normalizedImdbId = imdbId.normalizedImdbTitleIdOrNull()
        if (normalizedImdbId == null || !apiClient.isConfigured) {
            return emptyList()
        }

        return castCache.getOrPut(normalizedImdbId) {
            loadCast(normalizedImdbId)
        }
    }

    private suspend fun loadCast(imdbId: String): List<TmdbCastMember> {
        return apiClient.findMediaByImdbId(imdbId)
            .getOrNull()
            ?.let { mediaRef -> loadCredits(mediaRef.id, mediaRef.kind) }
            ?.takeIf(List<TmdbCastCredit>::isNotEmpty)
            ?.let { credits ->
                credits.mapToCastMembers(loadImageConfiguration())
            }
            .orEmpty()
    }

    private suspend fun loadCredits(
        mediaId: Int,
        mediaKind: TmdbMediaKind,
    ): List<TmdbCastCredit> =
        when (mediaKind) {
            TmdbMediaKind.MOVIE -> apiClient.getMovieCredits(mediaId)
                .getOrNull()
                ?.cast
                .orEmpty()

            TmdbMediaKind.TV -> apiClient.getTvAggregateCredits(mediaId)
                .getOrNull()
                ?.cast
                .orEmpty()
        }

    private suspend fun loadImageConfiguration(): ImageConfiguration =
        configurationCache.getOrPut(Unit) {
            apiClient.getConfiguration()
                .getOrNull()
                ?.images
                ?.let { images ->
                    ImageConfiguration(
                        secureBaseUrl = images.secureBaseUrl,
                        profileSize = images.profileSizes.firstSupportedProfileSize(),
                    )
                }
                ?: ImageConfiguration()
        }

    private fun List<TmdbCastCredit>.mapToCastMembers(
        imageConfiguration: ImageConfiguration,
    ): List<TmdbCastMember> =
        mapNotNull { credit ->
            credit.name?.trim()?.takeIf(String::isNotEmpty)?.let { name ->
                TmdbCastMember(
                    name = name,
                    profileUrl = imageConfiguration.profileUrl(credit.profilePath),
                )
            }
        }

    private data class ImageConfiguration(
        val secureBaseUrl: String? = null,
        val profileSize: String? = null,
    ) {
        fun profileUrl(profilePath: String?): String? {
            val baseUrl = secureBaseUrl?.trim()?.trimEnd('/')
            val path = profilePath?.trim()?.trimStart('/')
            return if (baseUrl == null || profileSize == null || path.isNullOrEmpty()) {
                null
            } else {
                "$baseUrl/$profileSize/$path"
            }
        }
    }

    private companion object {
        val CAST_CACHE_TTL = 30.minutes
        val CONFIGURATION_CACHE_TTL = 30.minutes

        fun List<String>.firstSupportedProfileSize(): String? {
            val nonBlankSizes = filter(String::isNotBlank)
            return PREFERRED_PROFILE_SIZES.firstOrNull(nonBlankSizes::contains)
                ?: nonBlankSizes.firstOrNull()
        }

        val PREFERRED_PROFILE_SIZES = listOf("w185", "w342", "h632", "w500", "original")
    }
}
