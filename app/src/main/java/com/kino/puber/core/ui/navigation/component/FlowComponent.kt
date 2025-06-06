package com.kino.puber.core.ui.navigation.component

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.bottomSheet.BottomSheetNavigator
import cafe.adriel.voyager.navigator.bottomSheet.LocalBottomSheetNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.kino.puber.core.di.DIScope
import com.kino.puber.core.logger.log
import com.kino.puber.core.ui.navigation.AppLauncher
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.Command
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.PuberScreenActivity
import com.kino.puber.core.ui.navigation.osomeHide
import com.kino.puber.core.ui.navigation.osomePop
import com.kino.puber.core.ui.navigation.osomePopUntil
import com.kino.puber.core.ui.navigation.osomePush
import com.kino.puber.core.ui.navigation.osomeReplace
import com.kino.puber.core.ui.navigation.osomeReplaceAll
import com.kino.puber.core.ui.navigation.osomeShow
import com.kino.puber.core.ui.uikit.component.FullScreenProgressIndicator
import kotlinx.coroutines.CoroutineScope
import kotlinx.parcelize.Parcelize
import org.koin.compose.LocalKoinScope
import org.koin.compose.currentKoinScope
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
        buildFlowModule(scopeId, parentScope, composableScope)
        moduleFactory(scopeId, parentScope)
    },
    scopeName = scopeName,
) {
    val router by currentKoinScope().inject<AppRouter>()

    BottomSheetNavigator(
        sheetBackgroundColor = Color.Unspecified,
        sheetElevation = 0.dp,
        hideOnBackPress = false,
        sheetContent = { navigator ->
            BackHandler(enabled = navigator.isVisible) {
                navigator.items.lastOrNull()?.let {
                    onBackPressed(router)
                }
            }
            CurrentScreen("sheetScreen:$scopeName")
        },
    ) {
        Navigator(
            screen = screen,
            onBackPressed = { onBackPressed(router) },
        ) {
            CurrentScreen("currentScreen$scopeName")
            FlowCommandRunner(router)
        }
    }
    content()
}

private fun onBackPressed(router: AppRouter): Boolean {
    return router.dispatchBackPressed()
}

@Composable
private fun FlowCommandRunner(router: AppRouter) {
    val navigator = LocalNavigator.currentOrThrow
    val bottomSheetNavigator = LocalBottomSheetNavigator.current
    val context = LocalContext.current
    val activityNavigator = remember(context) { ActivityNavigator(context) }
    val diScope = LocalKoinScope.current
    val scopeName = diScope.id
    val appLauncher by diScope.inject<AppLauncher>()
    LaunchedEffect(scopeName) {
        router.events().collect { event ->
            router.log("router command: $event")
            if (event !is Command.ShowOver) {
                bottomSheetNavigator.osomeHide()
            }

            if (event.screen is PuberScreenActivity) {
                activityNavigator.navigateTo(event.screen as PuberScreenActivity)
            } else {
                when (event) {
                    is Command.NavigateTo -> navigator.osomePush(event.screen)
                    is Command.ShowOver -> bottomSheetNavigator.osomeShow(event.screen)
                    is Command.HideOver -> bottomSheetNavigator.osomeHide()
                    is Command.Replace -> navigator.osomeReplace(event.screen)
                    is Command.NewRoot -> navigator.osomeReplaceAll(*event.screens.toTypedArray())
                    is Command.BackTo -> onBackTo(navigator, event)
                    Command.FinishFlow -> navigator.parent?.let { parentNavigator ->
                        onBackEventNavigator(
                            navigator = parentNavigator,
                            appLauncher = appLauncher,
                        )
                    } ?: appLauncher.finish()

                    is Command.Back -> onBackEvent(
                        navigator = navigator,
                        appLauncher = appLauncher,
                        sheetNavigator = bottomSheetNavigator,
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
        navigator.osomePopUntil { it.key == event.screen.key }
    } else {
        navigator.osomeReplaceAll(event.screen)
    }
}

private fun onBackEvent(
    navigator: Navigator,
    appLauncher: AppLauncher?,
    sheetNavigator: BottomSheetNavigator,
) {
    if (sheetNavigator.isVisible) {
        onBackEvenSheetNavigator(sheetNavigator = sheetNavigator)
    } else {
        onBackEventNavigator(navigator, appLauncher)
    }
}

private fun onBackEvenSheetNavigator(
    sheetNavigator: BottomSheetNavigator,
) {
    if (sheetNavigator.canPop) {
        sheetNavigator.osomePop()
    } else {
        sheetNavigator.osomeHide()
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
        navigator.osomePop()
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

@Composable
private fun CurrentScreen(key: String) {
    val navigator = LocalNavigator.currentOrThrow
    val currentScreen = navigator.lastItem
    val screenKey = key + currentScreen.key

    navigator.saveableState(screenKey) {
        currentScreen.Content()
    }
}

@Parcelize
object LoadingScreen : PuberScreen {
    @Composable
    override fun Content() {
        FullScreenProgressIndicator()
    }
}