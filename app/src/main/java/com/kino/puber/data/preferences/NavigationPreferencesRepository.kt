package com.kino.puber.data.preferences

import android.content.Context
import com.kino.puber.core.model.NavigationMode
import com.kino.puber.ui.feature.main.model.TabType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ContentPreferences(
    val showCartoonsTab: Boolean,
    val showAnimeTab: Boolean,
    val showAnime: Boolean,
)

class NavigationPreferencesRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _contentPreferences = MutableStateFlow(
        ContentPreferences(
            showCartoonsTab = prefs.getBoolean(KEY_SHOW_CARTOONS_TAB, false),
            showAnimeTab = prefs.getBoolean(KEY_SHOW_ANIME_TAB, false),
            showAnime = prefs.getBoolean(KEY_SHOW_ANIME, true),
        )
    )
    val contentPreferences: StateFlow<ContentPreferences> = _contentPreferences.asStateFlow()

    fun getNavigationMode(): NavigationMode {
        val name = prefs.getString(KEY_NAVIGATION_MODE, NavigationMode.TopTabs.name)
        return NavigationMode.entries.find { it.name == name } ?: NavigationMode.TopTabs
    }

    fun setNavigationMode(mode: NavigationMode) {
        prefs.edit().putString(KEY_NAVIGATION_MODE, mode.name).apply()
    }

    fun getVisibleTabs(mode: NavigationMode): List<TabType> {
        if (mode == NavigationMode.TopTabs) {
            migrateTopTabsIfNeeded()
        }
        val key = tabsKeyForMode(mode)
        val stored = prefs.getString(key, null)
        val baseTabs = stored?.let(::deserializeTabs) ?: defaultTabsForMode(mode)
        return insertOptionalTabs(baseTabs)
    }

    private fun migrateTopTabsIfNeeded() {
        val currentVersion = prefs.getInt(KEY_TOP_TABS_SCHEMA_VERSION, 0)
        if (currentVersion >= TOP_TABS_SCHEMA_VERSION_HISTORY) return

        val stored = prefs.getString(KEY_TOP_TABS, null)
        val currentTabs = stored
            ?.let(::deserializeTabs)
            ?: resolveTabNames(TOP_TABS_DEFAULT_TAB_NAMES)
        val normalizedTabs = normalizeTopTabsForHistory(currentTabs)
        val editor = prefs.edit()
        editor.putString(KEY_TOP_TABS, serializeTabs(normalizedTabs))
        editor.putInt(KEY_TOP_TABS_SCHEMA_VERSION, TOP_TABS_SCHEMA_VERSION_HISTORY)
        editor.apply()
    }

    private fun normalizeTopTabsForHistory(tabs: List<TabType>): List<TabType> {
        val normalized = tabs
            .filterNot { it == TabType.Search || it == TabType.Settings || it == TabType.History }
            .toMutableList()
        if (TabType.Home !in normalized) {
            normalized.add(index = 0, element = TabType.Home)
        }
        val collectionsIndex = normalized.indexOf(TabType.Collections)
        val historyIndex = if (collectionsIndex >= 0) collectionsIndex + 1 else normalized.size
        normalized.add(index = historyIndex, element = TabType.History)
        return normalized
    }

    fun setVisibleTabs(mode: NavigationMode, tabs: List<TabType>) {
        val key = tabsKeyForMode(mode)
        val withSettings = ensureRequiredTabs(mode, tabs).filterNot { it.isOptionalContentTab() }
        prefs.edit().putString(key, serializeTabs(withSettings)).apply()
    }

    fun setShowCartoonsTab(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_CARTOONS_TAB, show).apply()
        _contentPreferences.update { it.copy(showCartoonsTab = show) }
    }

    fun setShowAnimeTab(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_ANIME_TAB, show).apply()
        _contentPreferences.update { it.copy(showAnimeTab = show) }
    }

    fun setShowAnime(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_ANIME, show).apply()
        _contentPreferences.update { it.copy(showAnime = show) }
    }

    private fun defaultTabsForMode(mode: NavigationMode): List<TabType> {
        return when (mode) {
            NavigationMode.SideDrawer -> TabType.entries.filter(TabType::enabled)
            NavigationMode.TopTabs -> resolveTabNames(TOP_TABS_DEFAULT_TAB_NAMES)
        }
    }

    private fun resolveTabNames(names: List<String>): List<TabType> {
        return names.mapNotNull { name ->
            TabType.entries.find { it.name == name }
        }
    }

    private fun ensureRequiredTabs(mode: NavigationMode, tabs: List<TabType>): List<TabType> {
        val result = tabs.toMutableList()
        if (mode == NavigationMode.TopTabs) {
            result.removeAll { it == TabType.Search || it == TabType.Settings }
            if (TabType.Home !in result) {
                result.add(0, TabType.Home)
            }
        } else {
            if (TabType.Settings !in result) {
                result.add(TabType.Settings)
            }
        }
        return result
    }

    private fun insertOptionalTabs(tabs: List<TabType>): List<TabType> {
        val normalized = tabs.filterNot { it.isOptionalContentTab() }.toMutableList()
        val preferences = contentPreferences.value
        val optionalTabs = buildList {
            if (preferences.showCartoonsTab) add(TabType.Cartoons)
            if (preferences.showAnimeTab) add(TabType.Anime)
        }
        if (optionalTabs.isEmpty()) return normalized

        val anchorIndex = normalized.indexOf(TabType.Series)
            .takeIf { it >= 0 }
            ?: normalized.indexOf(TabType.Movies).takeIf { it >= 0 }
        val insertionIndex = anchorIndex
            ?.plus(1)
            ?: normalized.indexOfFirst {
                it == TabType.Collections || it == TabType.History || it == TabType.Settings
            }.takeIf { it >= 0 }
            ?: normalized.size
        normalized.addAll(insertionIndex, optionalTabs)
        return normalized
    }

    private fun TabType.isOptionalContentTab(): Boolean {
        return this == TabType.Cartoons || this == TabType.Anime
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
        const val KEY_TOP_TABS_SCHEMA_VERSION = "toptabs_schema_version"
        const val KEY_SHOW_CARTOONS_TAB = "show_cartoons_tab"
        const val KEY_SHOW_ANIME_TAB = "show_anime_tab"
        const val KEY_SHOW_ANIME = "show_anime"
        const val TOP_TABS_SCHEMA_VERSION_HISTORY = 1
        const val SEPARATOR = ","

        val TOP_TABS_DEFAULT_TAB_NAMES = listOf(
            "Home",
            "Movies",
            "Series",
            "Collections",
            "History",
        )
    }
}
