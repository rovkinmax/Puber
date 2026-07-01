package com.kino.puber.ui.feature.player.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import cafe.adriel.voyager.core.screen.ScreenKey
import com.kino.puber.core.di.DIScope
import com.kino.puber.core.ui.navigation.FullscreenPuberScreen
import com.kino.puber.core.ui.uikit.component.LifecycleAction
import com.kino.puber.core.ui.uikit.component.ScaffoldMessage
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.ui.feature.player.model.PlayerAction
import com.kino.puber.domain.interactor.player.PlayerInteractor
import com.kino.puber.domain.interactor.player.SkipSegmentInteractor
import com.kino.puber.ui.feature.player.model.PlayerScreenParams
import com.kino.puber.ui.feature.player.model.PlayerUIMapper
import com.kino.puber.ui.feature.player.vm.ContentStateFactory
import com.kino.puber.ui.feature.player.vm.PlaybackControl
import com.kino.puber.ui.feature.player.vm.PlaybackController
import com.kino.puber.ui.feature.player.vm.PlayerVM
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import com.kino.puber.core.di.puberViewModel
import org.koin.core.module.dsl.scopedOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeID
import org.koin.dsl.module

@Parcelize
internal data class PlayerScreen(private val params: PlayerScreenParams) : FullscreenPuberScreen {

    @IgnoredOnParcel
    override val key: ScreenKey = buildString {
        append("PlayerScreen_")
        append(params.itemId)
        params.seasonNumber?.let { season -> append("_s").append(season) }
        params.episodeNumber?.let { episode -> append("_e").append(episode) }
    }

    @Suppress("unused")
    private fun buildModule(scopeId: ScopeID, parentScope: Scope) = module {
        scope(named(scopeId)) {
            scoped { params }
            scopedOf(::PlayerInteractor)
            scopedOf(::SkipSegmentInteractor)
            scoped { PlayerUIMapper(get(), get(), get()) }
            scopedOf(::ContentStateFactory)
            scoped<PlaybackControl> { PlaybackController(get(), get(), get()) }
            viewModelOf(::PlayerVM)
        }
    }

    @Composable
    override fun Content() = DIScope(scopeName = key, moduleFactory = ::buildModule) {
        val vm = puberViewModel<PlayerVM>()
        val state by vm.collectViewState()
        val onAction: (UIAction) -> Unit = remember(vm) { vm::onAction }

        LifecycleAction(
            event = Lifecycle.Event.ON_STOP,
            onAction = onAction,
            action = PlayerAction.OnBackground,
        )

        PlayerScreenContent(
            state = state,
            onAction = onAction,
            exoPlayer = vm::getExoPlayer,
        )

        val message by vm.collectMessage()
        ScaffoldMessage(
            message = message,
            onAction = onAction,
        )
    }
}
