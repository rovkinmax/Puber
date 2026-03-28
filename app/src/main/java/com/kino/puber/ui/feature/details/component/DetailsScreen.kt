package com.kino.puber.ui.feature.details.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.kino.puber.core.di.DIScope
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.uikit.component.ScaffoldMessage
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.domain.interactor.details.DetailsInteractor
import com.kino.puber.ui.feature.details.model.DetailsScreenParams
import com.kino.puber.ui.feature.details.model.DetailsScreenUIMapper
import com.kino.puber.ui.feature.details.vm.DetailsVM
import kotlinx.parcelize.Parcelize
import org.koin.androidx.compose.koinViewModel
import org.koin.core.module.dsl.scopedOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeID
import org.koin.dsl.module

@Parcelize
internal data class DetailsScreen(private val params: DetailsScreenParams) : PuberScreen {

    @Suppress("unused")
    private fun buildModule(scopeId: ScopeID, parentScope: Scope) = module {
        scope(named(scopeId)) {
            scoped { params }
            scopedOf(::DetailsInteractor)
            scopedOf(::DetailsScreenUIMapper)
            viewModelOf(::DetailsVM)
        }
    }

    @Composable
    override fun Content() = DIScope(scopeName = key, moduleFactory = ::buildModule) {
        val vm = koinViewModel<DetailsVM>()
        val state by vm.collectViewState()
        val onAction: (UIAction) -> Unit = remember(vm) { vm::onAction }

        DetailsScreenContent(state = state, onAction = onAction)

        val message by vm.collectMessage()
        ScaffoldMessage(
            message = message,
            onAction = vm::onAction,
        )
    }
}