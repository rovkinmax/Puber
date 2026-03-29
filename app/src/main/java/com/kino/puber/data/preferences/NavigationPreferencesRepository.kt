package com.kino.puber.data.preferences

import android.content.Context
import com.kino.puber.core.model.NavigationMode
import com.kino.puber.ui.feature.main.model.TabType

class NavigationPreferencesRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getNavigationMode(): NavigationMode {
        val name = prefs.getString(KEY_NAVIGATION_MODE, NavigationMode.SideDrawer.name)
        return NavigationMode.entries.find { it.name == name } ?: NavigationMode.SideDrawer
    }

    fun setNavigationMode(mode: NavigationMode) {
        prefs.edit().putString(KEY_NAVIGATION_MODE, mode.name).apply()
    }

    fun getVisibleTabs(mode: NavigationMode): List<TabType> {
        val key = tabsKeyForMode(mode)
        val stored = prefs.getString(key, null)
        if (stored != null) {
            return deserializeTabs(stored)
        }
        return defaultTabsForMode(mode)
    }

    fun setVisibleTabs(mode: NavigationMode, tabs: List<TabType>) {
        val key = tabsKeyForMode(mode)
        val withSettings = ensureRequiredTabs(mode, tabs)
        prefs.edit().putString(key, serializeTabs(withSettings)).apply()
    }

    private fun defaultTabsForMode(mode: NavigationMode): List<TabType> {
        val names = when (mode) {
            NavigationMode.SideDrawer -> DRAWER_DEFAULT_TAB_NAMES
            NavigationMode.TopTabs -> TOP_TABS_DEFAULT_TAB_NAMES
        }
        return resolveTabNames(names)
    }

    private fun resolveTabNames(names: List<String>): List<TabType> {
        return names.mapNotNull { name ->
            TabType.entries.find { it.name == name }
        }
    }

    private fun ensureRequiredTabs(mode: NavigationMode, tabs: List<TabType>): List<TabType> {
        val result = tabs.toMutableList()
        if (TabType.Settings !in result) {
            result.add(TabType.Settings)
        }
        if (mode == NavigationMode.TopTabs) {
            // Home tab will be enforced once it exists in TabType
            val homeName = "Home"
            val homeTab = TabType.entries.find { it.name == homeName }
            if (homeTab != null && homeTab !in result) {
                result.add(0, homeTab)
            }
        }
        return result
    }

    private fun tabsKeyForMode(mode: NavigationMode): String {
        return when (mode) {
            NavigationMode.SideDrawer -> KEY_DRAWER_TABS
            NavigationMode.TopTabs -> KEY_TOP_TABS
        }
    }

    private fun serializeTabs(tabs: List<TabType>): String {
        return tabs.joinToString(SEPARATOR) { it.name }
    }

    private fun deserializeTabs(value: String): List<TabType> {
        if (value.isBlank()) return emptyList()
        return value.split(SEPARATOR).mapNotNull { name ->
            TabType.entries.find { it.name == name }
        }
    }

    private companion object {
        const val PREFS_NAME = "navigation_preferences"
        const val KEY_NAVIGATION_MODE = "navigation_mode"
        const val KEY_DRAWER_TABS = "drawer_tabs_visible"
        const val KEY_TOP_TABS = "toptabs_tabs_visible"
        const val SEPARATOR = ","

        val DRAWER_DEFAULT_TAB_NAMES = listOf(
            "Search",
            "Favourites",
            "Movies",
            "Series",
            "Cartoons",
            "For4k",
            "Concerts",
            "DocMovies",
            "DocSeries",
            "TvShows",
            "Settings",
        )

        val TOP_TABS_DEFAULT_TAB_NAMES = listOf(
            "Home",
            "Movies",
            "Series",
            "Collections",
            "Search",
            "Settings",
        )
    }
}
