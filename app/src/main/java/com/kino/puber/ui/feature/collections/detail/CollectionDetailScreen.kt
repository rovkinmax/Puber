package com.kino.puber.ui.feature.collections.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.kino.puber.core.di.DIScope
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.domain.interactor.collections.CollectionInteractor
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.ui.feature.collections.detail.component.CollectionDetailScreenContent
import com.kino.puber.ui.feature.collections.detail.vm.CollectionDetailVM
import kotlinx.parcelize.Parcelize
import com.kino.puber.core.di.puberViewModel
import org.koin.core.module.dsl.scopedOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeID
import org.koin.dsl.module

@Parcelize
internal class CollectionDetailScreen(
    private val collectionId: Int,
    private val collectionTitle: String,
) : PuberScreen {

    @Suppress("unused")
    private fun buildModule(scopeId: ScopeID, parentScope: Scope) = module {
        scope(named(scopeId)) {
            scopedOf(::CollectionInteractor)
            scoped { VideoItemUIMapper(get()) }
            viewModel {
                CollectionDetailVM(
                    router = get(),
                    collectionId = collectionId,
                    collectionTitle = collectionTitle,
                    interactor = get(),
                    mapper = get(),
                    errorHandler = get(),
                )
            }
        }
    }

    @Composable
    override fun Content() = DIScope(scopeName = key, moduleFactory = ::buildModule) {
        val vm = puberViewModel<CollectionDetailVM>()
        val state by vm.collectViewState()
        val onAction = remember(vm) { vm::onAction }
        CollectionDetailScreenContent(
            state = state,
            onAction = onAction,
            onItemClick = { item -> onAction(CommonAction.ItemSelected(item)) },
        )
    }
}
