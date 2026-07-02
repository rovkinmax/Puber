package com.kino.puber.ui.feature.main.vm

import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.TabRouter
import com.kino.puber.core.ui.navigation.component.TabAppRouterHolder
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.core.model.NavigationMode
import com.kino.puber.ui.feature.main.model.MainAction
import com.kino.puber.ui.feature.main.model.MainTab
import com.kino.puber.ui.feature.main.model.MainUIMapper
import com.kino.puber.ui.feature.main.model.MainViewState
import com.kino.puber.ui.feature.main.model.TabType

internal class MainVM(
    router: AppRouter,
    private val mainUIMapper: MainUIMapper,
    internal val tabRouter: TabRouter,
) : PuberVM<MainViewState>(router) {
    override val initialViewState = MainViewState()
    internal val tabAppRouterHolder = TabAppRouterHolder(router.screens)
    private val tabRefreshVersions = mutableMapOf<TabType, Int>()
    private val staleTabDisposeVersions = mutableMapOf<TabType, Int>()

    override fun onStart() {
        val state = mainUIMapper.buildViewState()
        updateViewState(state)
        val startTab = if (state.navigationMode == NavigationMode.TopTabs) {
            TabType.Home
        } else {
            TabType.Favourites
        }
        tabRouter.openTab(buildTabContent(startTab))
    }

    override fun onAction(action: UIAction) {
        when (action) {
            is CommonAction.ItemSelected<*> -> onTabSelected(action.item as MainTab)
            is MainAction.RefreshTab -> onTabRefresh(action.tab)
            else -> super.onAction(action)
        }
    }

    private fun onTabSelected(item: MainTab) {
        updateViewState<MainViewState> {
            mainUIMapper.updateSelectedTab(state = this, item)
        }
        tabRouter.openTab(buildTabContent(item.type))
    }

    private fun onTabRefresh(item: MainTab) {
        val staleTab = buildTabContent(item.type)
        tabRefreshVersions[item.type] = (tabRefreshVersions[item.type] ?: 0) + 1
        val refreshedTab = buildTabContent(item.type)
        updateViewState<MainViewState> {
            mainUIMapper.updateSelectedTab(state = this, item)
        }
        tabRouter.openTab(refreshedTab)
        disposeStaleTabAfterRefresh(type = item.type, tab = staleTab)
    }

    private fun buildTabContent(type: TabType) = mainUIMapper.buildTabContent(
        type = type,
        refreshVersion = tabRefreshVersions[type] ?: 0,
    )

    private fun disposeStaleTabAfterRefresh(type: TabType, tab: com.kino.puber.core.ui.navigation.PuberTab) {
        val version = (staleTabDisposeVersions[type] ?: 0) + 1
        staleTabDisposeVersions[type] = version
        launch {
            kotlinx.coroutines.delay(STALE_TAB_DISPOSE_DELAY_MS)
            if (staleTabDisposeVersions[type] == version) {
                tabAppRouterHolder.dispose(tab.key)
            }
        }
    }

    fun onSearchClick() {
        router.navigateTo(router.screens.search())
    }

    fun onSettingsClick() {
        router.navigateTo(router.screens.deviceSettings())
    }

    override fun onCleared() {
        tabAppRouterHolder.dispose()
        super.onCleared()
    }

    private companion object {
        const val STALE_TAB_DISPOSE_DELAY_MS = 500L
    }
}
