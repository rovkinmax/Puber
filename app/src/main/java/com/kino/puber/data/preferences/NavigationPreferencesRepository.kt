package com.kino.puber.data.preferences

import android.content.Context
import android.content.SharedPreferences
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

class NavigationPreferencesRepository private constructor(
    private val prefs: SharedPreferences?,
    private val fixedConfiguration: FixedNavigationConfiguration?,
) {

    constructor(context: Context) : this(
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        fixedConfiguration = null,
    )

    internal constructor(
        navigationMode: NavigationMode,
        visibleTabs: List<TabType>,
        contentPreferences: ContentPreferences,
    ) : this(
        prefs = null,
        fixedConfiguration = FixedNavigationConfiguration(
            navigationMode = navigationMode,
            visibleTabs = visibleTabs.toList(),
            contentPreferences = contentPreferences,
        ),
    )

    private val _contentPreferences = MutableStateFlow(
        fixedConfiguration?.contentPreferences ?: ContentPreferences(
            showCartoonsTab = persistentPreferences.getBoolean(KEY_SHOW_CARTOONS_TAB, false),
            showAnimeTab = persistentPreferences.getBoolean(KEY_SHOW_ANIME_TAB, false),
            showAnime = persistentPreferences.getBoolean(KEY_SHOW_ANIME, true),
        ),
    )
    val contentPreferences: StateFlow<ContentPreferences> = _contentPreferences.asStateFlow()

    fun getNavigationMode(): NavigationMode {
        fixedConfiguration?.let { return it.navigationMode }
        val name = persistentPreferences.getString(
            KEY_NAVIGATION_MODE,
            NavigationMode.TopTabs.name,
        )
        return NavigationMode.entries.find { it.name == name } ?: NavigationMode.TopTabs
    }

    fun setNavigationMode(mode: NavigationMode) {
        if (fixedConfiguration != null) return
        persistentPreferences.edit().putString(KEY_NAVIGATION_MODE, mode.name).apply()
    }

    fun getVisibleTabs(mode: NavigationMode): List<TabType> {
        fixedConfiguration?.let { return it.visibleTabs }
        if (mode == NavigationMode.TopTabs) {
            migrateTopTabsIfNeeded()
        } else {
            migrateSideDrawerIfNeeded()
        }
        val key = tabsKeyForMode(mode)
        val stored = persistentPreferences.getString(key, null)
        val baseTabs = stored?.let(::deserializeTabs) ?: defaultTabsForMode(mode)
        return insertOptionalTabs(baseTabs)
    }

    private fun migrateTopTabsIfNeeded() {
        val currentVersion = persistentPreferences.getInt(KEY_TOP_TABS_SCHEMA_VERSION, 0)
        if (currentVersion >= TOP_TABS_SCHEMA_VERSION_BOOKMARKS) return

        val stored = persistentPreferences.getString(KEY_TOP_TABS, null)
        var currentTabs = stored
            ?.let(::deserializeTabs)
            ?: resolveTabNames(TOP_TABS_DEFAULT_TAB_NAMES)
        if (currentVersion < TOP_TABS_SCHEMA_VERSION_HISTORY) {
            currentTabs = normalizeTopTabsForHistory(currentTabs)
        }
        if (currentVersion < TOP_TABS_SCHEMA_VERSION_BOOKMARKS) {
            currentTabs = insertBookmarksTab(currentTabs)
        }
        val editor = persistentPreferences.edit()
        editor.putString(KEY_TOP_TABS, serializeTabs(currentTabs))
        editor.putInt(KEY_TOP_TABS_SCHEMA_VERSION, TOP_TABS_SCHEMA_VERSION_BOOKMARKS)
        editor.apply()
    }

    private fun migrateSideDrawerIfNeeded() {
        val currentVersion = persistentPreferences.getInt(KEY_DRAWER_SCHEMA_VERSION, 0)
        if (currentVersion >= DRAWER_SCHEMA_VERSION_BOOKMARKS) return

        val stored = persistentPreferences.getString(KEY_DRAWER_TABS, null) ?: return
        val currentTabs = insertBookmarksTab(
            tabs = deserializeTabs(stored),
            fallbackAnchor = TabType.Favourites,
        )
        persistentPreferences.edit()
            .putString(KEY_DRAWER_TABS, serializeTabs(currentTabs))
            .putInt(KEY_DRAWER_SCHEMA_VERSION, DRAWER_SCHEMA_VERSION_BOOKMARKS)
            .apply()
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

    private fun insertBookmarksTab(
        tabs: List<TabType>,
        fallbackAnchor: TabType = TabType.Home,
    ): List<TabType> {
        val normalized = tabs.filterNot { it == TabType.Bookmarks }.toMutableList()
        val anchorIndex = normalized.indexOf(fallbackAnchor)
        normalized.add(index = if (anchorIndex >= 0) anchorIndex + 1 else 0, element = TabType.Bookmarks)
        return normalized
    }

    fun setVisibleTabs(mode: NavigationMode, tabs: List<TabType>) {
        if (fixedConfiguration != null) return
        val key = tabsKeyForMode(mode)
        val withSettings = ensureRequiredTabs(mode, tabs).filterNot { it.isOptionalContentTab() }
        val editor = persistentPreferences.edit().putString(key, serializeTabs(withSettings))
        when (mode) {
            NavigationMode.SideDrawer -> editor.putInt(
                KEY_DRAWER_SCHEMA_VERSION,
                DRAWER_SCHEMA_VERSION_BOOKMARKS,
            )
            NavigationMode.TopTabs -> editor.putInt(
                KEY_TOP_TABS_SCHEMA_VERSION,
                TOP_TABS_SCHEMA_VERSION_BOOKMARKS,
            )
        }
        editor.apply()
    }

    fun setShowCartoonsTab(show: Boolean) {
        if (fixedConfiguration != null) return
        persistentPreferences.edit().putBoolean(KEY_SHOW_CARTOONS_TAB, show).apply()
        _contentPreferences.update { it.copy(showCartoonsTab = show) }
    }

    fun setShowAnimeTab(show: Boolean) {
        if (fixedConfiguration != null) return
        persistentPreferences.edit().putBoolean(KEY_SHOW_ANIME_TAB, show).apply()
        _contentPreferences.update { it.copy(showAnimeTab = show) }
    }

    fun setShowAnime(show: Boolean) {
        if (fixedConfiguration != null) return
        persistentPreferences.edit().putBoolean(KEY_SHOW_ANIME, show).apply()
        _contentPreferences.update { it.copy(showAnime = show) }
    }

    private val persistentPreferences: SharedPreferences
        get() = checkNotNull(prefs) {
            "Persistent navigation preferences are unavailable for a fixed configuration"
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

    private data class FixedNavigationConfiguration(
        val navigationMode: NavigationMode,
        val visibleTabs: List<TabType>,
        val contentPreferences: ContentPreferences,
    )

    private companion object {
        const val PREFS_NAME = "navigation_preferences"
        const val KEY_NAVIGATION_MODE = "navigation_mode"
        const val KEY_DRAWER_TABS = "drawer_tabs_visible"
        const val KEY_DRAWER_SCHEMA_VERSION = "drawer_tabs_schema_version"
        const val KEY_TOP_TABS = "toptabs_tabs_visible"
        const val KEY_TOP_TABS_SCHEMA_VERSION = "toptabs_schema_version"
        const val KEY_SHOW_CARTOONS_TAB = "show_cartoons_tab"
        const val KEY_SHOW_ANIME_TAB = "show_anime_tab"
        const val KEY_SHOW_ANIME = "show_anime"
        const val TOP_TABS_SCHEMA_VERSION_HISTORY = 1
        const val TOP_TABS_SCHEMA_VERSION_BOOKMARKS = 2
        const val DRAWER_SCHEMA_VERSION_BOOKMARKS = 1
        const val SEPARATOR = ","

        val TOP_TABS_DEFAULT_TAB_NAMES = listOf(
            "Home",
            "Bookmarks",
            "Movies",
            "Series",
            "Collections",
            "History",
        )
    }
}
