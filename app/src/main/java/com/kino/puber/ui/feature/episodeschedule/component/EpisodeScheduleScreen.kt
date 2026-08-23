package com.kino.puber.ui.feature.episodeschedule.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import cafe.adriel.voyager.core.screen.ScreenKey
import com.kino.puber.core.di.DIScope
import com.kino.puber.core.di.puberViewModel
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.domain.interactor.schedule.EpisodeScheduleInteractor
import com.kino.puber.ui.feature.episodeschedule.model.EpisodeScheduleScreenParams
import com.kino.puber.ui.feature.episodeschedule.model.EpisodeScheduleUIMapper
import com.kino.puber.ui.feature.episodeschedule.vm.EpisodeScheduleVM
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.koin.core.module.dsl.scopedOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeID
import org.koin.dsl.module

@Parcelize
internal class EpisodeScheduleScreen(
    private val params: EpisodeScheduleScreenParams,
) : PuberScreen {

    @IgnoredOnParcel
    override val key: ScreenKey = "EpisodeScheduleScreen_${params.itemId}"

    @Suppress("unused")
    private fun buildModule(scopeId: ScopeID, parentScope: Scope) = module {
        scope(named(scopeId)) {
            scoped { params }
            scopedOf(::EpisodeScheduleUIMapper)
            scopedOf(::EpisodeScheduleInteractor)
            viewModelOf(::EpisodeScheduleVM)
        }
    }

    @Composable
    override fun Content() = DIScope(scopeName = key, moduleFactory = ::buildModule) {
        val vm = puberViewModel<EpisodeScheduleVM>()
        val state by vm.collectViewState()
        val onAction = remember(vm) { vm::onAction }
        EpisodeScheduleScreenContent(
            state = state,
            onAction = onAction,
        )
    }
}
