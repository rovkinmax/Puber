package com.kino.puber.data.repository

import com.kino.puber.core.collections.TypedTtlCache
import com.kino.puber.core.collections.TypedTtlCacheImpl
import com.kino.puber.data.api.TmdbApiClient
import com.kino.puber.data.api.config.TmdbImageConfig
import com.kino.puber.data.api.models.TmdbCastCredit
import com.kino.puber.data.api.models.TmdbCastMember
import com.kino.puber.data.api.models.TmdbImageConfiguration
import com.kino.puber.data.api.models.TmdbMediaKind
import com.kino.puber.data.api.normalizedImdbTitleIdOrNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.minutes

class TmdbCastRepository(
    private val apiClient: TmdbApiClient,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    private val castCache: TypedTtlCache<String, List<TmdbCastMember>> =
        TypedTtlCacheImpl(defaultTtl = CAST_CACHE_TTL)
    private val configurationCache: TypedTtlCache<Unit, TmdbImageConfiguration> =
        TypedTtlCacheImpl(defaultTtl = CONFIGURATION_CACHE_TTL)

    suspend fun getCast(imdbId: String): List<TmdbCastMember> {
        val normalizedImdbId = imdbId.normalizedImdbTitleIdOrNull()
        if (normalizedImdbId == null || !apiClient.isConfigured) {
            return emptyList()
        }

        return castCache.getOrPut(normalizedImdbId) {
            withContext(workerDispatcher) {
                loadCast(normalizedImdbId)
            }
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

    private suspend fun loadImageConfiguration(): TmdbImageConfiguration =
        configurationCache.getOrPut(Unit) {
            apiClient.getConfiguration()
                .getOrNull()
                ?.images
                ?: TmdbImageConfiguration()
        }

    private fun List<TmdbCastCredit>.mapToCastMembers(
        imageConfiguration: TmdbImageConfiguration,
    ): List<TmdbCastMember> =
        mapNotNull { credit ->
            credit.name?.trim()?.takeIf(String::isNotEmpty)?.let { name ->
                TmdbCastMember(
                    name = name,
                    profileUrl = TmdbImageConfig.resolveProfileUrl(
                        secureBaseUrl = imageConfiguration.secureBaseUrl,
                        profileSizes = imageConfiguration.profileSizes,
                        profilePath = credit.profilePath,
                    ),
                )
            }
        }

    private companion object {
        val CAST_CACHE_TTL = 30.minutes
        val CONFIGURATION_CACHE_TTL = 30.minutes
    }
}
