package com.kino.puber.ui.feature.main.component

import androidx.compose.runtime.Composable
import com.kino.puber.core.di.DIScope
import com.kino.puber.core.ui.model.VideoItemTypeMapper
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.TabRouter
import com.kino.puber.ui.feature.main.model.MainUIMapper
import com.kino.puber.ui.feature.main.vm.MainVM
import kotlinx.parcelize.Parcelize
import org.koin.core.module.Module
import org.koin.core.module.dsl.scopedOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeID
import org.koin.dsl.module

@Parcelize
internal class MainScreen : PuberScreen {

    @Suppress("unused")
    private fun buildModule(scopeId: ScopeID, parentScope: Scope): Module {
        return module {
            scope(named(scopeId)) {
                //возможно потребуется создать отедельный Flow для авторизованной зоны,чтобы провайдить эти маперы
                scopedOf(::VideoItemUIMapper)
                scopedOf(::VideoItemTypeMapper)

                scopedOf(::TabRouter)
                scopedOf(::MainUIMapper)
                viewModelOf(::MainVM)
            }
        }
    }

    @Composable
    override fun Content() = DIScope(scopeName = key, moduleFactory = ::buildModule) {
        MainScreenComponent()
    }
}