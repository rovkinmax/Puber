package com.kino.puber.ui.feature.main.vm

import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.TabRouter
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.ui.feature.main.model.MainTab
import com.kino.puber.ui.feature.main.model.MainUIMapper
import com.kino.puber.ui.feature.main.model.MainViewState
import com.kino.puber.ui.feature.main.model.TabType
import kotlinx.coroutines.delay

internal class MainVM(
    router: AppRouter,
    private val mainUIMapper: MainUIMapper,
    private val tabRouter: TabRouter,
    private val api: KinoPubApiClient,
) : PuberVM<MainViewState>(router) {
    override val initialViewState = MainViewState()

    override fun onStart() {
        updateViewState(mainUIMapper.buildViewState())
        tabRouter.openTab(mainUIMapper.buildTabContent(getStartTab()))
        launch {
            delay(4000)
            api.getItems()
        }
    }

    private fun getStartTab(): TabType {
        return TabType.Favourites
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