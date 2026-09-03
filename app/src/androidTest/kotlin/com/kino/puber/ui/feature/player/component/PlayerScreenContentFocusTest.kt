package com.kino.puber.ui.feature.player.component

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import com.kino.puber.core.ui.uikit.component.moviesList.VideoGridItemUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoGridUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.domain.model.SubtitleSize
import com.kino.puber.ui.feature.player.model.ActivePanel
import com.kino.puber.ui.feature.player.model.AudioTrackUIState
import com.kino.puber.ui.feature.player.model.BufferPreset
import com.kino.puber.ui.feature.player.model.BufferPresetUIState
import com.kino.puber.ui.feature.player.model.FocusTarget
import com.kino.puber.ui.feature.player.model.PlayerAction
import com.kino.puber.ui.feature.player.model.PlayerContentState
import com.kino.puber.ui.feature.player.model.PlayerViewState
import com.kino.puber.ui.feature.player.model.QualityUIState
import com.kino.puber.ui.feature.player.model.ResumeDialogState
import com.kino.puber.ui.feature.player.model.SoundModeUIState
import com.kino.puber.ui.feature.player.model.SpeedUIState
import com.kino.puber.ui.feature.player.model.SubtitleTrackUIState
import com.kino.puber.ui.feature.player.PlayerComposeInstrumentationTestCase
import com.kino.puber.ui.feature.player.vm.ControlsStateMachine
import com.kino.puber.ui.feature.player.vm.PlaybackIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

internal class PlayerScreenContentFocusTest : PlayerComposeInstrumentationTestCase() {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val robot by lazy { PlayerTvRobot(composeRule) }

    @Test
    fun initialControlsBackMovesFocusToPlayer_andDpadDownReopensFirstButton() = run {
        val harness = render()

        step("Initial controls own the single focus target across recomposition") {
            robot.focus.assertExactlyOneFocusedNode()
            robot.focus.assertFocusedControl()
            robot.focus.assertPlayerAnchorFocusable(expected = false)
            harness.bumpPosition()
            composeRule.waitForIdle()
            robot.focus.assertExactlyOneFocusedNode()
            robot.focus.assertFocusedControl()
        }

        step("Back hides controls and transfers focus to the player surface") {
            robot.pressBack()
            robot.waitForControlsToBeDisposed()
            assertEquals(0, harness.exitRequestCount)
            robot.focus.assertExactlyOneFocusedNode()
            robot.focus.assertFocusedPlayerSurface()
            robot.focus.assertPlayerAnchorFocusable(expected = true)
            robot.focus.assertHiddenControlsAreNotFocused()
        }

        step("D-pad Down reopens controls on the first button") {
            robot.press(PlayerRemoteKey.Down)
            robot.focus.assertExactlyOneFocusedNode()
            robot.focus.assertFocusedControl()
            robot.assertControlsVisible(harness.content.controlsVisible)
        }
    }

