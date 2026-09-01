package com.kino.puber.ui.feature.player.component

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.lifecycle.Lifecycle
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tv.material3.Text
import com.kino.puber.R
import com.kino.puber.core.ui.navigation.AppLauncher
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.core.ui.navigation.component.FlowComponent
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.repository.PlayerPreferencesRepository
import com.kino.puber.playertestfixtures.FixtureId
import com.kino.puber.playertestfixtures.PlayerTestFixtures
import com.kino.puber.playertestfixtures.network.LoopbackNetworkJournal
import com.kino.puber.playertestfixtures.server.HermeticRoute
import com.kino.puber.playertestfixtures.server.HermeticTestServer
import com.kino.puber.playertestfixtures.server.QueryMatchMode
import com.kino.puber.playertestfixtures.server.ResponsePlan
import com.kino.puber.profile.PlayerTestControl
import com.kino.puber.ui.ScreensImpl
import com.kino.puber.ui.feature.player.PlayerComposeInstrumentationTestCase
import com.kino.puber.ui.feature.player.model.PlayerScreenParams
import com.kino.puber.ui.feature.player.model.PlayerStartMode
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.parcelize.Parcelize
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import io.github.kakaocup.compose.node.element.ComposeScreen
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Production-screen coverage. Every case launches the real [PlayerScreen]
 * through the same FlowComponent and screen Koin scope used by the app.
 */
@RunWith(AndroidJUnit4::class)
internal class PlayerScreenE2ETest : PlayerComposeInstrumentationTestCase() {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var fixture: PlayerE2EFixture

    @Before
    fun setUp() {
        fixture = PlayerE2EFixture(composeRule)
        fixture.start()
    }

    @After
    fun tearDown() {
        fixture.close()
    }

