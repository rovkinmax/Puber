package com.kino.puber.core.ui.root.component

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.tv.material3.Surface
import com.kino.puber.core.ui.navigation.AppLauncher
import com.kino.puber.core.ui.navigation.AppLauncherImpl
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.core.ui.navigation.component.FlowComponent
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.ui.ScreensImpl
import org.koin.androidx.compose.KoinAndroidContext
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.core.scope.ScopeID
import org.koin.dsl.module

private fun buildFlowModule(
    scopeId: ScopeID,
    appLauncher: AppLauncher,
): Module = module {
    scope(named(scopeId)) {
        scoped<AppLauncher> { appLauncher }
        scoped<Screens> { ScreensImpl }
    }
}

private const val ScopeRoot = "Root"

@Composable
fun App() {
    val appLauncher = AppLauncherImpl.rememberAppLauncher()
    PuberTheme {
        KoinAndroidContext {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RectangleShape
            ) {
                FlowComponent(
                    scopeName = ScopeRoot,
                    screen = LauncherScreen(),
                    moduleFactory = { scopeId, parentScope ->
                        buildFlowModule(
                            scopeId,
                            appLauncher = appLauncher,
                        )
                    },
                )
            }
        }

    }
}