package com.kino.puber.core.ui.navigation.component

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.kino.puber.core.di.DIScope
import com.kino.puber.core.logger.log
import com.kino.puber.core.ui.navigation.AppLauncher
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.Command
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.PuberScreenActivity
import com.kino.puber.core.ui.navigation.puberPop
import com.kino.puber.core.ui.navigation.puberPopUntil
import com.kino.puber.core.ui.navigation.puberPush
import com.kino.puber.core.ui.navigation.puberReplace
import com.kino.puber.core.ui.navigation.puberReplaceAll
import com.kino.puber.core.ui.uikit.component.FullScreenProgressIndicator
import kotlinx.coroutines.CoroutineScope
import kotlinx.parcelize.Parcelize
import com.kino.puber.core.di.LocalPuberKoinScope
import com.kino.puber.core.di.LocalPuberKoinScope
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeID
import org.koin.dsl.module

@Suppress("unused")
private fun buildFlowModule(
    scopeId: ScopeID,
    parentScope: Scope,
    coroutineScope: CoroutineScope
): Module = module {
    scope(named(scopeId)) {
        scoped<CoroutineScope> { coroutineScope }
        scoped {
            AppRouter(
                coroutineScope = get(),
                screens = get(),
            )
        }
    }
}

@Composable
fun FlowComponent(
    scopeName: String,
    screen: PuberScreen = LoadingScreen,
    composableScope: CoroutineScope = rememberCoroutineScope(),
    moduleFactory: (scopeId: ScopeID, parentScope: Scope) -> Module = { _, _ -> module {} },
    content: @Composable () -> Unit = {},
) = DIScope(
    moduleFactory = { scopeId, parentScope ->
        module {
            this.includes(
                buildFlowModule(scopeId, parentScope, composableScope),
                moduleFactory(scopeId, parentScope),
            )
        }
    },
    scopeName = scopeName,
) {
    val router by LocalPuberKoinScope.current!!.inject<AppRouter>()

    Navigator(
        screen = screen,
        onBackPressed = { onBackPressed(router) },
    ) {
        CurrentScreen("currentScreen$scopeName")
        FlowCommandRunner(router)
    }
    content()
}

private fun onBackPressed(router: AppRouter): Boolean {
    val dispatched = router.dispatchBackPressed()
    // Return false when dispatched: we handle pop via Command.Back,
    // prevent Voyager's NavigatorBackHandler from doing a double pop.
    // Return true when NOT dispatched: let Voyager pop the screen itself.
    return !dispatched
}

@Composable
private fun FlowCommandRunner(router: AppRouter) {
    val navigator = LocalNavigator.currentOrThrow
    val context = LocalContext.current
    val activityNavigator = remember(context) { ActivityNavigator(context) }
    val diScope = LocalPuberKoinScope.current ?: return
    val scopeName = diScope.id
    val appLauncher by diScope.inject<AppLauncher>()
    LaunchedEffect(scopeName) {
        router.events().collect { event ->
            router.log("router command: $event")
            if (event.screen is PuberScreenActivity) {
                activityNavigator.navigateTo(event.screen as PuberScreenActivity)
            } else {
                when (event) {
                    is Command.NavigateTo -> navigator.puberPush(event.screen)
                    is Command.Replace -> navigator.puberReplace(event.screen)
                    is Command.NewRoot -> navigator.puberReplaceAll(*event.screens.toTypedArray())
                    is Command.BackTo -> onBackTo(navigator, event)
                    Command.FinishFlow -> navigator.parent?.let { parentNavigator ->
                        onBackEventNavigator(
                            navigator = parentNavigator,
                            appLauncher = appLauncher,
                        )
                    } ?: appLauncher.finish()

                    is Command.Back -> onBackEventNavigator(
                        navigator = navigator,
                        appLauncher = appLauncher,
                    )
                }
            }
        }
    }
}

private fun onBackTo(
    navigator: Navigator,
    event: Command.BackTo,
) {
    if (navigator.items.firstOrNull { it.key == event.screen.key } != null) {
        navigator.puberPopUntil { it.key == event.screen.key }
    } else {
        navigator.puberReplaceAll(event.screen)
    }
}

private fun onBackEventNavigator(
    navigator: Navigator,
    appLauncher: AppLauncher?,
) {
    onBackWithNavigator(navigator, appLauncher)
}

private fun onBackWithNavigator(navigator: Navigator, appLauncher: AppLauncher?) {
    if (navigator.canPop()) {
        navigator.puberPop()
    } else {
        navigator.parent?.let { parent ->
            onBackWithNavigator(parent, appLauncher)
        } ?: appLauncher?.finish()
    }
}

private fun Navigator.canPop(): Boolean {
    return items.filter { it.key != LoadingScreen.key }.size > 1
}

private class ActivityNavigator(private val context: Context) {
    fun navigateTo(screen: PuberScreenActivity) {
        context.startActivity(screen.getActivityIntent(context))
    }
}

val LocalScreenKey: ProvidableCompositionLocal<ScreenKey?> = staticCompositionLocalOf { null }

@Composable
private fun CurrentScreen(key: String) {
    val navigator = LocalNavigator.currentOrThrow
    val currentScreen = navigator.lastItem
    val screenKey = key + currentScreen.key

    CompositionLocalProvider(LocalScreenKey provides screenKey) {
        navigator.saveableState(screenKey) {
            currentScreen.Content()
        }
    }
}

@Parcelize
object LoadingScreen : PuberScreen {
    @Composable
    override fun Content() {
        FullScreenProgressIndicator()
    }
}