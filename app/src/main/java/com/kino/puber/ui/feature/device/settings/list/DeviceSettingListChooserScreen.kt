package com.kino.puber.ui.feature.device.settings.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kino.puber.R
import com.kino.puber.core.di.DIScope
import com.kino.puber.core.ui.navigation.PuberScreen
import kotlinx.parcelize.Parcelize
import org.koin.androidx.compose.koinViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeID
import org.koin.dsl.module

@Parcelize
class DeviceSettingListChooserScreen() : PuberScreen {

    @Composable
    override fun Content() = DIScope(scopeName = key, moduleFactory = ::buildModule) {
        val viewModel = koinViewModel<DeviceSettingListChooserVM>()
        val state by viewModel.collectViewState()

        DeviceSettingListChooserContent(
            state = state,
            onOptionSelected = { },
            onRetry = { }
        )
    }

    @Suppress("unused")
    private fun buildModule(scopeId: ScopeID, parentScope: Scope) = module {
        scope(named(scopeId)) {
            viewModelOf(::DeviceSettingListChooserVM)
        }
    }
}

@Composable
private fun DeviceSettingListChooserContent(
    state: DeviceSettingListChooserViewState,
    onOptionSelected: (Int) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (state.state) {
        is DeviceSettingListChooserState.Loading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        is DeviceSettingListChooserState.Error -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = state.state.error,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onRetry) {
                        Text(text = stringResource(R.string.device_settings_retry))
                    }
                }
            }
        }

        is DeviceSettingListChooserState.Success -> {
            Column(
                modifier = modifier.fillMaxSize()
            ) {
                Text(
                    text = "",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.state.options) { item ->
                        Text(item.label)
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionItem(
    option: com.kino.puber.data.api.models.SettingOption,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = option.label,
                style = MaterialTheme.typography.bodyLarge
            )
            RadioButton(
                selected = option.selected == 1,
                onClick = onClick
            )
        }
    }
} 