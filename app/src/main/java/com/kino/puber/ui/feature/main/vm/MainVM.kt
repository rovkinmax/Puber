package com.kino.puber.ui.feature.main.vm

import com.kino.puber.core.model.BookmarkMode
import com.kino.puber.core.model.NavigationMode
import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.TabRouter
import com.kino.puber.core.ui.navigation.component.TabAppRouterHolder
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.data.preferences.BookmarkPreferencesRepository
import com.kino.puber.data.preferences.ContentPreferences
import com.kino.puber.data.preferences.NavigationPreferencesRepository
import com.kino.puber.ui.feature.main.model.MainAction
import com.kino.puber.ui.feature.main.model.MainTab
import com.kino.puber.ui.feature.main.model.MainUIMapper
import com.kino.puber.ui.feature.main.model.MainViewState
import com.kino.puber.ui.feature.main.model.TabType
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect

internal class MainVM(
    router: AppRouter,
    private val mainUIMapper: MainUIMapper,
    internal val tabRouter: TabRouter,
    private val navigationPreferencesRepository: NavigationPreferencesRepository,
    private val bookmarkPreferencesRepository: BookmarkPreferencesRepository,
) : PuberVM<MainViewState>(router) {
    override val initialViewState = MainViewState()
    internal val tabAppRouterHolder = TabAppRouterHolder(router.screens)
    private val tabRefreshVersions = mutableMapOf<TabType, Int>()
    private var observedContentPreferences: ContentPreferences? = null
    private var observedBookmarkMode: BookmarkMode? = null

    override fun onStart() {
        val bookmarkMode = bookmarkPreferencesRepository.mode.value
        val state = mainUIMapper.buildViewState(bookmarkMode = bookmarkMode)
        observedContentPreferences = navigationPreferencesRepository.contentPreferences.value
        observedBookmarkMode = bookmarkMode
        updateViewState(state)
        tabRouter.openTab(buildTabContent(state.selectedTab, state.navigationMode))
        launch {
            combine(
                navigationPreferencesRepository.contentPreferences,
                bookmarkPreferencesRepository.mode,
                ::MainPreferences,
            ).collect(::onPreferencesChanged)
        }
    }

    override fun onAction(action: UIAction) {
        when (action) {
            is CommonAction.ItemSelected<*> -> onTabSelected(action.item as MainTab)
            is MainAction.RefreshTab -> onTabRefresh(action.tab)
            MainAction.SearchClicked -> navigateToSearch()
            MainAction.SettingsClicked -> onSettingsClick()
            else -> super.onAction(action)
        }
    }

    private fun onTabSelected(item: MainTab) {
        updateViewState<MainViewState> {
            mainUIMapper.updateSelectedTab(state = this, item)
        }
        tabRouter.openTab(buildTabContent(item.type, stateValue.navigationMode))
    }

    private fun onTabRefresh(item: MainTab) {
        tabRefreshVersions[item.type] = (tabRefreshVersions[item.type] ?: 0) + 1
        val refreshedTab = buildTabContent(item.type, stateValue.navigationMode)
        updateViewState<MainViewState> {
            mainUIMapper.updateSelectedTab(state = this, item)
        }
        tabRouter.openTab(refreshedTab)
    }

    private fun buildTabContent(
        type: TabType,
        navigationMode: NavigationMode,
    ) = mainUIMapper.buildTabContent(
        type = type,
        navigationMode = navigationMode,
        refreshVersion = tabRefreshVersions[type] ?: 0,
    )

    private fun onPreferencesChanged(preferences: MainPreferences) {
        val previousPreferences = observedContentPreferences
        val previousBookmarkMode = observedBookmarkMode
        if (
            preferences.content == previousPreferences &&
            preferences.bookmarkMode == previousBookmarkMode
        ) {
            return
        }
        observedContentPreferences = preferences.content
        observedBookmarkMode = preferences.bookmarkMode

        val previousState = stateValue
        val updatedState = mainUIMapper.buildViewState(
            previousSelectedTab = previousState.selectedTab,
            bookmarkMode = preferences.bookmarkMode,
        )
        val showAnimeChanged = previousPreferences?.showAnime != preferences.content.showAnime
        val bookmarkModeChanged = previousBookmarkMode != preferences.bookmarkMode
        if (showAnimeChanged) {
            ANIME_FILTERED_TABS.forEach { tab ->
                tabRefreshVersions[tab] = (tabRefreshVersions[tab] ?: 0) + 1
            }
        }
        if (bookmarkModeChanged) {
            tabRefreshVersions[TabType.Home] = (tabRefreshVersions[TabType.Home] ?: 0) + 1
        }
        updateViewState(updatedState)

        val selectedTabChanged = updatedState.selectedTab != previousState.selectedTab
        val selectedTabNeedsRefresh =
            showAnimeChanged && updatedState.selectedTab in ANIME_FILTERED_TABS ||
                bookmarkModeChanged && updatedState.selectedTab == TabType.Home
        if (selectedTabChanged || selectedTabNeedsRefresh) {
            tabRouter.openTab(buildTabContent(updatedState.selectedTab, updatedState.navigationMode))
        }
    }

    private fun navigateToSearch() {
        router.navigateTo(router.screens.search())
    }

    fun onSettingsClick() = navigateToSettings()

    private fun navigateToSettings() {
        router.navigateTo(router.screens.deviceSettings())
    }

    override fun onCleared() {
        tabAppRouterHolder.dispose()
        super.onCleared()
    }

    private companion object {
        val ANIME_FILTERED_TABS = setOf(
            TabType.Home,
            TabType.Movies,
            TabType.Series,
            TabType.Cartoons,
        )
    }

    private data class MainPreferences(
        val content: ContentPreferences,
        val bookmarkMode: BookmarkMode,
    )
}
