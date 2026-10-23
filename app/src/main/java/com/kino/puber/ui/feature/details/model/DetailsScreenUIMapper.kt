package com.kino.puber.ui.feature.details.model

import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Duotone
import com.adamglin.phosphoricons.duotone.CalendarBlank
import com.adamglin.phosphoricons.duotone.FilmSlate
import com.adamglin.phosphoricons.duotone.Play
import com.adamglin.phosphoricons.duotone.Playlist
import com.adamglin.phosphoricons.duotone.VideoCamera
import com.kino.puber.R
import com.kino.puber.core.model.BookmarkMode
import com.kino.puber.core.system.ResourceProvider
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.uikit.component.moviesList.VideoGridItemUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoGridUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemPresentation
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.data.api.config.TmdbImageConfig
import com.kino.puber.data.api.models.Episode
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.TmdbCastMember
import com.kino.puber.data.api.models.Video
import com.kino.puber.data.api.models.isSeriesLike
import com.kino.puber.data.preferences.BookmarkPreferencesRepository
import com.kino.puber.domain.model.EpisodeSchedule
import com.kino.puber.domain.model.ScheduledEpisode
import java.text.Normalizer
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.datetime.LocalDate

internal class DetailsScreenUIMapper(
    private val resources: ResourceProvider,
    private val itemMapper: VideoItemUIMapper,
    private val bookmarkPreferencesRepository: BookmarkPreferencesRepository,
) {

    fun map(item: Item, isInWatchlist: Boolean = item.inWatchlist ?: false): DetailsScreenState.Content {
        return mapContent(item = item, isInWatchlist = isInWatchlist)
    }

    fun map(
        item: Item,
        isInWatchlist: Boolean,
        schedule: EpisodeSchedule?,
    ): DetailsScreenState.Content {
        return mapContent(
            item = item,
            isInWatchlist = isInWatchlist,
            schedule = schedule,
        )
    }

    fun map(
        item: Item,
        isInWatchlist: Boolean,
        initialEpisode: DetailsEpisodeTarget,
    ): DetailsScreenState.Content {
        return mapContent(
            item = item,
            isInWatchlist = isInWatchlist,
            initialEpisode = initialEpisode,
        )
    }

    fun map(
        item: Item,
        isInWatchlist: Boolean,
        initialEpisode: DetailsEpisodeTarget,
        schedule: EpisodeSchedule?,
    ): DetailsScreenState.Content {
        return mapContent(
            item = item,
            isInWatchlist = isInWatchlist,
            initialEpisode = initialEpisode,
            schedule = schedule,
        )
    }

    private fun mapContent(
        item: Item,
        isInWatchlist: Boolean,
        initialEpisode: DetailsEpisodeTarget? = null,
        schedule: EpisodeSchedule? = null,
    ): DetailsScreenState.Content {
        val episodes = if (item.type.isSeriesLike()) mapEpisodes(item, schedule) else null
        val requestedEpisode = episodes?.findEpisode(initialEpisode)
        val seriesStatus = mapSeriesStatus(item)
        val bookmarkMode = bookmarkPreferencesRepository.mode.value
        val isSeriesLike = item.type.isSeriesLike()
        return DetailsScreenState.Content(
            details = itemMapper.mapDetailedItem(item),
            info = buildInfo(item),
            buttons = buildButtons(item, bookmarkMode),
            isInWatchlist = isInWatchlist,
            isBookmarked = if (isSeriesLike) {
                item.bookmarks.orEmpty().isNotEmpty()
            } else {
                isInWatchlist
            },
            bookmarkMode = bookmarkMode,
            isWatched = itemMapper.isItemWatched(item),
            episodes = episodes,
            currentEpisode = requestedEpisode
                ?: if (item.type.isSeriesLike()) mapCurrentEpisode(item) else null,
            initialEpisodeFocusId = requestedEpisode?.id,
            seriesStatus = seriesStatus,
        )
    }

    fun mapSimilarItems(items: List<Item>): List<VideoItemUIState> {
        return itemMapper.mapShortItemList(items)
            .map { item -> item.copy(showTitle = true) }
    }

    fun enrichCastCards(
        castCards: List<DetailsCastMemberUIState>,
        tmdbCast: List<TmdbCastMember>,
    ): List<DetailsCastMemberUIState> {
        return castCards.map { card ->
            val actorNameKeys = actorNameKeys(card.displayName)
            val matchingCredits = tmdbCast.filter { credit ->
                actorNameKeys.intersect(actorNameKeys(credit.name)).isNotEmpty()
            }
            card.copy(photoUrl = matchingCredits.singleOrNull()?.profileUrl)
        }
    }

    private fun mapEpisodes(item: Item, schedule: EpisodeSchedule?): VideoGridUIState? {
        val kinoPubSeasons = item.seasons
        val scheduledBySeason = schedule?.seasons.orEmpty().associateBy { it.seasonNumber }
        if (kinoPubSeasons == null && scheduledBySeason.isEmpty()) return null
        val kinoPubBySeason = kinoPubSeasons.orEmpty().associateBy { it.number }

        val seasonNumbers = (kinoPubBySeason.keys + scheduledBySeason.keys).distinct().sorted()
        val gridItems = mutableListOf<VideoGridItemUIState>()
        for (seasonNumber in seasonNumbers) {
            val season = kinoPubBySeason[seasonNumber]
            val scheduledSeason = scheduledBySeason[seasonNumber]
            val playableEpisodes = season?.episodes.orEmpty()
            val playableCoordinates = playableEpisodes
                .map { episode -> seasonNumber to episode.number }
                .toSet()
            val scheduledEpisodes = scheduledSeason?.episodes.orEmpty()
                .filterNot { episode ->
                    (episode.seasonNumber to episode.episodeNumber) in playableCoordinates
                }
            val items = (playableEpisodes.map { episode ->
                mapEpisode(seasonNumber, playableEpisodes, episode)
            } + scheduledEpisodes.map(::mapScheduledEpisode))
                .sortedBy { it.episodeNumber ?: Int.MAX_VALUE }
            val visibleItems = if (items.isNotEmpty()) {
                items
            } else {
                scheduledSeason?.announcementDate?.let { announcementDate ->
                    listOf(mapScheduledSeasonAnnouncement(seasonNumber, announcementDate))
                }.orEmpty()
            }
            gridItems.add(
                VideoGridItemUIState.Title(
                    resources.getString(R.string.player_season_episodes_count, seasonNumber, visibleItems.size)
                )
            )
            gridItems.add(
                VideoGridItemUIState.Items(
                    items = visibleItems,
                    rowKey = "season_$seasonNumber",
                )
            )
        }
        return VideoGridUIState(list = gridItems)
    }

    private fun mapScheduledEpisode(episode: ScheduledEpisode): VideoItemUIState {
        val stillUrl = TmdbImageConfig.resolveStillUrl(episode.stillPath)
        val title = buildString {
            append(episode.episodeNumber)
            append(". ")
            append(episode.title ?: resources.getString(R.string.player_episode_untitled))
        }
        return VideoItemUIState(
            id = scheduledItemId(episode.seasonNumber, episode.episodeNumber),
            title = title,
            imageUrl = stillUrl,
            bigImageUrl = stillUrl,
            showTitle = true,
            isWatched = null,
            isSeriesLike = false,
            seasonNumber = episode.seasonNumber,
            episodeNumber = episode.episodeNumber,
            presentation = VideoItemPresentation.Scheduled,
            scheduledReleaseDate = resources.getString(
                R.string.player_scheduled_episode_date,
                episode.airDate.localizedDate(),
            ),
        )
    }

    private fun mapScheduledSeasonAnnouncement(
        seasonNumber: Int,
        announcementDate: LocalDate,
    ): VideoItemUIState {
        return VideoItemUIState(
            id = scheduledItemId(seasonNumber, ANNOUNCEMENT_EPISODE_NUMBER),
            title = resources.getString(
                R.string.player_scheduled_season_date,
                announcementDate.localizedDate(),
            ),
            imageUrl = "",
            bigImageUrl = "",
            showTitle = true,
            isWatched = null,
            isSeriesLike = false,
            seasonNumber = seasonNumber,
            presentation = VideoItemPresentation.Scheduled,
            scheduledReleaseDate = resources.getString(
                R.string.player_scheduled_season_date,
                announcementDate.localizedDate(),
            ),
        )
    }

    private fun LocalDate.localizedDate(): String {
        return java.time.LocalDate.of(year, month.ordinal + 1, day)
            .format(
                DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                    .withLocale(Locale.getDefault()),
            )
    }

    private fun scheduledItemId(seasonNumber: Int, episodeNumber: Int): Int {
        val encoded = seasonNumber.toLong() * SCHEDULED_SEASON_MULTIPLIER + episodeNumber + 1L
        return -encoded.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
    }

    private fun mapCurrentEpisode(item: Item): VideoItemUIState? {
        val (seasonNumber, episodes, episode) = findFirstUnwatchedEpisode(item) ?: return null
        return mapEpisode(seasonNumber, episodes, episode)
    }

    private fun VideoGridUIState.findEpisode(target: DetailsEpisodeTarget?): VideoItemUIState? {
        if (target == null) return null
        return list
            .filterIsInstance<VideoGridItemUIState.Items>()
            .asSequence()
            .flatMap { it.items.asSequence() }
            .firstOrNull { item ->
                item.seasonNumber == target.seasonNumber &&
                    item.episodeNumber == target.episodeNumber
            }
    }

    private fun mapEpisode(
        seasonNumber: Int,
        seasonEpisodes: List<Episode>,
        episode: Episode,
    ): VideoItemUIState {
        val thumbnailUrls = itemMapper.mapPosterUrls(episode.thumbnail)
        val title = buildString {
            append(episode.number)
            append(". ")
            append(episode.title ?: resources.getString(R.string.player_episode_untitled))
        }
        return VideoItemUIState(
            id = episode.id,
            title = title,
            imageUrl = thumbnailUrls.firstOrNull().orEmpty(),
            bigImageUrl = thumbnailUrls.firstOrNull().orEmpty(),
            imageFallbackUrls = thumbnailUrls.drop(1),
            showTitle = true,
            isWatched = episode.watched == 1,
            showWatchedIndicator = itemMapper.watchedIndicatorsEnabled(),
            isSeriesLike = false,
            seasonNumber = seasonNumber,
            episodeNumber = episode.number,
            isSeasonWatched = seasonEpisodes.all { it.watched == 1 },
            progressPercent = episode.watching?.let { watching ->
                if (watching.duration > 0) {
                    watching.time.toFloat() / watching.duration.toFloat()
                } else {
                    null
                }
            },
        )
    }

    private fun buildButtons(
        item: Item,
        bookmarkMode: BookmarkMode,
    ): List<DetailsButtonUIState> {
        val isSeriesLike = item.type.isSeriesLike()
        return if (isSeriesLike) {
            buildSeriesButtons(item)
        } else {
            buildMovieButtons(item)
        } + buildStatusButtons(isSeriesLike, bookmarkMode)
    }

    private fun buildSeriesButtons(item: Item): List<DetailsButtonUIState> = buildList {
        val continueText = findFirstUnwatchedEpisode(item)?.let { (season, _, episode) ->
            resources.getString(R.string.player_season_episode, season, episode.number)
        }
        add(
            DetailsButtonUIState.TextButton(
                textRes = R.string.video_details_button_watch_series,
                icon = PhosphorIcons.Duotone.Play,
                action = DetailsAction.PlayClicked,
                textOverride = continueText,
            )
        )
        add(
            DetailsButtonUIState.TextButton(
                textRes = R.string.video_details_button_select_season,
                icon = PhosphorIcons.Duotone.Playlist,
                action = DetailsAction.SelectSeasonClicked,
            )
        )
        if (item.imdb?.trim()?.isNotBlank() == true) {
            add(
                DetailsButtonUIState.TextButton(
                    textRes = R.string.video_details_button_schedule,
                    icon = PhosphorIcons.Duotone.CalendarBlank,
                    action = DetailsAction.ScheduleClicked,
                )
            )
        }
        if (item.trailer != null) {
            add(
                DetailsButtonUIState.IconOnly(
                    icon = PhosphorIcons.Duotone.VideoCamera,
                    contentDescription = R.string.video_details_button_trailer,
                    action = DetailsAction.TrailerClicked,
                )
            )
        }
    }

    private fun buildMovieButtons(item: Item): List<DetailsButtonUIState> = buildList {
        add(
            DetailsButtonUIState.TextButton(
                textRes = R.string.video_details_button_watch_movie,
                icon = PhosphorIcons.Duotone.Play,
                action = DetailsAction.PlayClicked,
            )
        )
        if (item.trailer != null) {
            add(
                DetailsButtonUIState.TextButton(
                    textRes = R.string.video_details_button_trailer,
                    icon = PhosphorIcons.Duotone.FilmSlate,
                    action = DetailsAction.TrailerClicked,
                )
            )
        }
    }

    private fun buildStatusButtons(
        isSeriesLike: Boolean,
        bookmarkMode: BookmarkMode,
    ): List<DetailsButtonUIState> = buildList {
        if (isSeriesLike) {
            add(
                DetailsButtonUIState.WatchlistToggle(
                    contentDescription = R.string.video_details_button_add_to_watchlist,
                    action = DetailsAction.WatchlistToggleClicked,
                )
            )
        }
        if (!isSeriesLike || bookmarkMode == BookmarkMode.Extended) {
            add(
                DetailsButtonUIState.BookmarkToggle(
                    contentDescription = R.string.video_details_button_add_to_bookmarks,
                    action = DetailsAction.BookmarkToggleClicked,
                )
            )
        }
        if (!isSeriesLike) {
            add(
                DetailsButtonUIState.WatchedToggle(
                    contentDescription = R.string.video_details_button_mark_watched,
                    action = DetailsAction.WatchedToggleClicked,
                )
            )
        }
    }

    private fun findFirstUnwatchedEpisode(item: Item): FirstEpisode? {
        val seasons = item.seasons ?: return null
        for (season in seasons) {
            val episodes = season.episodes ?: continue
            for (episode in episodes) {
                if (episode.watched != 1) {
                    return FirstEpisode(season.number, episodes, episode)
                }
            }
        }
        return null
    }

    private data class FirstEpisode(
        val seasonNumber: Int,
        val episodes: List<Episode>,
        val episode: Episode,
    )

    private fun buildInfo(item: Item): DetailsInfoUIState {
        val details = itemMapper.mapDetailedItem(item)
        val castNames = item.parsedCastNames()
        return DetailsInfoUIState(
            description = item.plot.orEmpty(),
            ratings = details.ratings,
            primaryRows = buildPrimaryRows(item),
            secondaryRows = buildSecondaryRows(item),
            castCards = castNames.map { actor ->
                DetailsCastMemberUIState(
                    actorQuery = actor,
                    displayName = actor,
                )
            },
        )
    }

    private fun buildPrimaryRows(item: Item): List<DetailsInfoRowUIState> = buildList {
        item.originalTitle()?.let { add(row(R.string.video_details_info_original_title, it)) }
        item.year?.let { add(row(R.string.video_details_info_year, it.toString())) }
        item.durationRowValue()?.let { add(row(it.first, it.second)) }
        item.genres.orEmpty()
            .joinToString(", ") { it.title }
            .takeIf { it.isNotBlank() }
            ?.let { add(row(R.string.video_details_info_genres, it)) }
        item.countries.orEmpty()
            .joinToString(", ") { it.title }
            .takeIf { it.isNotBlank() }
            ?.let { add(row(R.string.video_details_info_country, it)) }
        item.ageRating?.takeIf { it.isNotBlank() }?.let { add(row(R.string.video_details_info_age_rating, it)) }
    }

    private fun buildSecondaryRows(item: Item): List<DetailsInfoRowUIState> = buildList {
        item.voice?.takeIf { it.isNotBlank() }?.let { add(row(R.string.video_details_info_translation, it)) }
        item.playbackAudioTrackCount().takeIf { it > 0 }?.let { count ->
            add(row(R.string.video_details_info_audio_tracks, count.toString()))
        }
        item.subtitleCount().takeIf { it > 0 }?.let {
            add(row(R.string.video_details_info_subtitles, it.toString()))
        }
        item.director?.takeIf { it.isNotBlank() }?.let { add(row(R.string.video_details_info_director, it)) }
        item.displayQuality()?.let { add(row(R.string.video_details_info_quality, it)) }
        if (item.ac3 == 1 || item.mediaItemsHaveSurroundSound()) {
            add(row(R.string.video_details_info_sound, resources.getString(R.string.video_details_info_sound_surround)))
        }
        mapSeriesStatus(item)?.let { status ->
            add(row(R.string.video_details_info_status, status))
        }
    }

    private fun mapSeriesStatus(item: Item): String? {
        if (!item.type.isSeriesLike()) return null
        return when (item.finished) {
            true -> resources.getString(R.string.video_details_series_status_finished)
            false -> resources.getString(R.string.video_details_series_status_ongoing)
            null -> null
        }
    }

    private fun row(labelRes: Int, value: String): DetailsInfoRowUIState {
        return DetailsInfoRowUIState(
            label = resources.getString(labelRes),
            value = value,
        )
    }

    private fun Item.originalTitle(): String? {
        return title.substringAfter("/", missingDelimiterValue = "")
            .trim()
            .takeIf { it.isNotBlank() }
    }

    private fun Item.durationRowValue(): Pair<Int, String>? {
        return if (type.isSeriesLike()) {
            seasons?.size?.takeIf { it > 0 }?.let { R.string.video_details_info_seasons to it.toString() }
        } else {
            duration?.total?.let { total ->
                R.string.video_details_info_duration to itemMapper.run { total.formatDurationWithResources() }
            }
        }
    }

    private fun Item.subtitleCount(): Int {
        return videos.orEmpty().sumOf { video -> video.subtitles.orEmpty().size } +
            seasons.orEmpty()
                .flatMap { season -> season.episodes.orEmpty() }
                .sumOf { episode -> episode.subtitles.orEmpty().size }
    }

    private fun Item.playbackAudioTrackCount(): Int {
        return if (type.isSeriesLike()) {
            firstPlayableEpisode()?.audios.orEmpty().size
        } else {
            videos?.firstOrNull()?.audios.orEmpty().size
        }
    }

    private fun Item.firstPlayableEpisode(): Episode? {
        val seasons = seasons.orEmpty()
        for (season in seasons) {
            val firstUnwatched = season.episodes.orEmpty().firstOrNull { episode -> episode.watched != 1 }
            if (firstUnwatched != null) return firstUnwatched
        }
        return seasons.firstOrNull()?.episodes?.firstOrNull()
    }

    private fun Item.displayQuality(): String? {
        return videos.orEmpty()
            .flatMap { video -> video.files.orEmpty() }
            .mapNotNull { file ->
                file.quality
                    ?: file.h?.takeIf { it > 0 }?.let { "${it}p" }
                    ?: file.url?.hls4?.takeIf { it.isNotBlank() }?.let { "4K" }
            }
            .firstOrNull()
    }

    private fun Item.mediaItemsHaveSurroundSound(): Boolean {
        return videos.orEmpty().any { video -> video.hasSurroundSound() } ||
            seasons.orEmpty()
                .flatMap { it.episodes.orEmpty() }
                .any { episode -> episode.hasSurroundSound() }
    }

    private fun Item.parsedCastNames(): List<String> {
        return cast.orEmpty()
            .split(",")
            .map { actor -> actor.trim() }
            .filter { actor -> actor.isNotBlank() }
    }

    private fun normalizeActorName(value: String): String {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .trim()
            .lowercase()
            .replace(Regex("[\\p{Punct}]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun actorNameKeys(value: String): Set<String> {
        val normalized = normalizeActorName(value)
        if (normalized.isEmpty()) return emptySet()
        val romanized = if (normalized.any { character -> character.isCyrillic() }) {
            setOf(
                normalized.transliterateCyrillic(),
                normalized.transliterateJapaneseCyrillic(),
            )
        } else {
            emptySet()
        }
        return (romanized + normalized)
            .flatMap { name -> listOf(name, name.sortedNameTokens()) }
            .filter(String::isNotEmpty)
            .toSet()
    }

    private fun String.sortedNameTokens(): String {
        return split(" ")
            .filter(String::isNotEmpty)
            .sorted()
            .joinToString(" ")
    }

    private fun String.transliterateCyrillic(): String {
        return buildString {
            this@transliterateCyrillic.forEach { character ->
                append(CYRILLIC_TRANSLITERATION[character] ?: character)
            }
        }
    }

    private fun String.transliterateJapaneseCyrillic(): String {
        return JAPANESE_CYRILLIC_SEQUENCES.entries.fold(this) { result, (source, target) ->
            result.replace(source, target)
        }.transliterateCyrillic()
            .replace("v", "w")
    }

    private fun Char.isCyrillic(): Boolean {
        return Character.UnicodeBlock.of(this) == Character.UnicodeBlock.CYRILLIC
    }

    private fun Video.hasSurroundSound(): Boolean {
        return ac3 == 1 || audios.orEmpty().any { audio -> (audio.channels ?: 0) >= SURROUND_CHANNELS }
    }

    private fun Episode.hasSurroundSound(): Boolean {
        return ac3 == 1 || audios.orEmpty().any { audio -> (audio.channels ?: 0) >= SURROUND_CHANNELS }
    }

    private companion object {
        const val SURROUND_CHANNELS = 6
        const val ANNOUNCEMENT_EPISODE_NUMBER = 0
        const val SCHEDULED_SEASON_MULTIPLIER = 1_000_000L
        val CYRILLIC_TRANSLITERATION = mapOf(
            'а' to "a",
            'б' to "b",
            'в' to "v",
            'г' to "g",
            'д' to "d",
            'е' to "e",
            'ё' to "e",
            'ж' to "zh",
            'з' to "z",
            'и' to "i",
            'й' to "y",
            'к' to "k",
            'л' to "l",
            'м' to "m",
            'н' to "n",
            'о' to "o",
            'п' to "p",
            'р' to "r",
            'с' to "s",
            'т' to "t",
            'у' to "u",
            'ф' to "f",
            'х' to "h",
            'ц' to "ts",
            'ч' to "ch",
            'ш' to "sh",
            'щ' to "shch",
            'ъ' to "",
            'ы' to "y",
            'ь' to "",
            'э' to "e",
            'ю' to "yu",
            'я' to "ya",
        )
        val JAPANESE_CYRILLIC_SEQUENCES = linkedMapOf(
            "дз" to "z",
            "си" to "shi",
            "ти" to "chi",
        )
    }
}