    @Test
    fun movieScreen_realRemoteControlsPanelsAndRecreation_restoreFocusTracksAndMediaState() = run {
        with(fixture) {
            val itemId = 7901
            val media = MovieMediaRoots(itemId)
            step("Launch the production movie screen on typed hermetic HLS routes") {
                server.reset(movieRoutes(itemId, watchingTime = 0, media = media))
                launchPlayer(
                    itemId = itemId,
                    startMode = PlayerStartMode.StartFromBeginning,
                )
                assertFocusedPlayPause()
                awaitPlayerReady()
                awaitPlayerCondition("initial movie position advances") {
                    playerPosition() >= POSITION_FENCE_MS
                }
            }

            step("The domain TV remote toggles playback through the player focus anchor") {
                robot.pressBack()
                assertFocusedPlayerSurface()
                robot.press(PlayerRemoteKey.PlayPause)
                awaitPlayerCondition("media key pauses playback") { !playerIsPlaying() }
                assertFocusedPlayPause()
                robot.pressBack()
                assertFocusedPlayerSurface()
                robot.press(PlayerRemoteKey.PlayPause)
                awaitPlayerCondition("media key resumes playback") { playerIsPlaying() }
                assertFocusedPlayPause()
                robot.pressBack()
                assertFocusedPlayerSurface()
                robot.press(PlayerRemoteKey.PlayPause)
                awaitPlayerCondition("second media key pauses playback") { !playerIsPlaying() }
                assertFocusedPlayPause()
            }

            step("Seek-bar and button traversal retains one concrete focused node") {
                robot.press(PlayerRemoteKey.Up)
                assertFocusedSeekBar()
                robot.press(PlayerRemoteKey.Down)
                assertFocusedPlayPause()
                robot.press(PlayerRemoteKey.Right)
                assertFocusedTag(PlayerScreenTestTags.MarkWatched, "mark-watched button")
                robot.press(PlayerRemoteKey.Right)
                assertFocusedTag(PlayerScreenTestTags.AudioSubtitles, "audio/subtitle button")
            }

            step("Select Spanish audio and external subtitles through typed panel items") {
                robot.press(PlayerRemoteKey.Select)
                assertFocusedPanelItem("sound", 0)
                robot.press(PlayerRemoteKey.Right)
                assertFocusedPanelItem("audio", 0)
                robot.press(PlayerRemoteKey.Down)
                assertFocusedPanelItem("audio", 1)
                robot.press(PlayerRemoteKey.Select)
                awaitPlayerCondition("Spanish audio is selected in UI state and Media3") {
                    selectedAudioLanguage() == SPANISH_LANGUAGE &&
                        preferredAudioLanguage() == SPANISH_LANGUAGE
                }

                robot.press(PlayerRemoteKey.Right)
                assertFocusedPanelItem("subtitle", 1)
                robot.press(PlayerRemoteKey.Select)
                awaitPlayerCondition("external subtitle is persisted and selected in Media3") {
                    preferredSubtitleLanguage() == SUBTITLE_LANGUAGE &&
                        selectedTextTrack() &&
                        preferredTextLanguages().contains(SUBTITLE_LANGUAGE)
                }
                robot.pressBack()
                assertFocusedTag(PlayerScreenTestTags.AudioSubtitles, "audio/subtitle button")
            }

            step("Playback requests the selected subtitle before pausing again") {
                robot.press(PlayerRemoteKey.Left)
                assertFocusedTag(PlayerScreenTestTags.MarkWatched, "mark-watched button")
                robot.press(PlayerRemoteKey.Left)
                assertFocusedPlayPause()
                robot.press(PlayerRemoteKey.Select)
                awaitPlayerCondition("selected external subtitle loads during playback") {
                    playerIsPlaying()
                }
                awaitJournalPath("${media.high}/subtitle.vtt")
                robot.press(PlayerRemoteKey.Select)
                awaitPlayerCondition("playback pauses after external subtitle request") {
                    !playerIsPlaying()
                }
            }

            step("Switch to the lower quality and prove the production Media3 request") {
                robot.press(PlayerRemoteKey.Right)
                assertFocusedTag(PlayerScreenTestTags.MarkWatched, "mark-watched button")
                robot.press(PlayerRemoteKey.Right)
                assertFocusedTag(PlayerScreenTestTags.AudioSubtitles, "audio/subtitle button")
                robot.press(PlayerRemoteKey.Right)
                assertFocusedTag(PlayerScreenTestTags.VideoSettings, "video-settings button")
                robot.press(PlayerRemoteKey.Select)
                assertFocusedPanelItem("quality", 0)
                robot.press(PlayerRemoteKey.Down)
                assertFocusedPanelItem("quality", 1)
                robot.press(PlayerRemoteKey.Down)
                assertFocusedPanelItem("quality", 2)
                robot.press(PlayerRemoteKey.Select)
                awaitPlayerCondition("manual quality changes the production Media3 item") {
                    currentMediaPath() == "${media.low}/master.m3u8"
                }
                awaitJournalPath("${media.low}/master.m3u8")
                robot.pressBack()
                assertFocusedTag(PlayerScreenTestTags.VideoSettings, "video-settings button")
                robot.pressBack()
                assertFocusedPlayerSurface()
                robot.press(PlayerRemoteKey.Down)
                assertFocusedPlayPause()
            }

            step("Activity recreation restores production playback and selected tracks") {
                val previousActivity = AtomicReference<ComponentActivity>()
                composeRule.activityRule.scenario.onActivity(previousActivity::set)
                composeRule.activityRule.scenario.recreate()
                setPlayerContent()
                composeRule.activityRule.scenario.onActivity { recreatedActivity ->
                    assertTrue(
                        "ActivityScenario.recreate must replace the host activity",
                        recreatedActivity !== previousActivity.get(),
                    )
                }
                assertFocusedPlayPause()
                awaitPlayerReady()
                awaitPlayerCondition("recreated UI state restores audio and subtitle choices") {
                    preferredAudioLanguage() == SPANISH_LANGUAGE &&
                        preferredSubtitleLanguage() == SUBTITLE_LANGUAGE
                }
                awaitPlayerCondition("recreated Media3 restores audio and subtitle choices") {
                    selectedAudioLanguage() == SPANISH_LANGUAGE &&
                        selectedTextTrack() &&
                        preferredTextLanguages().contains(SUBTITLE_LANGUAGE)
                }
            }

            step("Recreated video and audio panels close on the current router and restore focus") {
                robot.press(PlayerRemoteKey.Right)
                assertFocusedTag(PlayerScreenTestTags.MarkWatched, "mark-watched button")
                robot.press(PlayerRemoteKey.Right)
                assertFocusedTag(PlayerScreenTestTags.AudioSubtitles, "audio/subtitle button")
                robot.press(PlayerRemoteKey.Right)
                assertFocusedTag(PlayerScreenTestTags.VideoSettings, "video-settings button")
                robot.press(PlayerRemoteKey.Select)
                assertFocusedPanelItem("quality", 0)
                robot.pressBack()
                assertFalse(
                    "Video-settings panel Back must not pop PlayerScreen after recreation",
                    isTextDisplayed(HOST_TEXT),
                )
                assertFocusedTag(PlayerScreenTestTags.VideoSettings, "video-settings button")

                robot.press(PlayerRemoteKey.Left)
                assertFocusedTag(PlayerScreenTestTags.AudioSubtitles, "audio/subtitle button")
                robot.press(PlayerRemoteKey.Select)
                assertFocusedPanelItem("sound", 0)
                assertEquals(SPANISH_LANGUAGE, preferredAudioLanguage())
                assertEquals(SUBTITLE_LANGUAGE, preferredSubtitleLanguage())
                robot.pressBack()
                assertFalse(
                    "Audio/subtitle panel Back must not pop PlayerScreen after recreation",
                    isTextDisplayed(HOST_TEXT),
                )
                assertFocusedTag(PlayerScreenTestTags.AudioSubtitles, "audio/subtitle button")
            }

            step("Recreated directional seeks preserve surface focus and fail-closed requests") {
                robot.pressBack()
                assertFocusedPlayerSurface()
                robot.press(PlayerRemoteKey.PlayPause)
                awaitPlayerCondition("recreated playback starts or replays before seek checks") {
                    playerIsPlaying() && playerPosition() >= POSITION_FENCE_MS
                }
                assertFocusedPlayPause()
                robot.pressBack()
                assertFocusedPlayerSurface()
                robot.press(PlayerRemoteKey.PlayPause)
                awaitPlayerCondition("recreated playback pauses from the player surface") {
                    !playerIsPlaying() && playerPosition() >= POSITION_FENCE_MS
                }
                assertFocusedPlayPause()
                robot.pressBack()
                assertFocusedPlayerSurface()
                val beforeBackwardSeek = playerPosition()
                robot.press(PlayerRemoteKey.Left)
                awaitPlayerCondition("D-pad left seeks backward") {
                    playerPosition() < beforeBackwardSeek
                }
                awaitText(
                    context.getString(
                        R.string.player_seek_backward,
                        SEEK_STEP_SECONDS,
                    ),
                )
                assertFocusedPlayerSurface()
                val beforeForwardSeek = playerPosition()
                robot.press(PlayerRemoteKey.Right)
                awaitPlayerCondition("D-pad right seeks forward") {
                    playerPosition() > beforeForwardSeek
                }
                awaitText(
                    context.getString(
                        R.string.player_seek_forward,
                        SEEK_STEP_SECONDS,
                    ),
                )
                assertFocusedPlayerSurface()
                assertEquals(0, server.requestJournal.unknownRequests.size)
            }

            step("The unchanged second Back exits the recreated PlayerScreen") {
                robot.pressBack()
                awaitPlayerCondition("recreated PlayerScreen exits on the unchanged second Back") {
                    isTextDisplayed(HOST_TEXT)
                }
            }
        }
    }

