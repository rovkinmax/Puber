package com.kino.puber.domain.interactor.player

import com.kino.puber.core.logger.log
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.SkipSegment
import com.kino.puber.data.api.models.SkipSegmentType
import com.kino.puber.data.repository.PlayerPreferencesRepository
import com.kino.puber.data.repository.SkipSegmentService

class SkipSegmentInteractor(
    private val service: SkipSegmentService,
    private val preferences: PlayerPreferencesRepository,
) {

    suspend fun loadSegments(item: Item, season: Int?, episode: Int?): List<SkipSegment> {
        val imdbId = item.imdb
        if (imdbId == null) {
            log("SkipSegments: item.imdb is null for '${item.title}', skipping")
            return emptyList()
        }
        log("SkipSegments: loading for imdb=$imdbId, s=$season, e=$episode")
        val result = service.getSegments(imdbId, season, episode)
        log("SkipSegments: loaded ${result.size} segments: ${result.map { "${it.type}(${it.startMs}-${it.endMs})" }}")
        return result
    }

    fun findActiveSegment(segments: List<SkipSegment>, positionMs: Long): SkipSegment? {
        return segments.firstOrNull { segment ->
            isSegmentTypeEnabled(segment.type) &&
                positionMs >= segment.startMs &&
                positionMs <= (segment.endMs ?: Long.MAX_VALUE)
        }
    }

    // No settings check here: credits segment is used for next-episode timing
    // regardless of skip-credits toggle. The skip overlay visibility is controlled
    // by findActiveSegment() which does check isSegmentTypeEnabled().
    fun findCreditsSegment(segments: List<SkipSegment>): SkipSegment? {
        return segments.firstOrNull { it.type == SkipSegmentType.CREDITS }
    }

    fun isSegmentTypeEnabled(type: SkipSegmentType): Boolean {
        return when (type) {
            SkipSegmentType.INTRO -> preferences.skipIntroEnabled
            SkipSegmentType.RECAP -> preferences.skipRecapEnabled
            SkipSegmentType.CREDITS -> preferences.skipCreditsEnabled
            SkipSegmentType.PREVIEW -> preferences.skipIntroEnabled
        }
    }
}
