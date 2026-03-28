package com.kino.puber.domain.interactor.player

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
        val imdbId = item.imdb ?: return emptyList()
        return service.getSegments(imdbId, season, episode)
    }

    fun findActiveSegment(segments: List<SkipSegment>, positionMs: Long): SkipSegment? {
        return segments.firstOrNull { segment ->
            isSegmentTypeEnabled(segment.type) &&
                positionMs >= segment.startMs &&
                positionMs <= (segment.endMs ?: Long.MAX_VALUE)
        }
    }

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