    @Test
    fun movieScreen_continueCrossesSavedFence_andSeparatesPauseBackgroundAndExitSaves() = run {
        with(fixture) {
            val itemId = 7902
            val media = MovieMediaRoots(itemId)
            step("Launch the saved movie and continue past its persisted position") {
                server.reset(
                    movieRoutes(
                        itemId,
                        watchingTime = SAVED_POSITION_SECONDS,
                        media = media,
                    ),
                )
                launchPlayer(itemId)
                assertFocusedTag(PlayerScreenTestTags.ResumeContinue, "Continue button")
                assertTrue(playerPosition() < SAVED_POSITION_FENCE_MS)
                robot.press(PlayerRemoteKey.Select)
                awaitPlayerCondition("Continue crosses the saved-position fence") {
                    playerPosition() >= SAVED_POSITION_FENCE_MS
                }
                assertFocusedPlayerSurface()
            }

            lateinit var pauseSave: RecordedRequest
            step("Pause creates a typed progress request after observable player state") {
                robot.press(PlayerRemoteKey.PlayPause)
                awaitPlayerCondition("pause is observable before its save") {
                    !playerIsPlaying()
                }
                assertFocusedPlayPause()
                pauseSave = awaitProgressRequest(
                    "pause progress save",
                    itemId,
                    video = 1,
                    season = null,
                )
                assertProgressTimeAtLeast(pauseSave, MIN_SAVED_TIME_SECONDS)
            }

            lateinit var backgroundSave: RecordedRequest
            step("Backgrounding creates a distinct progress request and keeps playback paused") {
                robot.press(PlayerRemoteKey.Select)
                awaitPlayerCondition("play resumes before background") { playerIsPlaying() }
                composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
                backgroundSave = awaitProgressRequest(
                    "background progress save",
                    itemId,
                    video = 1,
                    season = null,
                )
                assertProgressTimeAtLeast(backgroundSave, MIN_SAVED_TIME_SECONDS)
                composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
                setPlayerContentIfMissing()
                awaitPlayerCondition("background keeps the player paused") {
                    !playerIsPlaying()
                }
                assertFocusedPlayerSurface()
            }

            step("Exit drains its own progress request after the canonical second Back") {
                robot.press(PlayerRemoteKey.PlayPause)
                awaitPlayerCondition("play resumes before exit") { playerIsPlaying() }
                assertFocusedPlayPause()
                robot.pressBack()
                assertFocusedPlayerSurface()
                robot.pressBack()
                val exitSave = awaitProgressRequest(
                    "exit progress save",
                    itemId,
                    video = 1,
                    season = null,
                )
                assertProgressTimeAtLeast(exitSave, MIN_SAVED_TIME_SECONDS)
                awaitPlayerCondition("Back exits only after the final progress write drains") {
                    isTextDisplayed(HOST_TEXT)
                }
                assertTrue(pauseSave !== backgroundSave)
                assertTrue(backgroundSave !== exitSave)
            }
        }
    }

    @Test
    fun movieScreen_failedMedia_focusesRetry_recoversAndBackDrainsProgressBeforeExit() = run {
        with(fixture) {
            val itemId = 7903
            val media = MovieMediaRoots(itemId)
            step("Launch the typed failing HLS route and focus the stable Retry control") {
                server.reset(retryMovieRoutes(itemId, media))
                launchPlayer(
                    itemId = itemId,
                    startMode = PlayerStartMode.StartFromBeginning,
                )
                assertFocusedPlayPause()
                assertFocusedTagEventually(PlayerScreenTestTags.Retry, "Retry button")
                assertEquals(
                    RETRY_FAILURE_REQUESTS,
                    routeRequestCount(media.high, "master"),
                )
            }

            step("Retry reaches real playback after the programmed response sequence") {
                robot.press(PlayerRemoteKey.Select)
                awaitPlayerReady()
                awaitPlayerCondition("Retry reaches real playback") {
                    playerIsPlaying() &&
                        currentMediaPath() == "${media.high}/master.m3u8"
                }
                assertTrue(
                    routeRequestCount(media.high, "master") > RETRY_FAILURE_REQUESTS,
                )
                assertFocusedPlayPause()
            }

            step("Recovered playback drains progress before the canonical Back exit") {
                robot.pressBack()
                assertFocusedPlayerSurface()
                robot.pressBack()
                awaitProgressRequest(
                    "Retry journey exit save",
                    itemId,
                    video = 1,
                    season = null,
                )
                awaitPlayerCondition("Back exits recovered playback") {
                    isTextDisplayed(HOST_TEXT)
                }
            }
        }
    }

