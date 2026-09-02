package com.kino.puber.core.ui.navigation.component

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.kino.puber.core.di.DIScope
import com.kino.puber.core.di.LocalPuberKoinScope
import com.kino.puber.core.di.LocalPuberScopePrefix
import com.kino.puber.core.logger.log
import com.kino.puber.core.ui.navigation.AppLauncher
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.Command
import com.kino.puber.core.ui.navigation.OverlayPuberScreen
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.PuberScreenActivity
import com.kino.puber.core.ui.navigation.RootPuberScreen
import com.kino.puber.core.ui.navigation.puberPop
import com.kino.puber.core.ui.navigation.puberPopUntil
import com.kino.puber.core.ui.navigation.puberPush
import com.kino.puber.core.ui.navigation.puberReplace
import com.kino.puber.core.ui.navigation.puberReplaceAll
import com.kino.puber.core.ui.uikit.component.FullScreenProgressIndicator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.yield
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
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
    remoteKeyHandler: ((android.view.KeyEvent, AppRouter, PuberScreen) -> Boolean)? = null,
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

    FlowNavigator(
        scopeName = scopeName,
        screen = screen,
        router = router,
        remoteKeyHandler = remoteKeyHandler,
    )
    content()
}

@Composable
private fun FlowNavigator(
    scopeName: String,
    screen: PuberScreen,
    router: AppRouter,
    remoteKeyHandler: ((android.view.KeyEvent, AppRouter, PuberScreen) -> Boolean)?,
) {
    val contentFocusRequester = remember { FocusRequester() }
    val rootAnchorCaptureRegistry = remember { RootAnchorCaptureRegistry() }
    var rootFocusRestoreVersion by remember { mutableIntStateOf(0) }

    Navigator(
        screen = screen,
        onBackPressed = { onBackPressed(router) },
    ) { navigator ->
        val currentScreenKey = screenCompositionKey(
            prefix = "currentScreen$scopeName",
            screenKey = navigator.lastItem.key,
        )
        CompositionLocalProvider(
            LocalRootFocusRestoreVersion provides rootFocusRestoreVersion,
            LocalRootAnchorCaptureRegistry provides rootAnchorCaptureRegistry,
            LocalRootAnchorFocusRestored provides { rootAnchorCaptureRegistry.markFocusRestored(currentScreenKey) },
            LocalRootAnchorRestoreCompletion provides rootAnchorCaptureRegistry.restoreCompletion,
            LocalRootAnchorRestorePending provides rootAnchorCaptureRegistry.restorePending,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onKeyEvent { event ->
                        remoteKeyHandler?.invoke(
                            event.nativeKeyEvent,
                            router,
                            navigator.lastItem as PuberScreen,
                        ) ?: false
                    },
            ) {
                Box(
                    Modifier
                        .focusRequester(contentFocusRequester)
                        .focusRestorer()
                        .focusGroup()
                ) {
                    CurrentScreen("currentScreen$scopeName")
                }
            }
        }
        FlowCommandRunner(
            router = router,
            contentFocusRequester = contentFocusRequester,
            onBeforeNavigate = { rootAnchorCaptureRegistry.capture(currentScreenKey) },
            rootAnchorCaptureRegistry = rootAnchorCaptureRegistry,
        )
        RootFlowReturnEffect(
            stackSize = navigator.items.size,
            currentScreenKey = currentScreenKey,
            rootAnchorCaptureRegistry = rootAnchorCaptureRegistry,
            onReturned = { rootFocusRestoreVersion++ },
        )
    }
}

internal fun onBackPressed(router: AppRouter): Boolean {
    if (!router.dispatchBackPressed()) {
        router.back()
    }
    // FlowComponent owns physical Back routing through AppRouter. Returning
    // false prevents Voyager from racing the command with a second direct pop.
    return false
}

