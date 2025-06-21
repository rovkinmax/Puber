package com.kino.puber.core.ui.model

import com.kino.puber.R
import com.kino.puber.core.system.ResourceProvider
import com.kino.puber.core.ui.uikit.component.RatingUIState
import com.kino.puber.core.ui.uikit.component.details.VideoDetailsUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.data.api.models.Item

class VideoItemUIMapper(private val resources: ResourceProvider) {

    fun mapShortItemList(items: List<Item>): List<VideoItemUIState> {
        return items.map { mapShortItem(it) }
    }

    fun mapShortItem(item: Item): VideoItemUIState {
        return VideoItemUIState(
            id = item.id,
            title = item.title,
            imageUrl = item.posters?.medium.orEmpty(),
            bigImageUrl = item.posters?.big.orEmpty(),
        )
    }

    fun mapDetailedItem(item: Item): VideoDetailsUIState {
        return VideoDetailsUIState(
            id = item.id,
            title = item.title.formatTitle(),
            description = item.plot.orEmpty(),
            imageUrl = item.posters?.wide.orEmpty(),
            trailerUrl = item.trailer?.url.orEmpty(),
            ratings = buildRatings(item),
            genres = item.genres.orEmpty().joinToString(", ") { it.title },
            country = item.countries.orEmpty().joinToString(", ") { it.title },
            year = item.year?.toString().orEmpty(),
            duration = buildDuration(item),
        )
    }

    private fun buildRatings(item: Item): List<RatingUIState> = buildList {
        if (item.kinopoiskRating != null) {
            add(RatingUIState.KP(item.kinopoiskRating))
        }

        if (item.imdbRating != null) {
            add(RatingUIState.IMDB(item.imdbRating))
        }

        item.ratingPercentage?.let { rating ->
            add(RatingUIState.PUB((rating.toFloat() / 10).toString()))
        }
    }

    private fun buildDuration(item: Item): String {
        return item.seasons?.let { seasons ->
            resources.getQuantityString(
                R.plurals.video_details_label_season_plurals,
                seasons.size,
                seasons.size
            )
        } ?: item.duration?.total?.formatDurationWithResources().orEmpty()
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
}