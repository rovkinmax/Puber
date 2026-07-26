package com.kino.puber.ui.feature.main.toptabs

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Devices.TV_1080p
import androidx.compose.ui.tooling.preview.Preview
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Duotone
import com.adamglin.phosphoricons.duotone.ClockCounterClockwise
import com.adamglin.phosphoricons.duotone.FilmSlate
import com.adamglin.phosphoricons.duotone.House
import com.adamglin.phosphoricons.duotone.Playlist
import com.adamglin.phosphoricons.duotone.TelevisionSimple
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.ui.feature.main.model.MainTab
import com.kino.puber.ui.feature.main.model.TabType

private val previewTabs = listOf(
    MainTab(type = TabType.Home, label = "Главная", icon = PhosphorIcons.Duotone.House),
    MainTab(type = TabType.Movies, label = "Фильмы", icon = PhosphorIcons.Duotone.FilmSlate),
    MainTab(type = TabType.Series, label = "Сериалы", icon = PhosphorIcons.Duotone.TelevisionSimple),
    MainTab(type = TabType.Collections, label = "Подборки", icon = PhosphorIcons.Duotone.Playlist),
    MainTab(type = TabType.History, label = "История", icon = PhosphorIcons.Duotone.ClockCounterClockwise),
)

@Preview(name = "TopTabBar — Home selected", device = TV_1080p)
@Composable
private fun TopTabBarHomePreview() = PuberTheme {
    val tabFocusRequesters = remember { List(previewTabs.size) { FocusRequester() } }
    TopTabBar(
        tabs = previewTabs,
        selectedIndex = 0,
        tabFocusRequesters = tabFocusRequesters,
        onContentFocusRequested = {},
        onTabFocused = {},
        onTabClick = {},
        onTabContextMenu = {},
        onSearchClick = {},
        onSettingsClick = {},
    )
}

@Preview(name = "TopTabBar — History selected", device = TV_1080p)
@Composable
private fun TopTabBarHistoryPreview() = PuberTheme {
    val tabFocusRequesters = remember { List(previewTabs.size) { FocusRequester() } }
    TopTabBar(
        tabs = previewTabs,
        selectedIndex = previewTabs.lastIndex,
        tabFocusRequesters = tabFocusRequesters,
        onContentFocusRequested = {},
        onTabFocused = {},
        onTabClick = {},
        onTabContextMenu = {},
        onSearchClick = {},
        onSettingsClick = {},
    )
}
