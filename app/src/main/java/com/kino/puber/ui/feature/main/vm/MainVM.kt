package com.kino.puber.ui.feature.main.vm

import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.TabRouter
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.core.model.NavigationMode
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

    override fun onStart() {
        val state = mainUIMapper.buildViewState()
        updateViewState(state)
        val startTab = if (state.navigationMode == NavigationMode.TopTabs) {
            TabType.Home
        } else {
            TabType.Favourites
        }
        tabRouter.openTab(mainUIMapper.buildTabContent(startTab))
    }

    override fun onAction(action: UIAction) {
        when (action) {
            is CommonAction.ItemSelected<*> -> onTabSelected(action.item as MainTab)
            else -> super.onAction(action)
        }
    }

    private fun onTabSelected(item: MainTab) {
        updateViewState<MainViewState> {
            mainUIMapper.updateSelectedTab(state = this, item)
        }
        tabRouter.openTab(mainUIMapper.buildTabContent(item.type))
    }

}