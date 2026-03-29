package com.kino.puber.ui.feature.collections.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.kino.puber.core.di.DIScope
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.domain.interactor.collections.CollectionInteractor
import com.kino.puber.ui.feature.collections.vm.CollectionsVM
import kotlinx.parcelize.Parcelize
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.dsl.scopedOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeID
import org.koin.dsl.module

@Parcelize
internal class CollectionsScreen : PuberScreen {

    @Suppress("unused")
    private fun buildModule(scopeId: ScopeID, parentScope: Scope) = module {
        scope(named(scopeId)) {
            scopedOf(::CollectionInteractor)
            viewModelOf(::CollectionsVM)
        }
    }

    @Composable
    override fun Content() = DIScope(scopeName = key, moduleFactory = ::buildModule) {
        val vm = koinViewModel<CollectionsVM>()
        val state by vm.collectViewState()
        CollectionsScreenContent(
            state = state,
            onCollectionClick = vm::onCollectionClick,
            onLoadMore = vm::onLoadMore,
        )
    }
}
