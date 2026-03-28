package com.kino.puber.ui.feature.player.component

import android.R.id.message
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kino.puber.core.di.DIScope
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.uikit.component.ScaffoldMessage
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.domain.interactor.player.PlayerInteractor
import com.kino.puber.domain.interactor.player.SkipSegmentInteractor
import com.kino.puber.ui.feature.player.model.PlayerScreenParams
import com.kino.puber.ui.feature.player.model.PlayerUIMapper
import com.kino.puber.ui.feature.player.vm.ContentStateFactory
import com.kino.puber.ui.feature.player.vm.PlaybackController
import com.kino.puber.ui.feature.player.vm.PlayerVM
import kotlinx.parcelize.Parcelize
import org.koin.androidx.compose.koinViewModel
import org.koin.core.module.dsl.scopedOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeID
import org.koin.dsl.module

@Parcelize
internal data class PlayerScreen(private val params: PlayerScreenParams) : PuberScreen {

    @Suppress("unused")
    private fun buildModule(scopeId: ScopeID, parentScope: Scope) = module {
        scope(named(scopeId)) {
            scoped { params }
            scopedOf(::PlayerInteractor)
            scopedOf(::SkipSegmentInteractor)
            scopedOf(::PlayerUIMapper)
            scopedOf(::ContentStateFactory)
            scopedOf(::PlaybackController)
            viewModelOf(::PlayerVM)
        }
    }

    @Composable
    override fun Content() = DIScope(scopeName = key, moduleFactory = ::buildModule) {
        val vm = koinViewModel<PlayerVM>()
        val state by vm.collectViewState()
        val onAction: (UIAction) -> Unit = remember(vm) { vm::onAction }

        PlayerScreenContent(
            state = state,
            onAction = onAction,
            exoPlayer = vm::getExoPlayer,
        )

        val message by vm.collectMessage()
        ScaffoldMessage(
            message = message,
            onAction = vm::onAction,
        )
    }
}