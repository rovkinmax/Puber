package com.kino.puber.core.ui.model

import com.kino.puber.R
import com.kino.puber.core.model.BookmarkMode
import com.kino.puber.core.system.ResourceProvider
import com.kino.puber.core.ui.uikit.component.HeroItemState
import com.kino.puber.core.ui.uikit.component.RatingUIState
import com.kino.puber.core.ui.uikit.component.details.VideoDetailsUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.isSeriesLike
import com.kino.puber.data.preferences.BookmarkPreferencesRepository
import com.kino.puber.data.repository.PlayerPreferencesRepository
import com.kino.puber.domain.interactor.bookmarks.BookmarkFolderInteractor

class VideoItemUIMapper(
    private val resources: ResourceProvider,
    private val playerPreferencesRepository: PlayerPreferencesRepository? = null,
    private val bookmarkPreferencesRepository: BookmarkPreferencesRepository? = null,
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
            isSaved = if (item.type.isSeriesLike()) {
                item.inWatchlist == true || item.subscribed == true
            } else {
                item.isInQuickFolder()
            },
            isBookmarked = item.bookmarks.orEmpty().isNotEmpty(),
            bookmarkMode = bookmarkPreferencesRepository?.mode?.value ?: BookmarkMode.Simple,
        )
    }

    /**
     * `isSaved` drives the single save/unsave row of the context menu, and for a movie that row
     * writes to the quick folder only (see `BookmarkFolderInteractor.setQuickSaved`). Reading
     * "bookmarked anywhere" here would label a movie filed in some other folder as saved while
     * unsaving it silently did nothing. Matches `DetailsInteractor.getBookmarkState` in Simple mode.
     */
    private fun Item.isInQuickFolder(): Boolean {
        val folders = bookmarks.orEmpty()
        val configuredId = bookmarkPreferencesRepository?.quickFolderId?.value
        return if (configuredId != null) {
            folders.any { it.id == configuredId }
        } else {
            // The id stays unset until the interactor resolves it against the account's folder
            // list, so fall back to the same legacy title that resolution looks for.
            folders.any { it.title == BookmarkFolderInteractor.LEGACY_QUICK_FOLDER_TITLE }
        }
    }

    fun mapHeroItems(items: List<Item>): List<HeroItemState> {
        return items.map { item ->
            val widePosterUrls = mapPosterUrls(item.posters?.wide)
            val bigPosterUrls = mapPosterUrls(item.posters?.big)
            val heroPosterUrls = (widePosterUrls + bigPosterUrls).distinct()
            HeroItemState(
                id = item.id,
                title = item.title,
                wideImageUrl = heroPosterUrls.firstOrNull().orEmpty(),
                fallbackImageUrl = heroPosterUrls.drop(1).firstOrNull().orEmpty(),
                fallbackImageUrls = heroPosterUrls.drop(2),
                year = item.year?.toString().orEmpty(),
                ratings = buildRatings(item),
                genres = item.genres?.joinToString(", ") { it.title }.orEmpty(),
                country = item.countries?.joinToString(", ") { it.title }.orEmpty(),
                duration = if (item.type.isSeriesLike()) {
                    item.seasons?.size?.let {
                        resources.getString(R.string.video_details_label_seasons, it)
                    }.orEmpty()
                } else {
                    buildMovieDuration(item)
                },
            )
        }
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
        } ?: buildMovieDuration(item)
    }

    private fun buildMovieDuration(item: Item): String {
        return resources.getString(
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
        return url.orEmpty().ensureHttps().takeIf { it.isNotBlank() }
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
