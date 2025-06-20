package com.kino.puber.ui.feature.main.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Collections
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
import com.kino.puber.R
import com.kino.puber.core.system.ResourceProvider
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.PuberTab
import com.kino.puber.core.ui.navigation.Screens

internal class MainUIMapper(
    private val resources: ResourceProvider,
    private val screens: Screens,
) {

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
                TabType.For4k -> resources.getImageVector(R.drawable.for_4k)
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
            selectedTab = tab.type,
        )
    }

    fun buildTabContent(type: TabType): PuberTab {
        return PuberTab(
            screen = tabScreen(type),
            tag = type,
        )
    }

    private fun tabScreen(type: TabType): PuberScreen {
        return when (type) {
            TabType.Favourites -> screens.favorites()
            TabType.Bookmarks -> screens.underDevelopment()
            TabType.History -> screens.underDevelopment()
            TabType.Movies -> screens.underDevelopment()
            TabType.Series -> screens.underDevelopment()
            TabType.Cartoons -> screens.underDevelopment()
            TabType.For4k -> screens.underDevelopment()
            TabType.Concerts -> screens.underDevelopment()
            TabType.DocMovies -> screens.underDevelopment()
            TabType.DocSeries -> screens.underDevelopment()
            TabType.TvShows -> screens.underDevelopment()
            TabType.Collections -> screens.underDevelopment()
            TabType.SportTV -> screens.underDevelopment()
            TabType.Settings -> screens.deviceSettings()
        }
    }
}