package com.kino.puber.ui.feature.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.kino.puber.core.di.DIScope
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.domain.interactor.search.SearchInteractor
import com.kino.puber.ui.feature.search.content.SearchScreenContent
import com.kino.puber.ui.feature.search.vm.SearchVM
import kotlinx.parcelize.Parcelize
import com.kino.puber.core.di.puberViewModel
import org.koin.core.module.dsl.scopedOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeID
import org.koin.dsl.module

@Parcelize
internal class SearchScreen : PuberScreen {

    @Suppress("unused")
    private fun buildModule(scopeId: ScopeID, parentScope: Scope) = module {
        scope(named(scopeId)) {
            scopedOf(::SearchInteractor)
            scoped { VideoItemUIMapper(get()) }
            viewModelOf(::SearchVM)
        }
    }

    @Composable
    override fun Content() = DIScope(scopeName = key, moduleFactory = ::buildModule) {
        val vm = puberViewModel<SearchVM>()
        val state by vm.collectViewState()
        val onAction: (UIAction) -> Unit = remember(vm) { vm::onAction }
        SearchScreenContent(
            state = state,
            onAction = onAction,
        )
    }
}
