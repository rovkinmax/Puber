package com.kino.puber.ui.feature.main.model

import androidx.compose.ui.graphics.vector.ImageVector
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Duotone
import com.adamglin.phosphoricons.duotone.BookmarkSimple
import com.adamglin.phosphoricons.duotone.Broadcast
import com.adamglin.phosphoricons.duotone.ClockCounterClockwise
import com.adamglin.phosphoricons.duotone.FilmReel
import com.adamglin.phosphoricons.duotone.FilmSlate
import com.adamglin.phosphoricons.duotone.GearSix
import com.adamglin.phosphoricons.duotone.Ghost
import com.adamglin.phosphoricons.duotone.Heart
import com.adamglin.phosphoricons.duotone.House
import com.adamglin.phosphoricons.duotone.MicrophoneStage
import com.adamglin.phosphoricons.duotone.MonitorPlay
import com.adamglin.phosphoricons.duotone.Playlist
import com.adamglin.phosphoricons.duotone.TelevisionSimple
import com.adamglin.phosphoricons.duotone.MagnifyingGlass
import com.adamglin.phosphoricons.duotone.Trophy
import com.kino.puber.R
import com.kino.puber.core.model.NavigationMode
import com.kino.puber.core.system.ResourceProvider
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.PuberTab
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.data.preferences.NavigationPreferencesRepository

internal class MainUIMapper(
    private val resources: ResourceProvider,
    private val screens: Screens,
    private val navPrefs: NavigationPreferencesRepository,
) {

    fun buildViewState(): MainViewState {
        val mode = navPrefs.getNavigationMode()
        val tabs = navPrefs.getVisibleTabs(mode)
        val defaultSelected = getDefaultSelectedTab(mode)
        return MainViewState(
            navigationMode = mode,
            tabs = tabs.map { type ->
                MainTab(
                    type = type,
                    label = resources.getString(type.title),
                    icon = type.icon,
                    isSelected = type == defaultSelected,
                    badge = if (type == TabType.Favourites) 20 else 0 // TODO добавить счетчик
                )
            },
            selectedTab = defaultSelected,
        )
    }

    private fun getDefaultSelectedTab(mode: NavigationMode): TabType {
        return if (mode == NavigationMode.TopTabs) TabType.Home else TabType.Favourites
    }

    private val TabType.icon: ImageVector
        get() {
            return when (this) {
                TabType.Home -> PhosphorIcons.Duotone.House
                TabType.Search -> PhosphorIcons.Duotone.MagnifyingGlass
                TabType.Favourites -> PhosphorIcons.Duotone.Heart
                TabType.Bookmarks -> PhosphorIcons.Duotone.BookmarkSimple
                TabType.History -> PhosphorIcons.Duotone.ClockCounterClockwise
                TabType.Movies -> PhosphorIcons.Duotone.FilmSlate
                TabType.Series -> PhosphorIcons.Duotone.TelevisionSimple
                TabType.Cartoons -> PhosphorIcons.Duotone.Ghost
                TabType.For4k -> resources.getImageVector(R.drawable.for_4k)
                TabType.Concerts -> PhosphorIcons.Duotone.MicrophoneStage
                TabType.DocMovies -> PhosphorIcons.Duotone.FilmReel
                TabType.DocSeries -> PhosphorIcons.Duotone.MonitorPlay
                TabType.TvShows -> PhosphorIcons.Duotone.Broadcast
                TabType.Collections -> PhosphorIcons.Duotone.Playlist
                TabType.SportTV -> PhosphorIcons.Duotone.Trophy
                TabType.Settings -> PhosphorIcons.Duotone.GearSix
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
            TabType.Home -> screens.home()
            TabType.Search -> screens.search()
            TabType.Favourites -> screens.favorites()
            TabType.Movies,
            TabType.Series,
            TabType.Cartoons,
            TabType.For4k,
            TabType.Concerts,
            TabType.DocMovies,
            TabType.DocSeries,
            TabType.TvShows -> screens.contentList(type)
            TabType.Bookmarks -> screens.bookmarks()
            TabType.History -> screens.underDevelopment()
            TabType.Collections -> screens.collections()
            TabType.SportTV -> screens.underDevelopment()
            TabType.Settings -> screens.deviceSettings()
        }
    }
}
