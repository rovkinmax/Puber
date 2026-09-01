package com.kino.puber.ui.feature.player.component

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
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
import com.kino.puber.ui.feature.player.PlayerInstrumentationTestCase
import com.kino.puber.ui.feature.player.vm.ControlsStateMachine
import com.kino.puber.ui.feature.player.vm.PlaybackIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val AUDIO_BUTTON = "Аудио и субтитры"
private const val VIDEO_BUTTON = "Видео"
private const val EPISODES_BUTTON = "Серии"
private const val SOUND_ITEM = "Стерео 2.0"
private const val QUALITY_ITEM = "1080p"
private const val EPISODE_ITEM = "1. Первое включение"
private const val MARK_WATCHED_BUTTON = "Просмотрено"

internal class PlayerScreenContentFocusTest : PlayerInstrumentationTestCase() {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun initialControlsBackMovesFocusToPlayer_andDpadDownReopensFirstButton() {
        val harness = render()

        assertExactlyOneFocusedNode()
        assertFocusedControl()
        assertPlayerAnchorFocusable(expected = false)
        assertFocusSurvivesRecomposition(harness, ::assertFocusedControl)

        sendKey(KeyEvent.KEYCODE_BACK)
        waitForControlsToBeDisposed()

        assertEquals(0, harness.exitRequestCount)
        assertExactlyOneFocusedNode()
        assertFocusedPlayerSurface()
        assertPlayerAnchorFocusable(expected = true)
        assertHiddenControlsAreNotFocused()

        sendKey(KeyEvent.KEYCODE_DPAD_DOWN)

        assertExactlyOneFocusedNode()
        assertFocusedControl()
        assertControlsVisible(harness)
    }

