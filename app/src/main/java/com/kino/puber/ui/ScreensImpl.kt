package com.kino.puber.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
import com.kino.puber.R
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.ui.feature.auth.component.AuthScreen
import com.kino.puber.ui.feature.contentlist.ContentListScreen
import com.kino.puber.ui.feature.details.component.DetailsScreen
import com.kino.puber.ui.feature.details.model.DetailsScreenParams
import com.kino.puber.ui.feature.details.model.DetailsEpisodeTarget
import com.kino.puber.ui.feature.device.settings.flow.DeviceSettingsFlowScreen
import com.kino.puber.ui.feature.episodeschedule.component.EpisodeScheduleScreen
import com.kino.puber.ui.feature.episodeschedule.model.EpisodeScheduleScreenParams
import com.kino.puber.ui.feature.player.component.PlayerScreen
import com.kino.puber.ui.feature.player.model.PlayerScreenParams
import com.kino.puber.ui.feature.player.model.PlayerStartMode
import com.kino.puber.ui.feature.favorites.content.FavoritesScreen
import com.kino.puber.ui.feature.main.component.MainScreen
import com.kino.puber.ui.feature.search.SearchScreen
import com.kino.puber.ui.feature.search.model.SearchScreenParams
import com.kino.puber.ui.feature.home.component.HomeScreen
import com.kino.puber.ui.feature.history.component.HistoryScreen
import com.kino.puber.ui.feature.history.model.HistoryPresentation
import com.kino.puber.ui.feature.collections.component.CollectionsScreen
import com.kino.puber.ui.feature.bookmarks.component.BookmarksScreen
import com.kino.puber.ui.feature.bookmarkpicker.component.BookmarkPickerScreen
import com.kino.puber.ui.feature.bookmarkpicker.model.BookmarkPickerParams
import com.kino.puber.ui.feature.main.model.TabType
import kotlinx.parcelize.Parcelize

internal object ScreensImpl : Screens {
    override fun auth(): PuberScreen {
        return AuthScreen()
    }

    override fun main(): PuberScreen {
        return MainScreen()
    }

    override fun search(): PuberScreen {
        return SearchScreen()
    }

    override fun actorItems(actorName: String): PuberScreen {
        return SearchScreen(
            SearchScreenParams(
                mode = SearchScreenParams.SearchMode.Actor(actorName),
            ),
        )
    }

    override fun home(): PuberScreen = HomeScreen()

    override fun history(presentation: HistoryPresentation): PuberScreen {
        return HistoryScreen(presentation)
    }

    override fun collections(): PuberScreen = CollectionsScreen()

    override fun bookmarks(): PuberScreen = BookmarksScreen()

    override fun bookmarkPicker(itemId: Int, resultCode: Int): PuberScreen {
        return BookmarkPickerScreen(
            BookmarkPickerParams(
                itemId = itemId,
                resultCode = resultCode,
            )
        )
    }

    override fun favorites(): PuberScreen {
        return FavoritesScreen()
    }

    override fun deviceSettings(): PuberScreen {
        return DeviceSettingsFlowScreen()
    }

    override fun contentList(tabType: TabType): PuberScreen {
        return ContentListScreen(tabType)
    }

    override fun underDevelopment(): PuberScreen {
        return UnderDevelopment()
    }

    override fun details(itemId: Int): PuberScreen {
        return DetailsScreen(DetailsScreenParams(itemId))
    }

    override fun details(itemId: Int, initialEpisode: DetailsEpisodeTarget): PuberScreen {
        return DetailsScreen(
            DetailsScreenParams(
                itemId = itemId,
                initialEpisode = initialEpisode,
            ),
        )
    }

    override fun episodeSchedule(params: EpisodeScheduleScreenParams): PuberScreen {
        return EpisodeScheduleScreen(params)
    }

    override fun player(
        itemId: Int,
        seasonNumber: Int?,
        episodeNumber: Int?,
        videoNumber: Int?,
        startMode: PlayerStartMode,
    ): PuberScreen {
        return PlayerScreen(
            PlayerScreenParams(
                itemId = itemId,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                videoNumber = videoNumber,
                startMode = startMode,
            ),
        )
    }


    @Parcelize
    private class UnderDevelopment : PuberScreen {
        @Composable
        override fun Content() {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.under_development))
            }

        }
    }
}
