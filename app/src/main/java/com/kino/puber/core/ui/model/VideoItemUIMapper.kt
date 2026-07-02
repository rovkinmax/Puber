package com.kino.puber.core.ui.model

import com.kino.puber.R
import com.kino.puber.core.system.ResourceProvider
import com.kino.puber.core.ui.uikit.component.RatingUIState
import com.kino.puber.core.ui.uikit.component.details.VideoDetailsUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.data.api.models.History
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.isSeriesLike
import com.kino.puber.data.repository.PlayerPreferencesRepository

class VideoItemUIMapper(
    private val resources: ResourceProvider,
    private val playerPreferencesRepository: PlayerPreferencesRepository? = null,
    private val posterUrlMapper: PosterUrlMapper? = null,
) {

    fun mapShortItemList(items: List<Item>): List<VideoItemUIState> {
        return items.map { mapShortItem(it) }
    }

    fun mapShortItem(item: Item): VideoItemUIState {
        val mediumPosterUrls = mapPosterUrls(item.posters?.medium)
        val bigPosterUrls = mapPosterUrls(item.posters?.big)
        val widePosterUrls = mapPosterUrls(item.posters?.wide)
        return VideoItemUIState(
            id = item.id,
            title = item.title,
            imageUrl = mediumPosterUrls.firstOrEmpty(),
            bigImageUrl = bigPosterUrls.firstOrEmpty(),
            wideImageUrl = widePosterUrls.firstOrEmpty(),
            imageFallbackUrls = (mediumPosterUrls.drop(1) + bigPosterUrls.drop(1) + widePosterUrls.drop(1))
                .distinct(),
            unwatchedCount = item.new,
            ratings = buildRatings(item),
            isWatched = isItemWatched(item),
            showWatchedIndicator = watchedIndicatorsEnabled(),
            isSeriesLike = item.type.isSeriesLike(),
        )
    }

    fun mapHistoryItem(history: History): VideoItemUIState {
        val progress = history.video?.watching?.let { w ->
            if (w.duration > 0) w.time.toFloat() / w.duration.toFloat() else null
        }
        return mapShortItem(history.item).copy(progressPercent = progress)
    }

    fun mapDetailedItem(item: Item): VideoDetailsUIState {
        val widePosterUrls = mapPosterUrls(item.posters?.wide)
        val bigPosterUrls = mapPosterUrls(item.posters?.big)
        return VideoDetailsUIState(
            id = item.id,
            title = item.title.formatTitle(),
            description = item.plot.orEmpty(),
            imageUrl = widePosterUrls.firstOrEmpty(),
            imageFallbackUrls = (widePosterUrls.drop(1) + bigPosterUrls).distinct(),
            trailerUrl = item.trailer?.url.orEmpty(),
            ratings = buildRatings(item),
            genres = item.genres.orEmpty().joinToString(", ") { it.title },
            country = item.countries.orEmpty().joinToString(", ") { it.title },
            year = item.year?.toString().orEmpty(),
            duration = buildDuration(item),
        )
    }

    fun isItemWatched(item: Item): Boolean {
        val watched = item.watched ?: return false
        if (watched == 0) return false
        // For series: watched when no new (unwatched) episodes remain
        val newEpisodes = item.new
        return newEpisodes == null || newEpisodes == 0
    }

    fun buildRatings(item: Item): List<RatingUIState> = buildList {
        if (item.kinopoiskRating.isValidRating()) {
            add(RatingUIState.KP(item.kinopoiskRating!!))
        }

        if (item.imdbRating.isValidRating()) {
            add(RatingUIState.IMDB(item.imdbRating!!))
        }

        item.ratingPercentage?.takeIf { it > 0 }?.let { rating ->
            add(RatingUIState.PUB((rating.toFloat() / 10).toString()))
        }
    }

    private fun String?.isValidRating(): Boolean {
        if (this == null) return false
        val value = toFloatOrNull() ?: return false
        return value > 0f
    }

    fun buildDuration(item: Item): String {
        return item.seasons?.let { seasons ->
            resources.getString(R.string.video_details_label_seasons, seasons.size)
        } ?: resources.getString(
            R.string.video_details_label_duration,
            item.duration?.total?.formatDurationWithResources().orEmpty(),
        )
    }

    fun Int.formatDurationWithResources(): String {
        val days = this / 86400
        val hours = (this % 86400) / 3600
        val minutes = (this % 3600) / 60

        return when {
            days > 0 && hours > 0 && minutes > 0 -> resources.getString(
                R.string.duration_days_hours_minutes, days, hours, minutes
            )

            days > 0 && hours > 0 -> resources.getString(
                R.string.duration_days_hours, days, hours
            )

            days > 0 && minutes > 0 -> resources.getString(
                R.string.duration_days_minutes, days, minutes
            )

            days > 0 -> resources.getString(R.string.duration_days_only, days)
            hours > 0 && minutes > 0 -> resources.getString(
                R.string.duration_hours_minutes, hours, minutes
            )

            hours > 0 -> resources.getString(R.string.duration_hours_only, hours)
            minutes > 0 -> resources.getString(R.string.duration_minutes_only, minutes)
            else -> resources.getString(R.string.duration_zero)
        }
    }

    private fun String.formatTitle(): String {
        return split("/").joinToString(separator = "\n") { it.trim() }
    }

    fun mapPosterUrl(url: String?): String = mapPosterUrls(url).firstOrEmpty()

    fun mapPosterUrls(url: String?): List<String> {
        return posterUrlMapper?.mapWithFallback(url) ?: url.orEmpty().ensureHttps().takeIf { it.isNotBlank() }
            ?.let(::listOf)
            .orEmpty()
    }

    fun watchedIndicatorsEnabled(): Boolean {
        return playerPreferencesRepository?.watchedIndicatorsEnabled ?: true
    }
}

private fun List<String>.firstOrEmpty(): String = firstOrNull().orEmpty()

private fun String.ensureHttps(): String {
    return if (startsWith("http://")) replaceFirst("http://", "https://") else this
}
