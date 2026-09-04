package com.kino.puber.core.ui.navigation.component

import android.app.Activity
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import com.kino.puber.core.ui.navigation.AppLauncher
import com.kino.puber.core.ui.navigation.AppRemoteHotkeyHandler
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.ui.feature.details.model.DetailsEpisodeTarget
import com.kino.puber.ui.feature.episodeschedule.model.EpisodeScheduleScreenParams
import com.kino.puber.ui.feature.history.model.HistoryPresentation
import com.kino.puber.ui.feature.main.model.TabType
import com.kino.puber.ui.feature.player.model.PlayerStartMode
import kotlinx.parcelize.Parcelize
import org.junit.Rule
import org.junit.Test
import org.koin.core.qualifier.named
import org.koin.dsl.module

private const val ROOT_TAG = "remote-hotkey-root"
private const val SEARCH_TAG = "remote-hotkey-search"
private const val SETTINGS_TAG = "remote-hotkey-settings"

internal class FlowComponentRemoteHotkeyTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun unconsumedSearchKey_reachesRootHandlerAndNavigatesThroughRootRouter() {
        setContent("search")

        focusRoot()
        composeRule.onNodeWithTag(ROOT_TAG).performKeyInput {
            keyDown(Key.Search)
            keyUp(Key.Search)
        }

        composeRule.onNodeWithTag(SEARCH_TAG).assertExists()
    }

    @Test
    fun unconsumedSettingsKey_reachesRootHandlerAndNavigatesThroughRootRouter() {
        setContent("settings")

        focusRoot()
        composeRule.onNodeWithTag(ROOT_TAG).performKeyInput {
            keyDown(Key.Settings)
            keyUp(Key.Settings)
        }

        composeRule.onNodeWithTag(SETTINGS_TAG).assertExists()
    }

    private fun setContent(scopeSuffix: String) {
        composeRule.setContent {
            FlowComponent(
                scopeName = "FlowComponentRemoteHotkeyTest:$scopeSuffix",
                screen = ProbeRootScreen,
                remoteKeyHandler = AppRemoteHotkeyHandler::handle,
                moduleFactory = { scopeId, _ ->
                    module {
                        scope(named(scopeId)) {
                            scoped<AppLauncher> { NoOpAppLauncher }
                            scoped<Screens> { ProbeScreens }
                        }
                    }
                },
            )
        }
    }

    private fun focusRoot() {
        composeRule
            .onNodeWithTag(ROOT_TAG)
            .performSemanticsAction(SemanticsActions.RequestFocus)
    }
}

private object NoOpAppLauncher : AppLauncher {
    override fun restart() = Unit

    override fun finish() = Unit

    override fun bind(activity: Activity) = Unit

    override fun unbind() = Unit
}

private object ProbeScreens : Screens {
    override fun auth(): PuberScreen = unsupported()

    override fun main(): PuberScreen = unsupported()

    override fun search(): PuberScreen = ProbeSearchScreen

    override fun actorItems(actorName: String): PuberScreen = unsupported()

    override fun home(): PuberScreen = unsupported()

    override fun history(presentation: HistoryPresentation): PuberScreen = unsupported()

    override fun collections(): PuberScreen = unsupported()

    override fun bookmarks(): PuberScreen = unsupported()

    override fun bookmarkPicker(itemId: Int, resultCode: Int): PuberScreen = unsupported()

    override fun favorites(): PuberScreen = unsupported()

    override fun deviceSettings(): PuberScreen = ProbeSettingsScreen

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
        error("Not used by the remote hotkey integration test")
    }
}

@Parcelize
private data object ProbeRootScreen : PuberScreen {
    @Composable
    override fun Content() {
        BasicText(
            text = "Root",
            modifier = Modifier
                .fillMaxSize()
                .testTag(ROOT_TAG)
                .focusable(),
        )
    }
}

@Parcelize
private data object ProbeSearchScreen : PuberScreen {
    @Composable
    override fun Content() {
        BasicText(
            text = "Search",
            modifier = Modifier
                .fillMaxSize()
                .testTag(SEARCH_TAG)
                .focusable(),
        )
    }
}

@Parcelize
private data object ProbeSettingsScreen : PuberScreen {
    @Composable
    override fun Content() {
        BasicText(
            text = "Settings",
            modifier = Modifier
                .fillMaxSize()
                .testTag(SETTINGS_TAG)
                .focusable(),
        )
    }
}
