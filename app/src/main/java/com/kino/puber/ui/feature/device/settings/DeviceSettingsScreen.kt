package com.kino.puber.ui.feature.device.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.kino.puber.core.di.DIScope
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.ui.feature.device.settings.mappers.DeviceUiSettingsMapper
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsActions
import com.kino.puber.ui.feature.device.settings.vm.DeviceSettingsVM
import kotlinx.parcelize.Parcelize
import org.koin.androidx.compose.koinViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeID
import org.koin.dsl.module

@Parcelize
internal class DeviceSettingsScreen : PuberScreen {

    @Suppress("unused")
    private fun buildModule(scopeId: ScopeID, parentScope: Scope) = module {
        scope(named(scopeId)) {
            scoped { DeviceUiSettingsMapper() }
            viewModelOf(::DeviceSettingsVM)
        }
    }

    @Composable
    override fun Content() = DIScope(scopeName = key, moduleFactory = ::buildModule) {
        val viewModel = koinViewModel<DeviceSettingsVM>()
        val state by viewModel.collectViewState()

        DeviceSettingsContent(
            state.state,
            onValueSettingUpdate = { viewModel.onAction(DeviceSettingsActions.ChangeSettingValue(it)) },
            onListSettingUpdate = { viewModel.onAction(DeviceSettingsActions.ChangeSettingList(it)) },
            onRetry = { viewModel.onAction(CommonAction.RetryClicked) },
        )
    }
}



