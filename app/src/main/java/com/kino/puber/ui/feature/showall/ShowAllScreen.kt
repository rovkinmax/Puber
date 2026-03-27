package com.kino.puber.ui.feature.showall

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import cafe.adriel.voyager.core.screen.ScreenKey
import com.kino.puber.core.di.DIScope
import com.kino.puber.core.paginator.Paginator
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.data.api.models.Item
import com.kino.puber.domain.interactor.contentlist.ContentListInteractor
import com.kino.puber.ui.feature.contentlist.model.SectionConfig
import com.kino.puber.ui.feature.showall.content.ShowAllScreenContent
import com.kino.puber.ui.feature.showall.vm.ShowAllVM
import kotlinx.parcelize.Parcelize
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.dsl.scopedOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeID
import org.koin.dsl.module

@Parcelize
internal class ShowAllScreen(
    private val config: SectionConfig,
) : PuberScreen {

    override val key: ScreenKey = "ShowAllScreen_${config.id}"

    @Suppress("unused")
    private fun buildModule(scopeId: ScopeID, parentScope: Scope) = module {
        scope(named(scopeId)) {
            scopedOf(::ContentListInteractor)
            scoped { VideoItemUIMapper(get()) }
            viewModel {
                ShowAllVM(
                    paginator = Paginator.Store { old, new -> old.id == new.id },
                    config = config,
                    interactor = get(),
                    mapper = get(),
                    router = get(),
                    errorHandler = get(),
                )
            }
        }
    }

    @Composable
    override fun Content() = DIScope(scopeName = key, moduleFactory = ::buildModule) {
        val vm = koinViewModel<ShowAllVM>()
        val state by vm.collectViewState()
        val onAction: (UIAction) -> Unit = remember(vm) { vm::onAction }
        ShowAllScreenContent(
            state = state,
            onAction = onAction,
        )
    }
}