    @Test
    fun seriesScreen_nextEpisodeDpadJourney_usesDistinctRoutesAndEpisodeProgressIdentity() = run {
        with(fixture) {
            val itemId = 7904
            val media = SeriesMediaRoots(itemId)
            step("Launch episode one and focus the stable next-episode action") {
                server.reset(seriesRoutes(itemId, media))
                launchPlayer(
                    itemId = itemId,
                    seasonNumber = 1,
                    episodeNumber = 1,
                    startMode = PlayerStartMode.StartFromBeginning,
                )
                assertFocusedPlayPause()
                awaitPlayerReady()
                awaitJournalPath("${media.episodeOne}/master.m3u8")
                assertFocusedTagEventually(
                    PlayerScreenTestTags.NextEpisode,
                    "next-episode button",
                )
            }

            lateinit var firstEpisodeSave: RecordedRequest
            step("Selecting next episode saves episode one under its typed video identity") {
                robot.press(PlayerRemoteKey.Select)
                firstEpisodeSave = awaitProgressRequest(
                    "first episode transition save",
                    itemId,
                    video = 1,
                    season = 1,
                )
                assertNull(firstEpisodeSave.url.queryParameter("episode"))
            }

            step("Episode two owns the production player and a distinct media route") {
                awaitJournalPath("${media.episodeTwo}/master.m3u8")
                awaitPlayerCondition("second episode owns the active production player") {
                    currentMediaPath() == "${media.episodeTwo}/master.m3u8"
                }
                awaitText(
                    context.getString(
                        R.string.player_season_episode_title,
                        1,
                        2,
                        "Second episode",
                    ),
                )
                awaitPlayerReady()
            }

            step("Pausing episode two saves progress under video two and fails closed") {
                if (isTagDisplayed(PlayerScreenTestTags.AudioSubtitles)) {
                    robot.pressBack()
                }
                assertFocusedPlayerSurface()
                robot.press(PlayerRemoteKey.PlayPause)
                awaitPlayerCondition("second episode pause is observable") {
                    !playerIsPlaying()
                }
                val secondEpisodeSave = awaitProgressRequest(
                    "second episode pause save",
                    itemId,
                    video = 2,
                    season = 1,
                )
                assertNull(secondEpisodeSave.url.queryParameter("episode"))
                assertTrue(
                    "Expected distinct episode media routes: " +
                        server.requestJournal.entries,
                    server.requestJournal.entries.any {
                        it.path == "${media.episodeOne}/master.m3u8"
                    } && server.requestJournal.entries.any {
                        it.path == "${media.episodeTwo}/master.m3u8"
                    },
                )
                assertEquals("1", firstEpisodeSave.url.queryParameter("video"))
                assertEquals("2", secondEpisodeSave.url.queryParameter("video"))
                assertEquals(0, server.requestJournal.unknownRequests.size)
            }
        }
    }
}