    @Test
    fun playerToControlsHandoff_keepsPlayerFocused_untilDelayedControlFocusSucceeds() = run {
        val harness = render()
        hideControls(harness)
        composeRule.mainClock.autoAdvance = false

        try {
            step("D-pad Down reveals controls while the player retains focus") {
                robot.pressWithoutSettling(PlayerRemoteKey.Down)
                robot.advanceOneFrame()
                assertTrue(harness.content.controlsVisible)
                robot.focus.assertExactlyOneFocusedNode()
                robot.focus.assertFocusedPlayerSurface()
                robot.focus.assertPlayerAnchorFocusable(expected = true)
            }

            step("The delayed handoff focuses the first control") {
                robot.advanceOneFrame()
                robot.focus.assertExactlyOneFocusedNode()
                robot.focus.assertFocusedControl()
                robot.focus.assertHiddenPlayerIsNotFocused()
            }

            step("The player anchor becomes non-focusable after handoff") {
                robot.advanceOneFrame()
                robot.focus.assertExactlyOneFocusedNode()
                robot.focus.assertFocusedControl()
                robot.focus.assertPlayerAnchorFocusable(expected = false)
            }
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun resumeDialogOwnsFocus_thenSelectionTransfersToPlayerAnchor() = run {
        val harness = render(content(resumeDialog = resumeDialog()))

        step("The resume dialog owns focus across recomposition") {
            robot.screen {
                focusedResumeButton.assertIsFocused()
            }
            robot.focus.assertExactlyOneFocusedNode()
            robot.focus.assertPlayerAnchorFocusable(expected = false)
            harness.bumpPosition()
            composeRule.waitForIdle()
            robot.focus.assertExactlyOneFocusedNode()
            robot.screen {
                focusedResumeButton.assertIsFocused()
            }
        }

        step("Selecting Continue transfers focus to the player anchor") {
            robot.press(PlayerRemoteKey.Select)
            assertTrue(harness.content.resumeDialog == null)
            robot.focus.assertExactlyOneFocusedNode()
            robot.focus.assertFocusedPlayerSurface()
            robot.focus.assertPlayerAnchorFocusable(expected = true)
        }
    }

    @Test
    fun hiddenPlayer_dpadUpReopensSeekBar_andDpadDownReopensButtons() = run {
        val harness = render()
        hideControls(harness)

        step("D-pad Up reopens controls on the seek bar") {
            robot.press(PlayerRemoteKey.Up)
            robot.focus.assertExactlyOneFocusedNode()
            robot.focus.assertFocusedSeekBar()
            assertEquals(listOf(FocusTarget.SeekBar), harness.revealedTargets)
        }

        step("D-pad Down reopens controls on the first button") {
            hideControls(harness)
            robot.press(PlayerRemoteKey.Down)
            robot.focus.assertExactlyOneFocusedNode()
            robot.focus.assertFocusedControl()
            assertEquals(
                listOf(FocusTarget.SeekBar, FocusTarget.Buttons),
                harness.revealedTargets,
            )
        }
    }

    @Test
    fun hiddenPlayer_directionalSeekPerformsOneAction_andFocusesSeekBar() = run {
        val harness = render()
        hideControls(harness)

        step("D-pad Left performs one backward seek and focuses the seek bar") {
            robot.press(PlayerRemoteKey.Left)
            robot.focus.assertExactlyOneFocusedNode()
            robot.focus.assertFocusedSeekBar()
            assertEquals(1, harness.seekBackwardCount)
            assertEquals(0, harness.seekForwardCount)
            assertEquals(listOf(FocusTarget.SeekBar), harness.revealedTargets)
        }

        step("D-pad Right performs one forward seek and preserves the seek target") {
            hideControls(harness)
            robot.press(PlayerRemoteKey.Right)
            robot.focus.assertExactlyOneFocusedNode()
            robot.focus.assertFocusedSeekBar()
            assertEquals(1, harness.seekBackwardCount)
            assertEquals(1, harness.seekForwardCount)
            assertEquals(
                listOf(FocusTarget.SeekBar, FocusTarget.SeekBar),
                harness.revealedTargets,
            )
        }
    }

    @Test
    fun hiddenPlayer_dpadCenterPerformsOnePlaybackAction_andFocusesFirstButton() = run {
        val harness = render()
        hideControls(harness)

        step("D-pad Center toggles playback once and focuses the first button") {
            robot.press(PlayerRemoteKey.Select)
            robot.focus.assertExactlyOneFocusedNode()
            robot.focus.assertFocusedControl()
            assertEquals(1, harness.togglePlayPauseCount)
            assertEquals(listOf(FocusTarget.Buttons), harness.revealedTargets)
        }
    }

    @Test
    fun hiddenPlayer_enterPerformsOnePlaybackAction_andFocusesFirstButton() = run {
        val harness = render()
        hideControls(harness)

        step("Enter toggles playback once and focuses the first button") {
            robot.press(PlayerRemoteKey.Enter)
            robot.focus.assertExactlyOneFocusedNode()
            robot.focus.assertFocusedControl()
            assertEquals(1, harness.togglePlayPauseCount)
            assertEquals(listOf(FocusTarget.Buttons), harness.revealedTargets)
        }
    }

    @Test
    fun hiddenPlayer_mediaSeekAndPlaybackKeysPerformOneAction_andKeepTargetContract() = run {
        val harness = render()

        step("Media rewind and fast-forward keys seek once and focus the seek bar") {
            hideControls(harness)
            robot.press(PlayerRemoteKey.Rewind)
            robot.focus.assertFocusedSeekBar()
            assertEquals(1, harness.seekBackwardCount)
            assertEquals(1, harness.revealedTargets.size)
            assertEquals(FocusTarget.SeekBar, harness.revealedTargets.single())

            hideControls(harness)
            robot.press(PlayerRemoteKey.FastForward)
            robot.focus.assertFocusedSeekBar()
            assertEquals(1, harness.seekForwardCount)
        }

        step("Media skip keys preserve one seek action per key") {
            hideControls(harness)
            robot.press(PlayerRemoteKey.SkipBackward)
            robot.focus.assertFocusedSeekBar()
            assertEquals(2, harness.seekBackwardCount)

            hideControls(harness)
            robot.press(PlayerRemoteKey.SkipForward)
            robot.focus.assertFocusedSeekBar()
            assertEquals(2, harness.seekForwardCount)
        }

        step("Media playback keys toggle once and focus the first button") {
            hideControls(harness)
            robot.press(PlayerRemoteKey.PlayPause)
            robot.focus.assertFocusedControl()
            assertEquals(1, harness.togglePlayPauseCount)
            assertEquals(FocusTarget.Buttons, harness.revealedTargets.last())

            hideControls(harness)
            robot.press(PlayerRemoteKey.Play)
            robot.focus.assertFocusedControl()
            assertEquals(2, harness.togglePlayPauseCount)

            hideControls(harness)
            robot.press(PlayerRemoteKey.Pause)
            robot.focus.assertFocusedControl()
            assertEquals(3, harness.togglePlayPauseCount)
        }
    }

    @Test
    fun controlsTraversal_opensAudioPanel_andBackRestoresAudioButton() = run {
        val harness = render()

        step("D-pad traversal focuses and opens Audio and subtitles") {
            robot.screen {
                robot.focusButton(focusedAudioSubtitlesButton, dpadRightSteps = 1)
            }
            robot.press(PlayerRemoteKey.Enter)
        }

        step("The sound item owns focus across recomposition") {
            robot.screen {
                focusedPanelItem("sound", 0).assertIsFocused()
                audioSubtitlesButton.assertDoesNotExist()
            }
            robot.focus.assertExactlyOneFocusedNode()
            robot.focus.assertPlayerAnchorFocusable(expected = false)
            harness.bumpPosition()
            composeRule.waitForIdle()
            robot.focus.assertExactlyOneFocusedNode()
            robot.screen {
                focusedPanelItem("sound", 0).assertIsFocused()
            }
        }

        step("Back closes the panel and restores Audio and subtitles focus") {
            robot.pressBack()
            robot.focus.assertExactlyOneFocusedNode()
            robot.screen {
                robot.waitUntilFocused(focusedAudioSubtitlesButton)
            }
            assertEquals(ActivePanel.None, harness.content.activePanel)
        }
    }

    @Test
    fun controlsTraversal_opensVideoPanel_andBackRestoresVideoButton() = run {
        val harness = render()

        step("D-pad traversal focuses and opens Video settings") {
            robot.screen {
                robot.focusButton(focusedVideoSettingsButton, dpadRightSteps = 2)
            }
            robot.press(PlayerRemoteKey.Enter)
        }

        step("The selected quality owns the single panel focus target") {
            robot.screen {
                focusedPanelItem("quality", 0).assertIsFocused()
            }
            robot.focus.assertExactlyOneFocusedNode()
            robot.focus.assertPlayerAnchorFocusable(expected = false)
        }

        step("Back closes the panel and restores Video settings focus") {
            robot.pressBack()
            robot.focus.assertExactlyOneFocusedNode()
            robot.screen {
                robot.waitUntilFocused(focusedVideoSettingsButton)
            }
            assertEquals(ActivePanel.None, harness.content.activePanel)
        }
    }

    @Test
    fun seriesEpisodesPanel_focusesCurrentEpisode_andBackRestoresEpisodesButton() = run {
        val harness = render(content(isMovie = false))

        step("D-pad traversal focuses and opens Episodes") {
            robot.screen {
                robot.focusButton(focusedEpisodesButton, dpadRightSteps = 1)
            }
            robot.press(PlayerRemoteKey.Enter)
        }

        step("The current episode model owns the single panel focus target") {
            robot.screen {
                focusedText(currentEpisodeTitle(harness.content)).assertIsFocused()
            }
            robot.focus.assertExactlyOneFocusedNode()
            robot.focus.assertPlayerAnchorFocusable(expected = false)
        }

        step("Back closes the panel and restores Episodes focus") {
            robot.pressBack()
            robot.focus.assertExactlyOneFocusedNode()
            robot.screen {
                robot.waitUntilFocused(focusedEpisodesButton)
            }
            assertEquals(ActivePanel.None, harness.content.activePanel)
        }
    }

    @Test
    fun controlsTraversal_movesFromSeekBarToButtonRow_withOneFocusedNode() = run {
        val harness = render()
        hideControls(harness)

        step("D-pad Up reopens controls with seek-bar focus") {
            robot.press(PlayerRemoteKey.Up)
            robot.focus.assertFocusedSeekBar()
        }

        step("D-pad Down moves from the seek bar to the first button") {
            robot.press(PlayerRemoteKey.Down)
            robot.focus.assertExactlyOneFocusedNode()
            robot.focus.assertFocusedControl()
            assertTrue(harness.content.controlsVisible)
        }
    }

    @Test
    fun playbackEndedReveal_focusesFirstButton_andPlayerAnchorLosesFocus() = run {
        val harness = render(content(isMovie = false))
        hideControls(harness)

        step("Playback-ended reveal focuses the first button") {
            harness.revealControls(FocusTarget.Buttons)
            robot.focus.assertExactlyOneFocusedNode()
            robot.focus.assertFocusedControl()
            robot.focus.assertHiddenPlayerIsNotFocused()
            assertEquals(FocusTarget.Buttons, harness.content.controlsFocusTarget)
        }
    }

    @Test
    fun hiddenDisabledExitingAndDetachedControls_neverOwnFocus() = run {
        val harness = render(
            content(
                canMarkCurrentWatched = true,
                isMarkCurrentWatchedInFlight = true,
            ),
        )
        val disabledControl = composeRule.onNode(
            hasTestTag(PlayerScreenTestTags.MarkWatched),
            useUnmergedTree = true,
        )

        composeRule.mainClock.autoAdvance = false
        try {
            step("A disabled control never owns initial focus") {
                disabledControl.assertIsNotEnabled()
                disabledControl.assertIsNotFocused()
                robot.focus.assertExactlyOneFocusedNode()
                robot.focus.assertFocusedControl()
            }

            step("Exiting controls transfer focus to the player before disposal") {
                composeRule.runOnIdle {
                    harness.onAction(PlayerAction.HideControls)
                }
                robot.advanceClockBy(FOCUS_TRANSFER_SETTLE_MS)
                assertFalse(harness.content.controlsVisible)
                robot.screen {
                    audioSubtitlesButton.assertExists()
                    focusedAudioSubtitlesButton.assertDoesNotExist()
                }
                disabledControl.assertExists().assertIsNotEnabled().assertIsNotFocused()
                robot.focus.assertExactlyOneFocusedNode()
                robot.focus.assertFocusedPlayerSurface()
            }

            step("Detached controls disappear without stealing player focus") {
                robot.advanceClockBy(CONTROLS_EXIT_SETTLE_MS)
                robot.screen {
                    audioSubtitlesButton.assertDoesNotExist()
                    markWatchedButton.assertDoesNotExist()
                }
                robot.focus.assertExactlyOneFocusedNode()
                robot.focus.assertFocusedPlayerSurface()
            }
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun hiddenSecondBack_exitsOnce_andRecompositionKeepsPlayerFocus() = run {
        val harness = render()
        hideControls(harness)

        step("The hidden player keeps focus across recomposition") {
            robot.focus.assertFocusedPlayerSurface()
            harness.bumpPosition()
            composeRule.waitForIdle()
            robot.focus.assertExactlyOneFocusedNode()
            robot.focus.assertFocusedPlayerSurface()
        }

        step("The second Back exits exactly once") {
            robot.pressBack()
            assertEquals(1, harness.exitRequestCount)
            robot.focus.assertExactlyOneFocusedNode()
        }

        step("Lifecycle resume restores the single player focus owner") {
            composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            composeRule.waitForIdle()
            robot.focus.assertExactlyOneFocusedNode()
            robot.focus.assertFocusedPlayerSurface()
        }

        step("D-pad Down after resume reopens the first button") {
            robot.press(PlayerRemoteKey.Down)
            robot.focus.assertFocusedControl()
            assertEquals(FocusTarget.Buttons, harness.revealedTargets.last())
        }
    }

    private fun render(
        initialContent: PlayerContentState = content(),
    ): PlayerHarness {
        val harness = PlayerHarness(initialContent)
        composeRule.setContent {
            PuberTheme {
                PlayerScreenContent(
                    state = harness.state.value,
                    onAction = harness::onAction,
                    exoPlayer = { null },
                )
            }
        }
        composeRule.runOnIdle { harness.installBackHandler(composeRule.activity) }
        composeRule.waitForIdle()
        return harness
    }

    private fun hideControls(harness: PlayerHarness) {
        harness.onAction(PlayerAction.HideControls)
        composeRule.waitForIdle()
        robot.focus.assertFocusedPlayerSurface()
    }

    private fun content(
        controlsVisible: Boolean = true,
        activePanel: ActivePanel = ActivePanel.None,
        isMovie: Boolean = true,
        resumeDialog: ResumeDialogState? = null,
        canMarkCurrentWatched: Boolean = false,
        isMarkCurrentWatchedInFlight: Boolean = false,
    ) = PlayerContentState(
        title = "Test",
        subtitle = null,
        isPlaying = false,
        playbackIntent = PlaybackIntent.Paused,
        shouldKeepScreenOn = false,
        currentPosition = 30_000L,
        duration = 600_000L,
        bufferedPosition = 60_000L,
        controlsVisible = controlsVisible,
        controlsFocusTarget = if (controlsVisible) FocusTarget.Buttons else null,
        activePanel = activePanel,
        seekIndicator = null,
        playPauseIndicator = null,
        audioTracks = listOf(AudioTrackUIState(0, "Русский", "ru")),
        selectedAudioTrackIndex = 0,
        subtitleTracks = listOf(SubtitleTrackUIState("Выкл.", "", "")),
        selectedSubtitleIndex = 0,
        soundModes = listOf(SoundModeUIState(0, FIXTURE_SOUND_MODE)),
        selectedSoundModeIndex = 0,
        subtitleSize = SubtitleSize.MEDIUM,
        qualities = listOf(
            QualityUIState(0, FIXTURE_QUALITY, 1080, 1920, 1080),
        ),
        selectedQualityIndex = 0,
        speeds = listOf(SpeedUIState(0, "Норм.", 1f)),
        selectedSpeedIndex = 0,
        aspectRatios = emptyList(),
        selectedAspectRatioIndex = 0,
        bufferPresets = listOf(BufferPresetUIState(0, "Авто", BufferPreset.AUTO)),
        selectedBufferPresetIndex = 0,
        isMovie = isMovie,
        hasNextEpisode = false,
        hasPreviousEpisode = false,
        canMarkCurrentWatched = canMarkCurrentWatched,
        isCurrentMediaWatched = false,
        isMarkCurrentWatchedInFlight = isMarkCurrentWatchedInFlight,
        nextEpisodeCountdown = null,
        resumeDialog = resumeDialog,
        episodes = if (isMovie) null else episodes(),
        currentEpisodeId = if (isMovie) null else CURRENT_EPISODE_ID,
    )

    private fun episodes() = VideoGridUIState(
        list = listOf(
            VideoGridItemUIState.Title("1 сезон"),
            VideoGridItemUIState.Items(
                items = listOf(
                    VideoItemUIState(
                        id = CURRENT_EPISODE_ID,
                        title = FIXTURE_EPISODE_TITLE,
                        imageUrl = "",
                        bigImageUrl = "",
                        showTitle = true,
                        seasonNumber = 1,
                        episodeNumber = 1,
                    ),
                ),
                rowKey = "season_1",
            ),
        ),
    )

    private fun resumeDialog() = ResumeDialogState(
        savedPosition = 30_000L,
        formattedTime = "0:30",
        episodeInfo = null,
    )

    private fun currentEpisodeTitle(content: PlayerContentState): String =
        content.episodes
            ?.list
            ?.filterIsInstance<VideoGridItemUIState.Items>()
            ?.flatMap(VideoGridItemUIState.Items::items)
            ?.single { it.id == content.currentEpisodeId }
            ?.title
            ?: error("Missing current episode model ${content.currentEpisodeId}")

    private class PlayerHarness(
        initialContent: PlayerContentState,
    ) {
        val machine = ControlsStateMachine()
        val state: MutableState<PlayerViewState>
        val revealedTargets = mutableListOf<FocusTarget>()
        var seekForwardCount = 0
            private set
        var seekBackwardCount = 0
            private set
        var togglePlayPauseCount = 0
            private set
        var exitRequestCount = 0
            private set

        val content: PlayerContentState
            get() = (state.value as PlayerViewState.Content).content

        init {
            machine.initialize(initialContent.resumeDialog != null)
            state = mutableStateOf(
                PlayerViewState.Content(initialContent.withControlsState(machine.state)),
            )
        }

        fun installBackHandler(activity: ComponentActivity) {
            activity.onBackPressedDispatcher.addCallback(
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        val effects = machine.handleBack()
                        if (effects.any { it is ControlsStateMachine.Effect.SaveAndExit }) {
                            exitRequestCount++
                        } else {
                            publishMachineState()
                        }
                    }
                },
            )
        }

        fun onAction(action: UIAction) {
            when (action) {
                is PlayerAction.ShowControls -> revealControls(action.focusTarget)
                PlayerAction.HideControls -> {
                    machine.hideControls()
                    publishMachineState()
                }
                PlayerAction.SeekForward -> seekForwardCount++
                PlayerAction.SeekBackward -> seekBackwardCount++
                PlayerAction.TogglePlayPause -> togglePlayPauseCount++
                PlayerAction.OpenAudioSubtitlesPanel -> openPanel(ActivePanel.AudioSubtitles)
                PlayerAction.OpenVideoSettingsPanel -> openPanel(ActivePanel.VideoSettings)
                PlayerAction.OpenEpisodesPanel -> openPanel(ActivePanel.Episodes)
                PlayerAction.ClosePanel -> {
                    machine.closePanel()
                    publishMachineState()
                }
                PlayerAction.ResumeFromPosition,
                PlayerAction.StartFromBeginning -> {
                    state.value = PlayerViewState.Content(
                        content.copy(resumeDialog = null),
                    )
                }
                else -> Unit
            }
        }

        fun revealControls(target: FocusTarget) {
            machine.showControls(target)
            revealedTargets += target
            publishMachineState()
        }

        fun bumpPosition() {
            state.value = PlayerViewState.Content(
                content.copy(currentPosition = content.currentPosition + 1),
            )
        }

        private fun openPanel(panel: ActivePanel) {
            machine.openPanel(panel, content.playbackIntent)
            publishMachineState()
        }

        private fun publishMachineState() {
            state.value = PlayerViewState.Content(
                content.withControlsState(machine.state),
            )
        }

        private fun PlayerContentState.withControlsState(
            machineState: ControlsStateMachine.State,
        ) = copy(
            controlsVisible = machineState.controlsVisible,
            controlsFocusTarget = machineState.focusTarget,
            activePanel = machineState.activePanel,
        )
    }

    private companion object {
        const val CURRENT_EPISODE_ID = 101
        const val FIXTURE_SOUND_MODE = "Fixture sound mode"
        const val FIXTURE_QUALITY = "Fixture quality"
        const val FIXTURE_EPISODE_TITLE = "Fixture episode"
        const val FOCUS_TRANSFER_SETTLE_MS = 64L
        const val CONTROLS_EXIT_SETTLE_MS = 1_000L
    }
}
