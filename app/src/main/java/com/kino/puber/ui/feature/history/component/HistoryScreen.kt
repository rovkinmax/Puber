package com.kino.puber.ui.feature.history.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import cafe.adriel.voyager.core.screen.ScreenKey
import com.kino.puber.core.di.DIScope
import com.kino.puber.core.di.puberViewModel
import com.kino.puber.core.paginator.Paginator
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.uikit.component.LifecycleAction
import com.kino.puber.core.ui.uikit.component.ScaffoldMessage
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.data.api.models.History
import com.kino.puber.domain.interactor.history.HistoryInteractor
import com.kino.puber.ui.feature.history.model.HistoryUIMapper
import com.kino.puber.ui.feature.history.model.HistoryPresentation
import com.kino.puber.ui.feature.history.vm.HistoryRowComparator
import com.kino.puber.ui.feature.history.vm.HistoryVM
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.koin.core.module.dsl.scopedOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeID
import org.koin.dsl.module

@Parcelize
internal class HistoryScreen(
    internal val presentation: HistoryPresentation,
) : PuberScreen {

    @IgnoredOnParcel
    override val key: ScreenKey = when (presentation) {
        HistoryPresentation.TopTabs -> "HistoryScreen_TopTabs"
        HistoryPresentation.SideDrawer -> "HistoryScreen_SideDrawer"
    }

    @Suppress("unused")
    private fun buildModule(scopeId: ScopeID, parentScope: Scope) = module {
        scope(named(scopeId)) {
            scopedOf(::HistoryInteractor)
            scoped { VideoItemUIMapper(get(), get()) }
            scopedOf(::HistoryUIMapper)
            scoped { Paginator.Store<History>(comparator = HistoryRowComparator) }
            viewModelOf(::HistoryVM)
        }
    }

    @Composable
    override fun Content() = DIScope(scopeName = key, moduleFactory = ::buildModule) {
        val vm = puberViewModel<HistoryVM>()
        val state by vm.collectViewState()
        val onAction: (UIAction) -> Unit = remember(vm) { vm::onAction }

        LifecycleAction(
            event = Lifecycle.Event.ON_RESUME,
            onAction = onAction,
            action = CommonAction.OnResume,
        )

        HistoryScreenContent(
            state = state,
            presentation = presentation,
            onAction = onAction,
        )

        val message by vm.collectMessage()
        ScaffoldMessage(
            message = message,
            onAction = onAction,
        )
    }
}