private class PlayerE2EFixture(
    val composeRule: androidx.compose.ui.test.junit4.AndroidComposeTestRule<
        androidx.test.ext.junit.rules.ActivityScenarioRule<ComponentActivity>,
        ComponentActivity,
        >,
) {

    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    val context
        get() = instrumentation.targetContext

    lateinit var server: PlayerTestControl
        private set
    private lateinit var scenarioToken: String
    private lateinit var flowScopeName: String
    private lateinit var currentScreen: PlayerScreen
    private var currentItemId: Int = 0
    private val ownedItemIds = mutableSetOf<Int>()

    val robot = PlayerTvRobot(composeRule) {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
    }

    fun start() {
        LoopbackNetworkJournal(context).clear()
        RecordingAppLauncher.reset()
        scenarioToken = UUID.randomUUID().toString()
        server = PlayerTestControl()
        server.start()
    }

    fun close() {
        var teardownFailure: Throwable? = null
        runCatching {
            if (composeRule.activityRule.scenario.state != Lifecycle.State.DESTROYED) {
                composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
                composeRule.activityRule.scenario.onActivity { activity ->
                    activity.setContent {}
                }
                composeRule.waitForIdle()
            }
        }.onFailure { teardownFailure = it }

        ownedItemIds.forEach { itemId ->
            PlayerPreferencesRepository(context).saveTrackPreferences(
                itemId = itemId,
                audioLang = null,
                audioLabel = null,
                subtitleLang = null,
                subtitleUrl = null,
            )
        }

        if (::server.isInitialized) {
            try {
                server.awaitQuiescence(timeout = 10, unit = TimeUnit.SECONDS)
                server.assertNoUnknownRequests()
            } catch (error: Throwable) {
                teardownFailure = teardownFailure ?: error
            } finally {
                server.close()
            }
        }

        val egress = LoopbackNetworkJournal(context).snapshot()
        if (egress.isNotEmpty()) {
            teardownFailure = teardownFailure ?: AssertionError("Unexpected network egress: $egress")
        }
        RecordingAppLauncher.reset()
        teardownFailure?.let { throw it }
    }

    fun launchPlayer(
        itemId: Int,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        startMode: PlayerStartMode = PlayerStartMode.ResumeIfAvailable,
    ) {
        ownedItemIds += itemId
        currentItemId = itemId
        flowScopeName = "PlayerScreenE2ETest:$itemId:$scenarioToken"
        currentScreen = PlayerScreen(
            PlayerScreenParams(
                itemId = itemId,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                startMode = startMode,
            ),
        )
        setPlayerContent()
        composeRule.waitUntil(PLAYER_TIMEOUT_MS) {
            server.requestJournal.entries.any { it.path == "/v1/items/$itemId" }
        }
        awaitPlayerCondition("production UI player for item $itemId") {
            uiPlayerRead { playbackState } != null
        }
    }

    fun setPlayerContent() {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                PuberTheme {
                    FlowComponent(
                        scopeName = flowScopeName,
                        screen = PlayerE2EHostScreen,
                        moduleFactory = { scopeId, _ ->
                            module {
                                scope(named(scopeId)) {
                                    scoped<AppLauncher> { RecordingAppLauncher }
                                    scoped<Screens> { ScreensImpl }
                                }
                            }
                        },
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.waitUntil(FOCUS_TIMEOUT_MS) {
            GlobalContext.get().getScopeOrNull(flowScopeName) != null
        }
        if (GlobalContext.get().getScopeOrNull(playerScopeId()) == null) {
            onMain { flowRouter().navigateTo(currentScreen) }
            composeRule.waitForIdle()
        }
    }

    fun setPlayerContentIfMissing() {
        if (runCatching { uiPlayerRead { playbackState } }.getOrNull() == null) {
            setPlayerContent()
        }
    }

    private fun flowRouter(): AppRouter {
        val scope = GlobalContext.get().getScopeOrNull(flowScopeName)
            ?: error("Missing flow scope $flowScopeName")
        return scope.get()
    }

    private fun playerScopeId(): String {
        val screenKey = currentScreen.key
        return "currentScreen$flowScopeName$screenKey:$screenKey"
    }

    fun preferredAudioLanguage(): String? =
        PlayerPreferencesRepository(context).getPreferredAudioLang(currentItemId)

    fun preferredSubtitleLanguage(): String? =
        PlayerPreferencesRepository(context).getPreferredSubtitleLang(currentItemId)

    fun playerPosition(): Long = uiPlayerRead { currentPosition } ?: 0L

    fun playerIsPlaying(): Boolean = uiPlayerRead { isPlaying } == true

    fun currentMediaPath(): String? =
        uiPlayerRead { currentMediaItem?.localConfiguration?.uri?.encodedPath }

    fun selectedAudioLanguage(): String? = uiPlayerRead {
        currentTracks
            .groups
            .filter { it.type == C.TRACK_TYPE_AUDIO }
            .firstOrNull { it.isSelected }
            ?.getTrackFormat(0)
            ?.language
    }

    fun selectedTextTrack(): Boolean = uiPlayerRead {
        currentTracks
            .groups
            .filter { it.type == C.TRACK_TYPE_TEXT }
            .any { it.isSelected }
    } == true

    fun preferredTextLanguages(): List<String> =
        uiPlayerRead { trackSelectionParameters.preferredTextLanguages.toList() }.orEmpty()

    fun awaitPlayerReady() {
        awaitPlayerCondition("Media3 READY without PlayerScreen error") {
            uiPlayerRead { playbackState } == Player.STATE_READY
        }
    }

    fun awaitPlayerCondition(
        label: String,
        condition: () -> Boolean,
    ) {
        try {
            composeRule.waitUntil(PLAYER_TIMEOUT_MS) {
                runCatching(condition).getOrDefault(false)
            }
        } catch (error: Throwable) {
            throw AssertionError("$label was not reached; diagnostics=${diagnostics()}", error)
        }
    }

    private fun diagnostics(): String {
        val player = runCatching {
            uiPlayerRead {
                "state=$playbackState,playing=$isPlaying," +
                    "position=$currentPosition,duration=$duration," +
                    "media=${currentMediaItem?.localConfiguration?.uri?.encodedPath}," +
                    "textSelected=${currentTracks.groups.any { group ->
                        group.type == C.TRACK_TYPE_TEXT && group.isSelected
                    }},preferredText=${trackSelectionParameters.preferredTextLanguages}," +
                    "savedSubtitle=${preferredSubtitleLanguage()}"
            }
        }.getOrNull()
        return "player=$player,requests=${server.requestJournal.entries.takeLast(16)}"
    }

    private fun <T> uiPlayerRead(read: Player.() -> T): T? {
        val result = AtomicReference<Result<T?>?>(null)
        composeRule.activityRule.scenario.onActivity { activity ->
            result.set(
                runCatching {
                    activity.window.decorView.findPlayerView()?.player?.read()
                },
            )
        }
        return requireNotNull(result.get()).getOrThrow()
    }

    fun assertFocusedPlayPause() {
        ComposeScreen.onComposeScreen<PlayerComposeScreen>(composeRule) {
            playPauseButton.assertIsDisplayed()
            playPauseButton.assertIsFocused()
        }
        assertSingleFocusedNode(
            isFocused() and hasTestTag(PlayerScreenTestTags.PlayPause),
            "play/pause button",
        )
    }

    fun assertFocusedSeekBar() {
        ComposeScreen.onComposeScreen<PlayerComposeScreen>(composeRule) {
            seekBar.assertIsDisplayed()
            seekBar.assertIsFocused()
        }
        val interaction = assertSingleFocusedNode(
            isFocused() and hasTestTag(PlayerScreenTestTags.SeekBar),
            "seek bar",
        )
        val rootBounds = composeRule.onRoot().getUnclippedBoundsInRoot()
        val focusedBounds = interaction.getUnclippedBoundsInRoot()
        val focusedHeight = (focusedBounds.bottom - focusedBounds.top).value
        val focusedWidth = (focusedBounds.right - focusedBounds.left).value
        val rootHeight = (rootBounds.bottom - rootBounds.top).value
        val rootWidth = (rootBounds.right - rootBounds.left).value
        assertTrue(
            "Expected bounded seek bar focus, root=$rootBounds focused=$focusedBounds",
            focusedHeight < rootHeight / 4f &&
                focusedWidth > rootWidth / 2f,
        )
    }

    fun assertFocusedPlayerSurface() {
        awaitAbsentTag(PlayerScreenTestTags.AudioSubtitles)
        ComposeScreen.onComposeScreen<PlayerComposeScreen>(composeRule) {
            playerSurface.assertIsDisplayed()
            playerSurface.assertIsFocused()
        }
        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        try {
            composeRule.waitUntil(FOCUS_TIMEOUT_MS) {
                val focused = focusedNodes()
                focused.isNotEmpty() && focused.all { node ->
                    node.boundsInRoot.width >= rootBounds.width * FULLSCREEN_FOCUS_FRACTION &&
                        node.boundsInRoot.height >= rootBounds.height * FULLSCREEN_FOCUS_FRACTION
                }
            }
        } catch (error: Throwable) {
            throw AssertionError(
                "Player surface focus did not settle; root=$rootBounds," +
                    " focused=${focusedSummary(focusedNodes())}",
                error,
            )
        }
        val focused = focusedNodes()
        focused.forEach { node ->
            assertFalse(
                "Focused player branch contains a disabled node: ${focusedSummary(focused)}",
                node.config.contains(SemanticsProperties.Disabled),
            )
            composeRule.onNode(
                SemanticsMatcher("focused player node ${node.id}") { it.id == node.id },
                useUnmergedTree = true,
            ).assertIsDisplayed().assertIsFocused()
        }
    }

    fun assertFocusedTag(tag: String, description: String) {
        assertSingleFocusedNode(focusedTagMatcher(tag), description)
        ComposeScreen.onComposeScreen<PlayerComposeScreen>(composeRule) {
            focusedTag(tag).assertIsDisplayed()
            focusedTag(tag).assertIsFocused()
        }
    }

    fun assertFocusedPanelItem(group: String, index: Int) {
        val tag = PlayerScreenTestTags.panelItem(group, index)
        assertFocusedTag(tag, "$group panel item $index")
    }

    fun assertFocusedTagEventually(tag: String, description: String) {
        val matcher = focusedTagMatcher(tag)
        composeRule.waitUntil(PLAYER_TIMEOUT_MS) {
            composeRule.onAllNodes(matcher, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .size == 1
        }
        assertSingleFocusedNode(matcher, description)
    }

    private fun assertSingleFocusedNode(
        matcher: SemanticsMatcher,
        description: String,
    ) = composeRule.onNode(matcher, useUnmergedTree = true).also { interaction ->
        try {
            composeRule.waitUntil(FOCUS_TIMEOUT_MS) {
                composeRule.onAllNodes(matcher, useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .size == 1
            }
        } catch (error: Throwable) {
            throw AssertionError(
                "$description did not acquire focus; focused=${focusedSummary(focusedNodes())}",
                error,
            )
        }
        val allFocused = composeRule.onAllNodes(isFocused(), useUnmergedTree = true)
            .fetchSemanticsNodes()
        val expected = composeRule.onAllNodes(matcher, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .single()
        assertEquals(
            "Exactly one concrete node must own focus for $description; focused=${focusedSummary(allFocused)}",
            1,
            allFocused.size,
        )
        assertEquals(
            "Focus escaped from $description; focused=${focusedSummary(allFocused)}",
            expected.id,
            allFocused.single().id,
        )
        assertFalse(
            "$description is disabled",
            expected.config.contains(SemanticsProperties.Disabled),
        )
        interaction.assertIsDisplayed().assertIsFocused()
    }

    private fun focusedTagMatcher(tag: String): SemanticsMatcher {
        val tagMatcher = hasTestTag(tag)
        return isFocused() and (
            tagMatcher or
                hasAnyDescendant(tagMatcher)
            )
    }

    private fun focusedSummary(nodes: List<SemanticsNode>): String =
        nodes.joinToString { node ->
            "id=${node.id},text=${node.config.getOrNull(SemanticsProperties.Text)}," +
                "bounds=${node.boundsInRoot}"
        }

    private fun focusedNodes(): List<SemanticsNode> =
        composeRule.onAllNodes(isFocused(), useUnmergedTree = true)
            .fetchSemanticsNodes()

    fun awaitText(text: String) {
        composeRule.waitUntil(PLAYER_TIMEOUT_MS) {
            composeRule.onAllNodesWithText(text, substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithText(text, substring = true).assertIsDisplayed()
    }

    private fun awaitAbsentTag(tag: String) {
        composeRule.waitUntil(FOCUS_TIMEOUT_MS) {
            composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty()
        }
    }

    fun isTagDisplayed(tag: String): Boolean =
        composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .isNotEmpty()

    fun isTextDisplayed(text: String): Boolean =
        composeRule.onAllNodesWithText(text, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .isNotEmpty()

    fun awaitJournalPath(path: String) {
        try {
            composeRule.waitUntil(PLAYER_TIMEOUT_MS) {
                server.requestJournal.entries.any { it.path == path }
            }
        } catch (error: Throwable) {
            throw AssertionError(
                "Request path $path was not observed; diagnostics=${diagnostics()}",
                error,
            )
        }
    }

    fun awaitProgressRequest(
        description: String,
        itemId: Int,
        video: Int,
        season: Int?,
    ): RecordedRequest = awaitRequest(description) { request ->
        request.url.encodedPath == "/v1/watching/marktime" &&
            request.url.queryParameter("id") == itemId.toString() &&
            request.url.queryParameter("video") == video.toString() &&
            request.url.queryParameter("season") == season?.toString() &&
            request.url.queryParameter("time")?.toIntOrNull() != null
    }

    fun assertProgressTimeAtLeast(request: RecordedRequest, expectedSeconds: Int) {
        val savedTime = request.url.queryParameter("time")?.toIntOrNull()
        assertNotNull("Missing typed progress time in ${request.url}", savedTime)
        assertTrue(
            "Expected progress >= $expectedSeconds seconds, request=${request.url}",
            requireNotNull(savedTime) >= expectedSeconds,
        )
    }

    private fun awaitRequest(
        description: String,
        predicate: (RecordedRequest) -> Boolean,
    ): RecordedRequest {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(PLAYER_TIMEOUT_MS)
        while (System.nanoTime() < deadline) {
            val request = server.takeRequest(timeout = 100, unit = TimeUnit.MILLISECONDS)
            if (request != null && predicate(request)) return request
        }
        fail("Missing $description; journal=${server.requestJournal.entries.takeLast(16)}")
        error("unreachable")
    }

    fun routeRequestCount(root: String, leaf: String): Int =
        server.requestJournal.matchedRoutes[mediaRouteId(root, leaf)] ?: 0

    fun movieRoutes(
        itemId: Int,
        watchingTime: Int,
        media: MovieMediaRoots,
    ): List<HermeticRoute> =
        apiRoutes(
            itemId = itemId,
            details = movieDetails(itemId, watchingTime, media),
        ) + mediaRoutes(media.high) + mediaRoutes(media.low)

    fun retryMovieRoutes(
        itemId: Int,
        media: MovieMediaRoots,
    ): List<HermeticRoute> {
        val failure = HermeticTestServer.text(
            body = "missing",
            status = 404,
            contentType = HLS_CONTENT_TYPE,
        )
        val success = HermeticTestServer.text(
            body = fixtureText(FixtureId.HlsMaster),
            contentType = HLS_CONTENT_TYPE,
        )
        val retrySequence = HermeticTestServer.sequence(
            *(List(RETRY_FAILURE_REQUESTS) { failure } + success).toTypedArray(),
        )
        return apiRoutes(
            itemId = itemId,
            details = movieDetails(itemId, watchingTime = 0, media = media),
        ) + mediaRoutes(media.high, masterResponse = retrySequence) + mediaRoutes(media.low)
    }

    fun seriesRoutes(
        itemId: Int,
        media: SeriesMediaRoots,
    ): List<HermeticRoute> =
        apiRoutes(
            itemId = itemId,
            details = seriesDetails(itemId, media),
        ) + mediaRoutes(media.episodeOne) + mediaRoutes(media.episodeTwo)

    private fun apiRoutes(
        itemId: Int,
        details: String,
    ): List<HermeticRoute> = listOf(
        server.route(
            id = "details-$itemId",
            path = "/v1/items/$itemId",
            response = HermeticTestServer.text(
                body = details,
                contentType = "application/json; charset=utf-8",
            ),
            required = true,
        ),
        server.route(
            id = "marktime-$itemId",
            path = "/v1/watching/marktime",
            query = mapOf("id" to itemId.toString()),
            queryMode = QueryMatchMode.Contains,
            response = HermeticTestServer.text(
                body = """{"id":$itemId,"status":0,"time":1}""",
                contentType = "application/json; charset=utf-8",
            ),
        ),
        server.route(
            id = "toggle-$itemId",
            path = "/v1/watching/toggle",
            query = mapOf("id" to itemId.toString()),
            queryMode = QueryMatchMode.Contains,
            response = HermeticTestServer.text(
                body = """{"status":1,"watched":1}""",
                contentType = "application/json; charset=utf-8",
            ),
        ),
    )

    private fun mediaRoutes(
        root: String,
        masterResponse: ResponsePlan = HermeticTestServer.text(
            body = fixtureText(FixtureId.HlsMaster),
            contentType = HLS_CONTENT_TYPE,
        ),
    ): List<HermeticRoute> {
        val routes = mutableListOf(
            server.route(
                id = mediaRouteId(root, "master"),
                path = "$root/master.m3u8",
                queryMode = QueryMatchMode.Contains,
                response = masterResponse,
            ),
            server.route(
                id = mediaRouteId(root, "video-360"),
                path = "$root/video_360.m3u8",
                response = HermeticTestServer.text(
                    body = fixtureText(FixtureId.HlsVideo360Playlist),
                    contentType = HLS_CONTENT_TYPE,
                ),
            ),
            server.route(
                id = mediaRouteId(root, "video-720"),
                path = "$root/video_720.m3u8",
                response = HermeticTestServer.text(
                    body = fixtureText(FixtureId.HlsVideo720Playlist),
                    contentType = HLS_CONTENT_TYPE,
                ),
            ),
            server.route(
                id = mediaRouteId(root, "audio-en"),
                path = "$root/audio_english.m3u8",
                response = HermeticTestServer.text(
                    body = fixtureText(FixtureId.HlsAudioEnglishPlaylist),
                    contentType = HLS_CONTENT_TYPE,
                ),
            ),
            server.route(
                id = mediaRouteId(root, "audio-es"),
                path = "$root/audio_spanish.m3u8",
                response = HermeticTestServer.text(
                    body = fixtureText(FixtureId.HlsAudioSpanishPlaylist),
                    contentType = HLS_CONTENT_TYPE,
                ),
            ),
            server.route(
                id = mediaRouteId(root, "subtitle"),
                path = "$root/subtitle.vtt",
                queryMode = QueryMatchMode.Contains,
                response = HermeticTestServer.text(
                    body = fixtureText(FixtureId.SubtitleWebVtt),
                    contentType = "text/vtt",
                ),
            ),
        )
        listOf(
            "video_360_000.ts",
            "video_360_001.ts",
            "video_720_000.ts",
            "video_720_001.ts",
            "audio_english_000.ts",
            "audio_english_001.ts",
            "audio_english_002.ts",
            "audio_spanish_000.ts",
            "audio_spanish_001.ts",
            "audio_spanish_002.ts",
        ).forEach { filename ->
            routes += server.route(
                id = mediaRouteId(root, filename),
                path = "$root/$filename",
                response = HermeticTestServer.bytes(
                    body = PlayerTestFixtures.openPath("hls/$filename", context).use { it.readBytes() },
                    contentType = "video/mp2t",
                ),
            )
        }
        return routes
    }

    private fun movieDetails(
        itemId: Int,
        watchingTime: Int,
        media: MovieMediaRoots,
    ): String =
        """
        {
          "item": {
            "id": $itemId,
            "title": "Synthetic movie",
            "type": "${ItemType.MOVIE.value}",
            "videos": [{
              "id": ${itemId + 1},
              "number": 1,
              "duration": 4,
              "files": [
                {
                  "url": {"hls": "${mediaUrl("${media.high}/master.m3u8?scenario=$scenarioToken")}"},
                  "quality": "$HIGH_QUALITY_LABEL",
                  "quality_id": 2,
                  "w": 1280,
                  "h": 720
                },
                {
                  "url": {"hls": "${mediaUrl("${media.low}/master.m3u8?scenario=$scenarioToken")}"},
                  "quality": "$LOW_QUALITY_LABEL",
                  "quality_id": 1,
                  "w": 640,
                  "h": 360
                }
              ],
              "subtitles": [{
                "lang": "$SUBTITLE_LANGUAGE",
                "url": "${mediaUrl(
                    "${media.high}/subtitle.vtt?signature=screen-test&scenario=$scenarioToken",
                )}"
              }],
              "watching": {"time": $watchingTime, "duration": 4, "status": 0}
            }]
          }
        }
        """.trimIndent()

    private fun seriesDetails(
        itemId: Int,
        media: SeriesMediaRoots,
    ): String =
        """
        {
          "item": {
            "id": $itemId,
            "title": "Synthetic series",
            "type": "${ItemType.SERIAL.value}",
            "seasons": [{
              "id": ${itemId + 10},
              "number": 1,
              "episodes": [
                {
                  "id": ${itemId + EPISODE_ONE_ID_OFFSET},
                  "number": 1,
                  "title": "First episode",
                  "duration": 4,
                  "files": [{
                    "url": {"hls": "${mediaUrl("${media.episodeOne}/master.m3u8?scenario=$scenarioToken")}"},
                    "quality": "$HIGH_QUALITY_LABEL",
                    "quality_id": 2
                  }],
                  "watching": {"time": 0, "duration": 4, "status": 0}
                },
                {
                  "id": ${itemId + EPISODE_TWO_ID_OFFSET},
                  "number": 2,
                  "title": "Second episode",
                  "duration": 4,
                  "files": [{
                    "url": {"hls": "${mediaUrl("${media.episodeTwo}/master.m3u8?scenario=$scenarioToken")}"},
                    "quality": "$HIGH_QUALITY_LABEL",
                    "quality_id": 2
                  }],
                  "watching": {"time": 0, "duration": 4, "status": 0}
                }
              ]
            }]
          }
        }
        """.trimIndent()

    private fun fixtureText(fixture: FixtureId): String =
        PlayerTestFixtures.readBytes(fixture, context).toString(Charsets.UTF_8)

    private fun mediaUrl(path: String): String =
        server.url(path).replace("http://localhost:", "http://127.0.0.1:")

    private fun mediaRouteId(root: String, leaf: String): String =
        "${root.trim('/').replace('/', '-')}:$leaf"

    private fun <T> onMain(block: () -> T): T {
        val result = AtomicReference<Result<T>?>(null)
        instrumentation.runOnMainSync {
            result.set(runCatching(block))
        }
        return requireNotNull(result.get()).getOrThrow()
    }

}

private data class MovieMediaRoots(val itemId: Int) {
    val high = "/media/movie-$itemId/high"
    val low = "/media/movie-$itemId/low"
}

private data class SeriesMediaRoots(val itemId: Int) {
    val episodeOne = "/media/series-$itemId/episode-1"
    val episodeTwo = "/media/series-$itemId/episode-2"
}

private const val HLS_CONTENT_TYPE = "application/vnd.apple.mpegurl"
private const val PLAYER_TIMEOUT_MS = 30_000L
private const val FOCUS_TIMEOUT_MS = 5_000L
private const val POSITION_FENCE_MS = 500L
private const val SAVED_POSITION_SECONDS = 2
private const val SAVED_POSITION_FENCE_MS = 1_750L
private const val MIN_SAVED_TIME_SECONDS = 1
private const val SEEK_STEP_SECONDS = 10
private const val RETRY_FAILURE_REQUESTS = 6
private const val FULLSCREEN_FOCUS_FRACTION = 0.8f
private const val ENGLISH_AUDIO_LABEL = "English"
private const val SPANISH_AUDIO_LABEL = "Español"
private const val SPANISH_LANGUAGE = "es"
private const val SUBTITLE_LANGUAGE = "en"
private const val HIGH_QUALITY_LABEL = "720p"
private const val LOW_QUALITY_LABEL = "360p"
private const val EPISODE_ONE_ID_OFFSET = 11
private const val EPISODE_TWO_ID_OFFSET = 12
private const val HOST_TEXT = "Player E2E host"

@Parcelize
private data object PlayerE2EHostScreen : PuberScreen {
    @Composable
    override fun Content() {
        Text(HOST_TEXT)
    }
}

private fun View.findPlayerView(): PlayerView? {
    if (this is PlayerView) return this
    if (this !is ViewGroup) return null
    repeat(childCount) { index ->
        getChildAt(index).findPlayerView()?.let { return it }
    }
    return null
}

private object RecordingAppLauncher : AppLauncher {
    val finishCount = AtomicInteger()

    fun reset() {
        finishCount.set(0)
    }

    override fun restart() = Unit

    override fun finish() {
        finishCount.incrementAndGet()
    }

    override fun bind(activity: Activity) = Unit

    override fun unbind() = Unit
}
