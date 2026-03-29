package com.kino.puber.ui.feature.home.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.kino.puber.core.di.DIScope
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.domain.interactor.home.HomeInteractor
import com.kino.puber.ui.feature.home.model.HomeUIMapper
import com.kino.puber.ui.feature.home.vm.HomeVM
import kotlinx.parcelize.Parcelize
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.dsl.scopedOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeID
import org.koin.dsl.module

@Parcelize
internal class HomeScreen : PuberScreen {

    @Suppress("unused")
    private fun buildModule(scopeId: ScopeID, parentScope: Scope) = module {
        scope(named(scopeId)) {
            scopedOf(::HomeInteractor)
            scoped { VideoItemUIMapper(get()) }
            scoped { HomeUIMapper(get(), get()) }
            viewModelOf(::HomeVM)
        }
    }

    @Composable
    override fun Content() = DIScope(scopeName = key, moduleFactory = ::buildModule) {
        val vm = koinViewModel<HomeVM>()
        val state by vm.collectViewState()
        val onAction = remember(vm) { vm::onAction }
        HomeScreenContent(
            state = state,
            onAction = onAction,
            onHeroClick = vm::onHeroClick,
        )
    }
}