    @Test
    fun playerToControlsHandoff_keepsPlayerFocused_untilDelayedControlFocusSucceeds() {
        val harness = render()
        hideControls(harness)
        composeRule.mainClock.autoAdvance = false

        try {
            sendKeyWithoutSettling(KeyEvent.KEYCODE_DPAD_DOWN)
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()

            assertTrue(harness.content.controlsVisible)
            assertExactlyOneFocusedNode()
            assertFocusedPlayerSurface()
            assertPlayerAnchorFocusable(expected = true)

            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()

            assertExactlyOneFocusedNode()
            assertFocusedControl()
            assertHiddenPlayerIsNotFocused()

            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()

            assertExactlyOneFocusedNode()
            assertFocusedControl()
            assertPlayerAnchorFocusable(expected = false)
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun resumeDialogOwnsFocus_thenSelectionTransfersToPlayerAnchor() {
        val harness = render(content(resumeDialog = resumeDialog()))

        composeRule.onNodeWithText("Продолжить").assertIsFocused()
        assertExactlyOneFocusedNode()
        assertPlayerAnchorFocusable(expected = false)
        assertFocusSurvivesRecomposition(harness) {
            composeRule.onNodeWithText("Продолжить").assertIsFocused()
        }

        composeRule.onNodeWithText("Продолжить").performKeyInput {
            keyDown(androidx.compose.ui.input.key.Key.Enter)
            keyUp(androidx.compose.ui.input.key.Key.Enter)
        }
        composeRule.waitForIdle()

        assertTrue(harness.content.resumeDialog == null)
        assertExactlyOneFocusedNode()
        assertFocusedPlayerSurface()
        assertPlayerAnchorFocusable(expected = true)
    }

    @Test
    fun hiddenPlayer_dpadUpReopensSeekBar_andDpadDownReopensButtons() {
        val harness = render()
        hideControls(harness)

        sendKey(KeyEvent.KEYCODE_DPAD_UP)
        assertExactlyOneFocusedNode()
        assertFocusedSeekBar()
        assertEquals(listOf(FocusTarget.SeekBar), harness.revealedTargets)

        hideControls(harness)
        sendKey(KeyEvent.KEYCODE_DPAD_DOWN)
        assertExactlyOneFocusedNode()
        assertFocusedControl()
        assertEquals(
            listOf(FocusTarget.SeekBar, FocusTarget.Buttons),
            harness.revealedTargets,
        )
    }

    @Test
    fun hiddenPlayer_directionalSeekPerformsOneAction_andFocusesSeekBar() {
        val harness = render()
        hideControls(harness)

        sendKey(KeyEvent.KEYCODE_DPAD_LEFT)
        assertExactlyOneFocusedNode()
        assertFocusedSeekBar()
        assertEquals(1, harness.seekBackwardCount)
        assertEquals(0, harness.seekForwardCount)
        assertEquals(listOf(FocusTarget.SeekBar), harness.revealedTargets)

        hideControls(harness)
        sendKey(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertExactlyOneFocusedNode()
        assertFocusedSeekBar()
        assertEquals(1, harness.seekBackwardCount)
        assertEquals(1, harness.seekForwardCount)
        assertEquals(
            listOf(FocusTarget.SeekBar, FocusTarget.SeekBar),
            harness.revealedTargets,
        )
    }

    @Test
    fun hiddenPlayer_dpadCenterPerformsOnePlaybackAction_andFocusesFirstButton() {
        val harness = render()
        hideControls(harness)

        sendKey(KeyEvent.KEYCODE_DPAD_CENTER)

        assertExactlyOneFocusedNode()
        assertFocusedControl()
        assertEquals(1, harness.togglePlayPauseCount)
        assertEquals(listOf(FocusTarget.Buttons), harness.revealedTargets)
    }

    @Test
    fun hiddenPlayer_enterPerformsOnePlaybackAction_andFocusesFirstButton() {
        val harness = render()
        hideControls(harness)

        sendKey(KeyEvent.KEYCODE_ENTER)

        assertExactlyOneFocusedNode()
        assertFocusedControl()
        assertEquals(1, harness.togglePlayPauseCount)
        assertEquals(listOf(FocusTarget.Buttons), harness.revealedTargets)
    }

    @Test
    fun hiddenPlayer_mediaSeekAndPlaybackKeysPerformOneAction_andKeepTargetContract() {
        val harness = render()

        hideControls(harness)
        sendKey(KeyEvent.KEYCODE_MEDIA_REWIND)
        assertFocusedSeekBar()
        assertEquals(1, harness.seekBackwardCount)
        assertEquals(1, harness.revealedTargets.size)
        assertEquals(FocusTarget.SeekBar, harness.revealedTargets.single())

        hideControls(harness)
        sendKey(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
        assertFocusedSeekBar()
        assertEquals(1, harness.seekForwardCount)

        hideControls(harness)
        sendKey(KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD)
        assertFocusedSeekBar()
        assertEquals(2, harness.seekBackwardCount)

        hideControls(harness)
        sendKey(KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD)
        assertFocusedSeekBar()
        assertEquals(2, harness.seekForwardCount)

        hideControls(harness)
        sendKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        assertFocusedControl()
        assertEquals(1, harness.togglePlayPauseCount)
        assertEquals(FocusTarget.Buttons, harness.revealedTargets.last())

        hideControls(harness)
        sendKey(KeyEvent.KEYCODE_MEDIA_PLAY)
        assertFocusedControl()
        assertEquals(2, harness.togglePlayPauseCount)

        hideControls(harness)
        sendKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
        assertFocusedControl()
        assertEquals(3, harness.togglePlayPauseCount)
    }

    @Test
    fun controlsTraversal_opensAudioPanel_andBackRestoresAudioButton() {
        val harness = render()

        focusButton(AUDIO_BUTTON, steps = 1)
        sendKey(KeyEvent.KEYCODE_ENTER)

        composeRule.onNodeWithText(SOUND_ITEM).assertIsFocused()
        assertExactlyOneFocusedNode()
        assertPlayerAnchorFocusable(expected = false)
        composeRule.onNodeWithText(AUDIO_BUTTON).assertDoesNotExist()
        assertFocusSurvivesRecomposition(harness) {
            composeRule.onNodeWithText(SOUND_ITEM).assertIsFocused()
        }

        sendKey(KeyEvent.KEYCODE_BACK)

        assertExactlyOneFocusedNode()
        waitUntilFocusedText(AUDIO_BUTTON)
        assertEquals(ActivePanel.None, harness.content.activePanel)
    }

    @Test
    fun controlsTraversal_opensVideoPanel_andBackRestoresVideoButton() {
        val harness = render()

        focusButton(VIDEO_BUTTON, steps = 2)
        sendKey(KeyEvent.KEYCODE_ENTER)

        composeRule.onNodeWithText(QUALITY_ITEM).assertIsFocused()
        assertExactlyOneFocusedNode()
        assertPlayerAnchorFocusable(expected = false)

        sendKey(KeyEvent.KEYCODE_BACK)

        assertExactlyOneFocusedNode()
        waitUntilFocusedText(VIDEO_BUTTON)
        assertEquals(ActivePanel.None, harness.content.activePanel)
    }

    @Test
    fun seriesEpisodesPanel_focusesCurrentEpisode_andBackRestoresEpisodesButton() {
        val harness = render(content(isMovie = false))

        focusButton(EPISODES_BUTTON, steps = 1)
        sendKey(KeyEvent.KEYCODE_ENTER)

        composeRule.onNodeWithText(EPISODE_ITEM).assertIsFocused()
        assertExactlyOneFocusedNode()
        assertPlayerAnchorFocusable(expected = false)

        sendKey(KeyEvent.KEYCODE_BACK)

        assertExactlyOneFocusedNode()
        waitUntilFocusedText(EPISODES_BUTTON)
        assertEquals(ActivePanel.None, harness.content.activePanel)
    }

    @Test
    fun controlsTraversal_movesFromSeekBarToButtonRow_withOneFocusedNode() {
        val harness = render()
        hideControls(harness)

        sendKey(KeyEvent.KEYCODE_DPAD_UP)
        assertFocusedSeekBar()

        sendKey(KeyEvent.KEYCODE_DPAD_DOWN)
        assertExactlyOneFocusedNode()
        assertFocusedControl()
        assertTrue(harness.content.controlsVisible)
    }

    @Test
    fun playbackEndedReveal_focusesFirstButton_andPlayerAnchorLosesFocus() {
        val harness = render(content(isMovie = false))
        hideControls(harness)

        harness.revealControls(FocusTarget.Buttons)

        assertExactlyOneFocusedNode()
        assertFocusedControl()
        assertHiddenPlayerIsNotFocused()
        assertEquals(FocusTarget.Buttons, harness.content.controlsFocusTarget)
    }

    @Test
    fun hiddenDisabledExitingAndDetachedControls_neverOwnFocus() {
        val harness = render(
            content(
                canMarkCurrentWatched = true,
                isMarkCurrentWatchedInFlight = true,
            ),
        )
        val disabledControl = composeRule
            .onNodeWithText(MARK_WATCHED_BUTTON, useUnmergedTree = true)
            .onParent()
            .onParent()

        disabledControl.assertIsNotEnabled()
        disabledControl.assertIsNotFocused()
        assertExactlyOneFocusedNode()
        assertFocusedControl()

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle {
            harness.onAction(PlayerAction.HideControls)
        }
        composeRule.mainClock.advanceTimeBy(FOCUS_TRANSFER_SETTLE_MS)
        composeRule.waitForIdle()

        assertFalse(harness.content.controlsVisible)
        composeRule.onNodeWithText(AUDIO_BUTTON).assertExists().assertIsNotFocused()
        disabledControl.assertExists().assertIsNotEnabled().assertIsNotFocused()
        assertExactlyOneFocusedNode()
        assertFocusedPlayerSurface()

        composeRule.mainClock.advanceTimeBy(CONTROLS_EXIT_SETTLE_MS)
        composeRule.waitForIdle()

        composeRule.onNodeWithText(AUDIO_BUTTON).assertDoesNotExist()
        composeRule.onNodeWithText(MARK_WATCHED_BUTTON).assertDoesNotExist()
        assertExactlyOneFocusedNode()
        assertFocusedPlayerSurface()
        composeRule.mainClock.autoAdvance = true
    }

    @Test
    fun hiddenSecondBack_exitsOnce_andRecompositionKeepsPlayerFocus() {
        val harness = render()
        hideControls(harness)

        assertFocusedPlayerSurface()
        assertFocusSurvivesRecomposition(harness, ::assertFocusedPlayerSurface)

        sendKey(KeyEvent.KEYCODE_BACK)
        assertEquals(1, harness.exitRequestCount)
        assertExactlyOneFocusedNode()

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()
        assertExactlyOneFocusedNode()
        assertFocusedPlayerSurface()

        sendKey(KeyEvent.KEYCODE_DPAD_DOWN)
        assertFocusedControl()
        assertEquals(FocusTarget.Buttons, harness.revealedTargets.last())
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
        assertFocusedPlayerSurface()
    }

    private fun focusButton(text: String, steps: Int) {
        repeat(steps) {
            sendKey(KeyEvent.KEYCODE_DPAD_RIGHT)
        }
        onPlayerScreen(composeRule) {
            focusedText(text).assertIsFocused()
        }
    }

    private fun assertFocusSurvivesRecomposition(
        harness: PlayerHarness,
        assertFocusedOwner: () -> Unit,
    ) {
        harness.bumpPosition()
        composeRule.waitForIdle()
        assertExactlyOneFocusedNode()
        assertFocusedOwner()
    }

    private fun assertExactlyOneFocusedNode() {
        assertEquals(
            "PlayerScreen must have exactly one focused semantics node",
            1,
            composeRule.onAllNodes(isFocused(), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .size,
        )
    }

    private fun assertFocusedPlayerSurface() {
        onPlayerScreen(composeRule) {
            playerSurface.assertIsDisplayed()
            playerSurface.assertIsFocused()
        }
        val focusedNode = focusedNode()
        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Focused player anchor must cover the root width",
            focusedNode.boundsInRoot.width >= rootBounds.width * 0.9f,
        )
        assertTrue(
            "Focused player anchor must cover the root height",
            focusedNode.boundsInRoot.height >= rootBounds.height * 0.9f,
        )
    }

    private fun assertFocusedSeekBar() {
        onPlayerScreen(composeRule) {
            seekBar.assertIsDisplayed()
            seekBar.assertIsFocused()
        }
        val focusedNode = focusedNode()
        val rootWidth = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.width
        assertTrue(
            "Seek bar must be the focused wide control: node=${focusedNode.boundsInRoot}, root=$rootWidth",
            focusedNode.boundsInRoot.width >= rootWidth * 0.5f,
        )
        assertTrue(
            "Seek bar must not be the full-screen player anchor: node=${focusedNode.boundsInRoot}, root=$rootWidth",
            focusedNode.boundsInRoot.width < rootWidth * 0.9f,
        )
    }

    private fun assertFocusedControl() {
        onPlayerScreen(composeRule) {
            playPauseButton.assertIsDisplayed()
            playPauseButton.assertIsFocused()
        }
        waitUntilFocusedTarget { node ->
            val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
            node.boundsInRoot.width < rootBounds.width * 0.5f &&
                node.boundsInRoot.height < rootBounds.height * 0.9f
        }
        val focusedNode = focusedNode()
        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Control focus must not remain on the full-screen player anchor",
            focusedNode.boundsInRoot.width < rootBounds.width * 0.5f &&
                focusedNode.boundsInRoot.height < rootBounds.height * 0.9f,
        )
    }

    private fun assertHiddenControlsAreNotFocused() {
        composeRule.onNodeWithText(AUDIO_BUTTON).assertDoesNotExist()
        composeRule.onNodeWithText(VIDEO_BUTTON).assertDoesNotExist()
    }

    private fun assertHiddenPlayerIsNotFocused() {
        val focusedNode = focusedNode()
        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        assertFalse(
            "Player anchor must lose focus after controls reopen",
            focusedNode.boundsInRoot.width >= rootBounds.width * 0.9f,
        )
    }

    private fun assertPlayerAnchorFocusable(expected: Boolean) {
        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val fullScreenFocusableCount = composeRule
            .onAllNodes(isFocusable(), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .count { node ->
                node.boundsInRoot.width >= rootBounds.width * 0.9f &&
                    node.boundsInRoot.height >= rootBounds.height * 0.9f
            }
        assertEquals(
            "Only the typed Player owner may expose the full-screen focus anchor",
            if (expected) 1 else 0,
            fullScreenFocusableCount,
        )
    }

    private fun focusedNode() = composeRule
        .onAllNodes(isFocused(), useUnmergedTree = true)
        .fetchSemanticsNodes()
        .single()

    private fun waitUntilFocusedTarget(predicate: (androidx.compose.ui.semantics.SemanticsNode) -> Boolean) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(isFocused(), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .singleOrNull()
                ?.let(predicate) == true
        }
    }

    private fun waitUntilFocusedText(text: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithText(text)
                    .fetchSemanticsNode()
                    .config
                    .getOrNull(SemanticsProperties.Focused) == true
            }.getOrDefault(false)
        }
        onPlayerScreen(composeRule) {
            focusedText(text).assertIsFocused()
        }
    }

    private fun assertControlsVisible(harness: PlayerHarness) {
        assertTrue(harness.content.controlsVisible)
        onPlayerScreen(composeRule) {
            text(AUDIO_BUTTON).assertIsDisplayed()
        }
    }

    private fun waitForControlsToBeDisposed() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(
                hasText(AUDIO_BUTTON),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun sendKey(keyCode: Int) {
        sendKeyWithoutSettling(keyCode)
        composeRule.waitForIdle()
    }

    private fun sendKeyWithoutSettling(keyCode: Int) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_UP, keyCode))
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
        subtitleTracks = listOf(SubtitleTrackUIState(0, "Выкл.", "", "")),
        selectedSubtitleIndex = 0,
        soundModes = listOf(SoundModeUIState(0, SOUND_ITEM)),
        selectedSoundModeIndex = 0,
        subtitleSize = SubtitleSize.MEDIUM,
        qualities = listOf(QualityUIState(0, QUALITY_ITEM, 1080, 1920, 1080)),
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
                        title = EPISODE_ITEM,
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
        const val FOCUS_TRANSFER_SETTLE_MS = 64L
        const val CONTROLS_EXIT_SETTLE_MS = 1_000L
    }
}