@Composable
private fun FlowCommandRunner(
    router: AppRouter,
    contentFocusRequester: FocusRequester,
    onBeforeNavigate: () -> Unit,
    rootAnchorCaptureRegistry: RootAnchorCaptureRegistry,
) {
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
                    is Command.NavigateTo -> {
                        onBeforeNavigate()
                        contentFocusRequester.saveFocusedChild()
                        navigator.puberPush(event.screen)
                    }
                    is Command.NavigateForResult -> {
                        onBeforeNavigate()
                        contentFocusRequester.saveFocusedChild()
                        router.setOnceResultListener(event.requestCode, event.listener)
                        navigator.puberPush(event.screen)
                    }

                    is Command.Replace -> navigator.puberReplace(event.screen)
                    is Command.NewRoot -> {
                        navigator.puberReplaceAll(*event.screens.toTypedArray())
                        rootAnchorCaptureRegistry.reconcilePendingRestore(
                            rootScreenCompositionKey(scopeName, event.screen?.key),
                        )
                    }
                    is Command.BackTo -> {
                        onBackTo(navigator, event)
                        rootAnchorCaptureRegistry.reconcilePendingRestore(
                            rootScreenCompositionKey(scopeName, event.screen.key),
                        )
                    }
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

@Composable
private fun RootFlowReturnEffect(
    stackSize: Int,
    currentScreenKey: String,
    rootAnchorCaptureRegistry: RootAnchorCaptureRegistry,
    onReturned: () -> Unit,
) {
    var lastStackSize by remember { mutableIntStateOf(stackSize) }
    LaunchedEffect(stackSize) {
        val returned = stackSize < lastStackSize
        lastStackSize = stackSize
        if (returned) {
            withFrameNanos { }
            rootAnchorCaptureRegistry.reconcilePendingRestore(currentScreenKey)
            onReturned()
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
internal val LocalRootFocusRestoreVersion = staticCompositionLocalOf { 0 }
internal val LocalRootAnchorFocusRestored = staticCompositionLocalOf<() -> Unit> { {} }
internal val LocalRootAnchorRestoreCompletion = staticCompositionLocalOf {
    RootAnchorRestoreCompletion()
}
internal val LocalRootAnchorRestorePending = staticCompositionLocalOf { false }
private val LocalRootAnchorCaptureRegistry =
    staticCompositionLocalOf<RootAnchorCaptureRegistry?> { null }

internal data class RootAnchorRestoreCompletion(
    val screenKey: String? = null,
    val version: Int = 0,
)

internal data class LazyAnchor(
    val index: Int,
    val offset: Int,
)

internal class RootAnchorCaptureRegistry {
    private val captures = mutableMapOf<String, AnchorCapture>()
    private var pendingRestoreFrames by mutableStateOf<List<PendingRestoreFrame>>(emptyList())
    val restorePending: Boolean
        get() = pendingRestoreFrames.isNotEmpty()
    val focusRestored: Boolean
        get() = pendingRestoreFrames.lastOrNull()?.focusRestored == true
    var restoreCompletion by mutableStateOf(RootAnchorRestoreCompletion())
        private set

    fun register(key: String, capture: () -> LazyAnchor): () -> Unit {
        val registration = AnchorCapture(capture)
        captures[key] = registration
        return {
            if (captures[key] === registration) {
                captures.remove(key)
            }
        }
    }

    fun capture(key: String): Boolean {
        val anchor = captures[key]?.capture?.invoke() ?: return false
        pendingRestoreFrames += PendingRestoreFrame(
            screenKey = key,
            anchor = anchor,
        )
        return true
    }

    fun reconcilePendingRestore(currentScreenKey: String?) {
        val matchingFrameIndex = currentScreenKey?.let { screenKey ->
            pendingRestoreFrames.indexOfLast { it.screenKey == screenKey }
        } ?: -1
        pendingRestoreFrames = if (matchingFrameIndex >= 0) {
            pendingRestoreFrames.take(matchingFrameIndex + 1)
        } else {
            emptyList()
        }
    }

    fun savedAnchor(key: String): LazyAnchor? =
        pendingRestoreFrames.lastOrNull()
            ?.takeIf { it.screenKey == key }
            ?.anchor

    fun markFocusRestored(key: String) {
        val frame = pendingRestoreFrames.lastOrNull() ?: return
        if (frame.screenKey != key || frame.focusRestored) return
        pendingRestoreFrames = pendingRestoreFrames.dropLast(1) +
            frame.copy(focusRestored = true)
    }

    fun completeRestore(key: String) {
        if (pendingRestoreFrames.lastOrNull()?.screenKey != key) return
        pendingRestoreFrames = pendingRestoreFrames.dropLast(1)
        restoreCompletion = RootAnchorRestoreCompletion(
            screenKey = key,
            version = restoreCompletion.version + 1,
        )
    }

    private class AnchorCapture(
        val capture: () -> LazyAnchor,
    )

    private data class PendingRestoreFrame(
        val screenKey: String,
        val anchor: LazyAnchor,
        val focusRestored: Boolean = false,
    )
}

@Composable
internal fun PreserveLazyListAnchorOnRootReturn(lazyListState: LazyListState) {
    val restoreVersion = LocalRootFocusRestoreVersion.current
    val captureRegistry = LocalRootAnchorCaptureRegistry.current
    val screenKey = LocalScreenKey.current
    val focusRestored = captureRegistry?.focusRestored == true
    DisposableEffect(screenKey, lazyListState, captureRegistry) {
        val unregister = if (screenKey != null) {
            captureRegistry?.register(screenKey) {
                LazyAnchor(
                    index = lazyListState.firstVisibleItemIndex,
                    offset = lazyListState.firstVisibleItemScrollOffset,
                )
            }
        } else {
            null
        }
        onDispose {
            unregister?.invoke()
        }
    }
    LaunchedEffect(restoreVersion, focusRestored) {
        if (restoreVersion == 0 || !focusRestored) return@LaunchedEffect

        val registry = captureRegistry
        val savedAnchor = screenKey?.let(registry::savedAnchor)
            ?: return@LaunchedEffect
        lazyListState.awaitRestoredFocusScrollSettled()
        repeat(ROOT_RETURN_ANCHOR_SETTLE_FRAMES) {
            withFrameNanos { }
            if (
                lazyListState.firstVisibleItemIndex != savedAnchor.index ||
                lazyListState.firstVisibleItemScrollOffset != savedAnchor.offset
            ) {
                lazyListState.scrollToItem(savedAnchor.index, savedAnchor.offset)
            }
        }
        registry.completeRestore(screenKey)
    }
}

private suspend fun LazyListState.awaitRestoredFocusScrollSettled() {
    var consecutiveIdleFrames = 0
    repeat(ROOT_RETURN_FOCUS_SETTLE_MAX_FRAMES) { frame ->
        withFrameNanos { }
        consecutiveIdleFrames = if (isScrollInProgress) 0 else consecutiveIdleFrames + 1
        if (
            frame >= ROOT_RETURN_FOCUS_SETTLE_MIN_FRAMES &&
            consecutiveIdleFrames >= ROOT_RETURN_FOCUS_SETTLE_IDLE_FRAMES
        ) {
            return
        }
    }
}

@Composable
private fun CurrentScreen(key: String) {
    val navigator = LocalNavigator.currentOrThrow
    val screens = navigator.items.map { it as PuberScreen }

    Box {
        resolveVisibleScreenLayers(screens).forEach { screen ->
            val screenKey = screenCompositionKey(key, screen.key)
            CompositionLocalProvider(
                LocalScreenKey provides screenKey,
                LocalPuberScopePrefix provides screenKey,
            ) {
                navigator.saveableState(screenKey) {
                    screen.Content()
                }
            }
        }
    }
}

internal fun resolveVisibleScreenLayers(screens: List<PuberScreen>): List<PuberScreen> {
    if (screens.isEmpty()) return emptyList()
    var firstVisibleIndex = screens.lastIndex
    while (firstVisibleIndex > 0 && screens[firstVisibleIndex] is OverlayPuberScreen) {
        firstVisibleIndex--
    }
    return screens.subList(firstVisibleIndex, screens.size)
}

private fun screenCompositionKey(prefix: String, screenKey: ScreenKey): String = prefix + screenKey

private fun rootScreenCompositionKey(scopeName: String, screenKey: ScreenKey?): String? =
    screenKey?.let {
        screenCompositionKey(
            prefix = "currentScreen$scopeName",
            screenKey = it,
        )
    }

@Parcelize
object LoadingScreen : PuberScreen {
    @Composable
    override fun Content() {
        FullScreenProgressIndicator()
    }
}

@Composable
internal fun TabFlowComponent(
    scopeName: String,
    navigationSlotKey: ScreenKey,
    contentInstanceKey: ScreenKey,
    screen: PuberScreen,
    tabSession: TabFlowSession,
) {
    val composableScope = rememberCoroutineScope()
    val tabRouter = tabSession.router
    val parentScope = LocalPuberKoinScope.current
    val rootRouter = remember(parentScope) { parentScope?.getOrNull<AppRouter>() }
    val rootScreen = remember(contentInstanceKey, screen) {
        TabRootScreen(
            delegate = screen,
            tabInstanceKey = contentInstanceKey,
        )
    }
    DIScope(
        scopeName = scopeName,
        moduleFactory = { scopeId, _ ->
            module {
                scope(named(scopeId)) {
                    scoped<CoroutineScope> { composableScope }
                    scoped<AppRouter> { tabRouter }
                }
            }
        },
    ) {
        TabFlowNavigator(
            scopeName = scopeName,
            navigationSlotKey = navigationSlotKey,
            contentInstanceKey = contentInstanceKey,
            rootScreen = rootScreen,
            tabSession = tabSession,
            tabRouter = tabRouter,
            rootRouter = rootRouter,
        )
    }
}

@Composable
private fun TabFlowNavigator(
    scopeName: String,
    navigationSlotKey: ScreenKey,
    contentInstanceKey: ScreenKey,
    rootScreen: TabRootScreen,
    tabSession: TabFlowSession,
    tabRouter: AppRouter,
    rootRouter: AppRouter?,
) {
    val contentFocusRequester = remember { FocusRequester() }
    val rootAnchorCaptureRegistry = LocalRootAnchorCaptureRegistry.current
    Navigator(
        screen = rootScreen,
        onBackPressed = null,
        key = tabFlowNavigatorKey(navigationSlotKey),
    ) { navigator ->
        val currentScreenKey = screenCompositionKey(
            prefix = "currentTab$scopeName",
            screenKey = navigator.lastItem.key,
        )
        LaunchedEffect(contentInstanceKey) {
            replaceTabRootIfChanged(
                navigator = navigator,
                rootScreen = rootScreen,
                contentInstanceKey = contentInstanceKey,
                tabSession = tabSession,
            )
        }
        TabBackHandler(navigator, tabRouter)
        Box(
            Modifier
                .focusRequester(contentFocusRequester)
                .focusRestorer()
                .focusGroup()
        ) {
            CurrentScreen("currentTab$scopeName")
        }
        TabFlowCommandRunner(
            navigator = navigator,
            router = tabRouter,
            rootRouter = rootRouter,
            contentFocusRequester = contentFocusRequester,
            onBeforeRootNavigate = {
                rootAnchorCaptureRegistry?.capture(currentScreenKey)
            },
        )

        RestoreTabContentFocusEffect(
            stackSize = navigator.items.size,
            contentFocusRequester = contentFocusRequester,
        )
    }
}

@Composable
private fun RestoreTabContentFocusEffect(
    stackSize: Int,
    contentFocusRequester: FocusRequester,
) {
    var lastStackSize by remember { mutableIntStateOf(stackSize) }
    LaunchedEffect(stackSize) {
        if (stackSize < lastStackSize) {
            yield()
            if (!contentFocusRequester.restoreFocusedChild()) {
                runCatching { contentFocusRequester.requestFocus() }
            }
        }
        lastStackSize = stackSize
    }
}

@Parcelize
private data class TabRootScreen(
    private val delegate: PuberScreen,
    private val tabInstanceKey: ScreenKey,
) : PuberScreen {

    @IgnoredOnParcel
    override val key: ScreenKey = tabRootScreenKey(
        tabInstanceKey = tabInstanceKey,
        delegateKey = delegate.key,
    )

    @Composable
    override fun Content() {
        delegate.Content()
    }
}

internal fun tabRootScreenKey(
    tabInstanceKey: ScreenKey,
    delegateKey: ScreenKey,
): ScreenKey = "TabRoot:$tabInstanceKey:$delegateKey"

internal fun tabFlowNavigatorKey(navigationSlotKey: ScreenKey): String {
    return "TabFlow:$navigationSlotKey"
}

internal fun replaceTabRootIfChanged(
    navigator: Navigator,
    rootScreen: PuberScreen,
    contentInstanceKey: ScreenKey,
    tabSession: TabFlowSession,
): Boolean {
    if (!tabSession.beginContentInstance(contentInstanceKey)) {
        return false
    }
    tabSession.router.resetSession()
    navigator.puberReplaceAll(rootScreen)
    return true
}

private const val ROOT_RETURN_ANCHOR_SETTLE_FRAMES = 3
private const val ROOT_RETURN_FOCUS_SETTLE_IDLE_FRAMES = 2
private const val ROOT_RETURN_FOCUS_SETTLE_MIN_FRAMES = 2
private const val ROOT_RETURN_FOCUS_SETTLE_MAX_FRAMES = 12

@Composable
private fun TabBackHandler(navigator: Navigator, router: AppRouter) {
    // navigator.canPop is Voyager's Stack<Screen> property (size > 1),
    // not the private canPop() extension in this file that filters LoadingScreen.
    BackHandler(enabled = navigator.canPop) {
        if (!router.dispatchBackPressed()) {
            navigator.puberPop()
        }
    }
}

@Composable
private fun TabFlowCommandRunner(
    navigator: Navigator,
    router: AppRouter,
    rootRouter: AppRouter?,
    contentFocusRequester: FocusRequester,
    onBeforeRootNavigate: () -> Unit,
) {
    val context = LocalContext.current
    val activityNavigator = remember(context) { ActivityNavigator(context) }
    LaunchedEffect(router) {
        router.clearPendingCommands()
        router.events().collect { event ->
            router.log("tab router command: $event")
            if (event.screen is PuberScreenActivity) {
                activityNavigator.navigateTo(event.screen as PuberScreenActivity)
            } else {
                when (event) {
                    is Command.NavigateTo -> {
                        if (event.screen is RootPuberScreen && rootRouter != null) {
                            onBeforeRootNavigate()
                        }
                        contentFocusRequester.saveFocusedChild()
                        if (event.screen is RootPuberScreen && rootRouter != null) {
                            rootRouter.navigateTo(event.screen)
                        } else {
                            navigator.puberPush(event.screen)
                        }
                    }
                    is Command.NavigateForResult -> {
                        if (event.screen is RootPuberScreen && rootRouter != null) {
                            onBeforeRootNavigate()
                        }
                        contentFocusRequester.saveFocusedChild()
                        onTabNavigateForResult(
                            event = event,
                            router = router,
                            rootRouter = rootRouter,
                            pushScreen = navigator::puberPush,
                        )
                    }

                    is Command.Replace -> navigator.puberReplace(event.screen)
                    is Command.NewRoot -> navigator.puberReplaceAll(*event.screens.toTypedArray())
                    is Command.BackTo -> onBackTo(navigator, event)
                    Command.FinishFlow -> Unit
                    is Command.Back -> if (navigator.canPop) {
                        navigator.puberPop()
                    }
                }
            }
        }
    }
}

internal fun onTabNavigateForResult(
    event: Command.NavigateForResult,
    router: AppRouter,
    rootRouter: AppRouter?,
    pushScreen: (PuberScreen) -> Unit,
) {
    when (resolveTabResultNavigationTarget(event.screen, rootRouter)) {
        TabResultNavigationTarget.Root -> rootRouter?.navigateForResult(
            screen = event.screen,
            requestCode = event.requestCode,
            listener = event.listener,
        )

        TabResultNavigationTarget.Tab -> {
            router.setOnceResultListener(event.requestCode, event.listener)
            pushScreen(event.screen)
        }
    }
}

internal enum class TabResultNavigationTarget {
    Root,
    Tab,
}

internal fun resolveTabResultNavigationTarget(
    screen: PuberScreen,
    rootRouter: AppRouter?,
): TabResultNavigationTarget {
    return if (screen is RootPuberScreen && rootRouter != null) {
        TabResultNavigationTarget.Root
    } else {
        TabResultNavigationTarget.Tab
    }
}
