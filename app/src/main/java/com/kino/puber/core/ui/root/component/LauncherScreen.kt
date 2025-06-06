package com.kino.puber.core.ui.root.component

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.tv.material3.Text
import com.kino.puber.core.di.DIScope
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.root.vm.LauncherVM
import kotlinx.parcelize.Parcelize
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeID
import org.koin.dsl.module

@Parcelize
internal class LauncherScreen : PuberScreen {

    @Suppress("unused")
    private fun buildModule(scopeId: ScopeID, parentScope: Scope) = module {
        scope(named(scopeId)) {
            viewModelOf(::LauncherVM)
        }
    }

    @Composable
    override fun Content() = DIScope(scopeName = key, moduleFactory = ::buildModule) {
        Box {
            val vm = koinViewModel<LauncherVM>()
            vm.collectViewState()
            Text("Launcher TBD")
        }
    }
}