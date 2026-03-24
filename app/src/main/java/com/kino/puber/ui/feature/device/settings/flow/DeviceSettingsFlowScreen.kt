package com.kino.puber.ui.feature.device.settings.flow

import androidx.compose.runtime.Composable
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.component.FlowComponent
import com.kino.puber.ui.feature.device.settings.flow.vm.DeviceSettingsFlowVM
import kotlinx.parcelize.Parcelize
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeID
import org.koin.dsl.module

@Parcelize
internal class DeviceSettingsFlowScreen : PuberScreen {

    @Suppress("unused")
    private fun buildModule(scopeId: ScopeID, parentScope: Scope) = module {
        scope(named(scopeId)) {
            viewModelOf(::DeviceSettingsFlowVM)
        }
    }

    @Composable
    override fun Content() {
        FlowComponent(
            scopeName = key,
            moduleFactory = ::buildModule,
        ) {
            val vm = koinViewModel<DeviceSettingsFlowVM>()
            vm.collectViewState()
        }
    }
}
