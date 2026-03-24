package com.kino.puber.ui.feature.details.model

import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Duotone
import com.adamglin.phosphoricons.duotone.BookmarkSimple
import com.adamglin.phosphoricons.duotone.FilmSlate
import com.adamglin.phosphoricons.duotone.Play
import com.adamglin.phosphoricons.duotone.Playlist
import com.adamglin.phosphoricons.duotone.VideoCamera
import com.kino.puber.R
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType

internal class DetailsScreenUIMapper(
    private val itemMapper: VideoItemUIMapper,
) {

    fun map(item: Item): DetailsScreenState.Content {
        return DetailsScreenState.Content(
            details = itemMapper.mapDetailedItem(item),
            buttons = buildButtons(item),
            isInWatchlist = item.inWatchlist ?: false,
        )
    }

    private fun buildButtons(item: Item): List<DetailsButtonUIState> = buildList {
        if (item.type.isSeriesLike()) {
            add(
                DetailsButtonUIState.TextButton(
                    textRes = R.string.video_details_button_watch_series,
                    icon = PhosphorIcons.Duotone.Play,
                    action = DetailsAction.PlayClicked,
                )
            )
            add(
                DetailsButtonUIState.TextButton(
                    textRes = R.string.video_details_button_select_season,
                    icon = PhosphorIcons.Duotone.Playlist,
                    action = DetailsAction.SelectSeasonClicked,
                )
            )
            if (item.trailer != null) {
                add(
                    DetailsButtonUIState.IconOnly(
                        icon = PhosphorIcons.Duotone.VideoCamera,
                        contentDescription = R.string.video_details_button_trailer,
                        action = DetailsAction.TrailerClicked,
                    )
                )
            }
        } else {
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

        val isInWatchlist = item.inWatchlist ?: false
        add(
            DetailsButtonUIState.IconOnly(
                icon = PhosphorIcons.Duotone.BookmarkSimple,
                contentDescription = if (item.type.isSeriesLike()) {
                    R.string.video_details_button_add_to_watchlist
                } else {
                    R.string.video_details_button_add_to_bookmarks
                },
                action = DetailsAction.WatchlistToggleClicked,
                isActive = isInWatchlist,
            )
        )
    }

    private fun ItemType.isSeriesLike(): Boolean = when (this) {
        ItemType.SERIAL, ItemType.DOCU_SERIAL, ItemType.TV_SHOW -> true
        else -> false
    }
}
