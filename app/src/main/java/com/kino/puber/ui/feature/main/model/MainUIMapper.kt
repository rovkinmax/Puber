package com.kino.puber.ui.feature.main.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.HdrOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicVideo
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoCameraFront
import androidx.compose.ui.graphics.vector.ImageVector
import com.kino.puber.core.system.ResourceProvider
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.PuberTab
import com.kino.puber.ui.feature.favorites.content.FavoritesScreen

internal class MainUIMapper(private val resources: ResourceProvider) {

    fun buildViewState(): MainViewState {
        return MainViewState(
            tabs = TabType.entries.map { type ->
                MainTab(
                    type = type,
                    label = resources.getString(type.title),
                    icon = type.icon,
                    isSelected = type == TabType.Favourites,
                    badge = if (type == TabType.Favourites) 20 else 0 // TODO добавить счетчик
                )
            })
    }

    private val TabType.icon: ImageVector
        get() {
            return when (this) {
                TabType.Favourites -> Icons.Default.Notifications
                TabType.Bookmarks -> Icons.Default.Bookmarks
                TabType.History -> Icons.Default.History
                TabType.Movies -> Icons.Default.Movie
                TabType.Series -> Icons.Default.Tv
                TabType.Cartoons -> Icons.Default.ChildCare
                TabType.Fork -> Icons.Default.HdrOn
                TabType.Concerts -> Icons.Default.MusicVideo
                TabType.DocMovies -> Icons.Default.VideoCameraFront
                TabType.DocSeries -> Icons.Default.VideoCameraFront
                TabType.TvShows -> Icons.Default.LiveTv
                TabType.Collections -> Icons.Default.Collections
                TabType.SportTV -> Icons.Default.SportsSoccer
                TabType.Settings -> Icons.Default.Settings
            }
        }

    fun updateSelectedTab(state: MainViewState, tab: MainTab): MainViewState {
        return state.copy(
            tabs = state.tabs.map { it.copy(isSelected = it.type == tab.type) },
        )
    }

    fun buildTabContent(type: TabType): PuberTab {
        return PuberTab(
            screen = tabScreen(type),
            tag = type,
        )
    }

    private fun tabScreen(type: TabType): PuberScreen {
        // тут будем брать экраны из router.screens.someTabScreen()
        // сейчас сделано для примера
        return when (type) {
            TabType.Favourites -> FavoritesScreen(tab = type)
            TabType.Bookmarks -> FavoritesScreen(tab = type)
            TabType.History -> FavoritesScreen(tab = type)
            TabType.Movies -> FavoritesScreen(tab = type)
            TabType.Series -> FavoritesScreen(tab = type)
            TabType.Cartoons -> FavoritesScreen(tab = type)
            TabType.Fork -> FavoritesScreen(tab = type)
            TabType.Concerts -> FavoritesScreen(tab = type)
            TabType.DocMovies -> FavoritesScreen(tab = type)
            TabType.DocSeries -> FavoritesScreen(tab = type)
            TabType.TvShows -> FavoritesScreen(tab = type)
            TabType.Collections -> FavoritesScreen(tab = type)
            TabType.SportTV -> FavoritesScreen(tab = type)
            TabType.Settings -> FavoritesScreen(tab = type)
        }
    }
}