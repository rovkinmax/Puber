package com.kino.puber.ui.feature.device.speedtest

import android.net.ConnectivityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.kino.puber.core.di.DIScope
import com.kino.puber.core.di.puberViewModel
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.system.ConnectionTransportProvider
import com.kino.puber.domain.interactor.speedtest.SpeedTestInteractor
import com.kino.puber.ui.feature.device.speedtest.vm.SpeedTestVM
import kotlinx.parcelize.Parcelize
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeID
import org.koin.dsl.module

@Parcelize
internal class SpeedTestScreen : PuberScreen {

    @Suppress("unused")
    private fun buildModule(scopeId: ScopeID, parentScope: Scope) = module {
        scope(named(scopeId)) {
            scoped {
                ConnectionTransportProvider(
                    androidContext().getSystemService(ConnectivityManager::class.java),
                )
            }
            scoped {
                SpeedTestInteractor(
                    apiClient = get(),
                    deviceSettingInteractor = get(),
                )
            }
            viewModelOf(::SpeedTestVM)
        }
    }

    @Composable
    override fun Content() = DIScope(scopeName = key, moduleFactory = ::buildModule) {
        val vm = puberViewModel<SpeedTestVM>()
        val state by vm.collectViewState()
        val onAction = remember(vm) { vm::onAction }
        SpeedTestContent(
            state = state,
            onAction = onAction,
        )
    }
}
