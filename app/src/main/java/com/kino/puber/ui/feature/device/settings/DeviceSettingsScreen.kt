package com.kino.puber.ui.feature.device.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.kino.puber.core.di.DIScope
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.uikit.component.ApiDomainDialog
import com.kino.puber.core.ui.uikit.component.ScaffoldMessage
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsActions
import com.kino.puber.ui.feature.device.settings.mappers.DeviceUiSettingsMapper
import com.kino.puber.ui.feature.device.settings.vm.DeviceSettingsVM
import kotlinx.parcelize.Parcelize
import com.kino.puber.core.di.puberViewModel
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
        val viewModel = puberViewModel<DeviceSettingsVM>()
        val state by viewModel.collectViewState()
        val message by viewModel.collectMessage()

        Box {
            DeviceSettingsContent(
                state = state.state,
                apiDomain = state.apiDomain,
                onAction = viewModel::onAction,
            )
            ApiDomainDialog(
                state = state.apiDomain.takeIf { state.isApiDomainDialogOpen },
                onSave = { viewModel.onAction(DeviceSettingsActions.SaveApiDomain(it)) },
                onReset = { viewModel.onAction(DeviceSettingsActions.ResetApiDomain) },
                onDetect = { viewModel.onAction(DeviceSettingsActions.DetectApiDomain) },
                onDismiss = { viewModel.onAction(DeviceSettingsActions.CloseApiDomainDialog) },
            )
            ScaffoldMessage(
                message = message,
                onAction = viewModel::onAction,
            )
        }
    }
}
