package com.kino.puber.core.ui.navigation.component

import android.app.Activity
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.kino.puber.core.di.DIScope
import com.kino.puber.core.di.LocalPuberKoinScope
import com.kino.puber.core.ui.navigation.AppLauncher
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.OverlayPuberScreen
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.ui.feature.details.model.DetailsEpisodeTarget
import com.kino.puber.ui.feature.episodeschedule.model.EpisodeScheduleScreenParams
import com.kino.puber.ui.feature.history.model.HistoryPresentation
import com.kino.puber.ui.feature.main.model.TabType
import com.kino.puber.ui.feature.player.model.PlayerStartMode
import cafe.adriel.voyager.core.screen.ScreenKey
import kotlinx.parcelize.Parcelize
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.module

private const val BASE_TAG = "overlay-layer-base"
private const val OVERLAY_TAG = "overlay-layer-overlay"
private const val BASE_SCOPE = "OverlayLayerBaseScreen"
private const val OVERLAY_SCOPE = "OverlayLayerOverlayScreen"
private const val RESULT_CODE = 4711

/**
 * Pushing an [OverlayPuberScreen] keeps the screen underneath composed. That lower layer must keep
 * the composition (and therefore the Koin scope) it already owns: recreating it throws
 * `ScopeAlreadyCreatedException`, because a new scope is created during composition while the old
 * one is only closed later, when the abandoned composition is applied.
 */
internal class FlowComponentOverlayLayerTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun resetProbes() {
        ScopeProbe.reset()
        RouterHandle.router = null
    }

    @After
    fun clearProbes() {
        ScopeProbe.reset()
        RouterHandle.router = null
    }

    @Test
    fun pushingOverlay_rendersOverlayAboveBaseScreen() {
        setContent("render")

        composeRule.onNodeWithTag(BASE_TAG).assertIsDisplayed()

        navigateToOverlay()

        composeRule.onNodeWithTag(BASE_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(OVERLAY_TAG).assertIsDisplayed()
    }

    @Test
    fun pushingOverlay_keepsBaseScreenScope() {
        setContent("keep-scope")

        composeRule.onNodeWithTag(BASE_TAG).assertIsDisplayed()
        assertEquals(1, ScopeProbe.baseScopes.size)

        navigateToOverlay()

        composeRule.onNodeWithTag(OVERLAY_TAG).assertIsDisplayed()
        assertEquals(
            "Base screen scope was recreated while the overlay was pushed",
            1,
            ScopeProbe.baseScopes.size,
        )
    }

    @Test
    fun poppingOverlay_keepsBaseScreenScope() {
        setContent("pop")

        navigateToOverlay()
        composeRule.onNodeWithTag(OVERLAY_TAG).assertIsDisplayed()

        composeRule.runOnUiThread { requireRouter().back() }
        composeRule.waitUntil { !overlayExists() }

        composeRule.onNodeWithTag(BASE_TAG).assertIsDisplayed()
        assertEquals(
            "Base screen scope was recreated while the overlay was popped",
            1,
            ScopeProbe.baseScopes.size,
        )
    }

    private fun setContent(scopeSuffix: String) {
        composeRule.setContent {
            FlowComponent(
                scopeName = "FlowComponentOverlayLayerTest:$scopeSuffix",
                screen = BaseProbeScreen,
                moduleFactory = { scopeId, _ ->
                    module {
                        scope(named(scopeId)) {
                            scoped<AppLauncher> { OverlayNoOpAppLauncher }
                            scoped<Screens> { OverlayProbeScreens }
                        }
                    }
                },
            )
        }
        composeRule.waitUntil { RouterHandle.router != null }
    }

    private fun navigateToOverlay() {
        composeRule.runOnUiThread {
            requireRouter().navigateForResult<Any>(
                screen = OverlayProbeScreen,
                requestCode = RESULT_CODE,
            ) { }
        }
        composeRule.waitUntil { overlayExists() }
    }

    private fun overlayExists(): Boolean =
        composeRule.onAllNodesWithTag(OVERLAY_TAG).fetchSemanticsNodes().isNotEmpty()

    private fun requireRouter(): AppRouter =
        requireNotNull(RouterHandle.router) { "Flow router was not published by the base screen" }
}

private object RouterHandle {
    @Volatile
    var router: AppRouter? = null
}

private object ScopeProbe {
    val baseScopes = mutableListOf<Scope>()

    fun recordBase(scope: Scope) {
        if (baseScopes.none { it === scope }) {
            baseScopes.add(scope)
        }
    }

    fun reset() {
        baseScopes.clear()
    }
}

@Parcelize
private data object BaseProbeScreen : PuberScreen {

    override val key: ScreenKey
        get() = BASE_SCOPE

    @Composable
    override fun Content() = DIScope(scopeName = key) {
        val scope = requireNotNull(LocalPuberKoinScope.current)
        SideEffect {
            ScopeProbe.recordBase(scope)
            RouterHandle.router = scope.get<AppRouter>()
        }
        BasicText(
            text = "Base",
            modifier = Modifier
                .fillMaxSize()
                .testTag(BASE_TAG)
                .focusable(),
        )
    }
}

@Parcelize
private data object OverlayProbeScreen : OverlayPuberScreen {

    override val key: ScreenKey
        get() = OVERLAY_SCOPE

    @Composable
    override fun Content() = DIScope(scopeName = key) {
        BasicText(
            text = "Overlay",
            modifier = Modifier
                .testTag(OVERLAY_TAG)
                .focusable(),
        )
    }
}

private object OverlayNoOpAppLauncher : AppLauncher {
    override fun restart() = Unit

    override fun finish() = Unit

    override fun bind(activity: Activity) = Unit

    override fun unbind() = Unit
}

private object OverlayProbeScreens : Screens {
    override fun auth(): PuberScreen = unsupported()

    override fun main(): PuberScreen = unsupported()

    override fun search(): PuberScreen = unsupported()

    override fun actorItems(actorName: String): PuberScreen = unsupported()

    override fun home(): PuberScreen = unsupported()

    override fun history(presentation: HistoryPresentation): PuberScreen = unsupported()

    override fun collections(): PuberScreen = unsupported()

    override fun bookmarks(): PuberScreen = unsupported()

    override fun bookmarkPicker(
        itemId: Int,
        resultCode: Int,
    ): PuberScreen = OverlayProbeScreen

    override fun favorites(): PuberScreen = unsupported()

    override fun deviceSettings(): PuberScreen = unsupported()

    override fun contentList(tabType: TabType): PuberScreen = unsupported()

    override fun underDevelopment(): PuberScreen = unsupported()

    override fun details(itemId: Int): PuberScreen = unsupported()

    override fun details(
        itemId: Int,
        initialEpisode: DetailsEpisodeTarget,
    ): PuberScreen = unsupported()

    override fun episodeSchedule(params: EpisodeScheduleScreenParams): PuberScreen = unsupported()

    override fun player(
        itemId: Int,
        seasonNumber: Int?,
        episodeNumber: Int?,
        videoNumber: Int?,
        startMode: PlayerStartMode,
    ): PuberScreen = unsupported()

    private fun unsupported(): Nothing {
        error("Not used by the overlay layer integration test")
    }
}
