package com.kino.puber.ui.feature.episodeschedule.component

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.kino.puber.core.di.LocalPuberKoinScope
import com.kino.puber.core.ui.navigation.AppLauncher
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.RootPuberScreen
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.core.ui.uikit.component.modifier.rememberFocusRequesterOnLaunch
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.domain.model.ScheduleProvider
import com.kino.puber.ui.feature.episodeschedule.model.EpisodeScheduleScreenParams
import com.kino.puber.ui.feature.episodeschedule.model.EpisodeScheduleScreenState
import kotlinx.datetime.LocalDate
import kotlinx.parcelize.Parcelize
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.koin.core.qualifier.named
import org.koin.dsl.module

private const val DETAILS_SCHEDULE_TAG = "probe_details_schedule"
private const val SCHEDULE_CARD_TAG = "episode_schedule_1_1"

internal class EpisodeSchedulePushBackFocusTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun focusedDetailsAction_pushesSchedule_restoresDetailsFocusAfterBack() {
        composeRule.setContent {
            PuberTheme {
                com.kino.puber.core.ui.navigation.component.FlowComponent(
                    scopeName = "EpisodeSchedulePushBackFocusTest",
                    screen = ProbeDetailsScreen,
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

        composeRule.waitUntil {
            composeRule
                .onNodeWithTag(DETAILS_SCHEDULE_TAG)
                .fetchSemanticsNode()
                .config
                .getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Focused) == true
        }
        composeRule.onNodeWithTag(DETAILS_SCHEDULE_TAG)
            .assertIsFocused()
            .performKeyInput {
                keyDown(Key.DirectionCenter)
                keyUp(Key.DirectionCenter)
            }

        composeRule.waitUntil {
            composeRule
                .onNodeWithTag(SCHEDULE_CARD_TAG)
                .fetchSemanticsNode()
                .config
                .getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Focused) == true
        }
        composeRule.onNodeWithTag(SCHEDULE_CARD_TAG).assertIsFocused()
        composeRule.runOnIdle {
            assertNotNull(ProbeNavigationHost.router)
            ProbeNavigationHost.router!!.back()
        }

        composeRule.waitUntil {
            composeRule
                .onNodeWithTag(DETAILS_SCHEDULE_TAG)
                .fetchSemanticsNode()
                .config
                .getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Focused) == true
        }
        composeRule.onNodeWithTag(DETAILS_SCHEDULE_TAG).assertIsFocused()
    }
}

private object NoOpAppLauncher : AppLauncher {
    override fun restart() = Unit

    override fun finish() = Unit

    override fun bind(activity: Activity) = Unit

    override fun unbind() = Unit
}

private object ProbeNavigationHost {
    var router: AppRouter? = null
}

private object ProbeScreens : Screens {
    override fun auth(): PuberScreen = unsupported()

    override fun main(): PuberScreen = unsupported()

    override fun search(): PuberScreen = unsupported()

    override fun home(): PuberScreen = unsupported()

    override fun history(
        presentation: com.kino.puber.ui.feature.history.model.HistoryPresentation,
    ): PuberScreen = unsupported()

    override fun collections(): PuberScreen = unsupported()

    override fun bookmarks(): PuberScreen = unsupported()

    override fun favorites(): PuberScreen = unsupported()

    override fun deviceSettings(): PuberScreen = unsupported()

    override fun contentList(
        tabType: com.kino.puber.ui.feature.main.model.TabType,
    ): PuberScreen = unsupported()

    override fun underDevelopment(): PuberScreen = unsupported()

    override fun details(itemId: Int): PuberScreen = unsupported()

    override fun details(
        itemId: Int,
        initialEpisode: com.kino.puber.ui.feature.details.model.DetailsEpisodeTarget,
    ): PuberScreen = unsupported()

    override fun episodeSchedule(params: EpisodeScheduleScreenParams): PuberScreen {
        return ProbeScheduleScreen
    }

    override fun player(
        itemId: Int,
        seasonNumber: Int?,
        episodeNumber: Int?,
        videoNumber: Int?,
        startMode: com.kino.puber.ui.feature.player.model.PlayerStartMode,
    ): PuberScreen = unsupported()

    private fun unsupported(): Nothing {
        error("Not used by the schedule push/back focus integration test")
    }
}

@Parcelize
private data object ProbeDetailsScreen : RootPuberScreen {
    @Composable
    override fun Content() {
        val router = requireNotNull(LocalPuberKoinScope.current).get<AppRouter>()
        val focusRequester = rememberFocusRequesterOnLaunch()
        SideEffect {
            ProbeNavigationHost.router = router
        }
        Column {
            Button(
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .then(Modifier.testTag(DETAILS_SCHEDULE_TAG)),
                onClick = {
                    router.navigateTo(
                        router.screens.episodeSchedule(
                            EpisodeScheduleScreenParams(
                                itemId = 42,
                                title = "Series",
                                imdbId = "tt123",
                            ),
                        ),
                    )
                },
            ) {
                Text("Расписание")
            }
        }
    }
}

@Parcelize
private data object ProbeScheduleScreen : PuberScreen {
    @Composable
    override fun Content() {
        EpisodeScheduleScreenContent(
            state = EpisodeScheduleScreenState.Content(
                title = "Series",
                provider = ScheduleProvider.TMDB,
                seasons = listOf(
                    EpisodeScheduleScreenState.Season(
                        seasonNumber = 1,
                        announcementDate = null,
                        announcementDateLabel = null,
                        episodes = listOf(
                            EpisodeScheduleScreenState.Episode(
                                episodeNumber = 1,
                                title = "Episode 1",
                                airDate = LocalDate(2026, 8, 23),
                                airDateLabel = "23 авг. 2026 г.",
                            ),
                        ),
                    ),
                ),
            ),
            onAction = {},
        )
    }
}
