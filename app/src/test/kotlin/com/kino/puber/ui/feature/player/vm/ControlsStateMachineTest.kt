package com.kino.puber.ui.feature.player.vm

import com.kino.puber.ui.feature.player.model.ActivePanel
import com.kino.puber.ui.feature.player.model.FocusTarget
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ControlsStateMachineTest {

    private lateinit var machine: ControlsStateMachine

    @BeforeEach
    fun setUp() {
        machine = ControlsStateMachine()
    }

    // ------------------------------------------------------------------
    // initialize
    // ------------------------------------------------------------------

    @Test
    fun `initialize_withoutResumeDialog_showsInitialButtons`() {
        machine.initialize(resumeDialogVisible = false)

        assertTrue(machine.state.controlsVisible)
        assertEquals(FocusTarget.Buttons, machine.state.focusTarget)
        assertEquals(ActivePanel.None, machine.state.activePanel)
    }

    @Test
    fun `initialize_withResumeDialog_keepsControlsHidden`() {
        machine.initialize(resumeDialogVisible = true)

        assertFalse(machine.state.controlsVisible)
        assertEquals(null, machine.state.focusTarget)
        assertEquals(ActivePanel.None, machine.state.activePanel)
    }

    // ------------------------------------------------------------------
    // showControls
    // ------------------------------------------------------------------

    @Test
    fun `showControls_setsVisible_returnsScheduleHide`() {
        val effects = machine.showControls(FocusTarget.Buttons)

        assertTrue(machine.state.controlsVisible)
        assertTrue(effects.contains(ControlsStateMachine.Effect.ScheduleHide))
    }

    @Test
    fun `showControls_setsFocusTarget`() {
        machine.showControls(FocusTarget.SeekBar)

        assertEquals(FocusTarget.SeekBar, machine.state.focusTarget)
    }

    @Test
    fun `showControlsPersistently_setsTargetAndCancelsOrdinaryHide`() {
        val effects = machine.showControlsPersistently(FocusTarget.Buttons)

        assertTrue(machine.state.controlsVisible)
        assertEquals(FocusTarget.Buttons, machine.state.focusTarget)
        assertEquals(listOf(ControlsStateMachine.Effect.CancelHide), effects)
    }

    // ------------------------------------------------------------------
    // hideControls
    // ------------------------------------------------------------------

    @Test
    fun `hideControls_setsInvisible_returnsCancelHide`() {
        machine.showControls(FocusTarget.Buttons)

        val effects = machine.hideControls()

        assertFalse(machine.state.controlsVisible)
        assertTrue(effects.contains(ControlsStateMachine.Effect.CancelHide))
    }

    // ------------------------------------------------------------------
    // openPanel
    // ------------------------------------------------------------------

    @Test
    fun `openPanel_setsActivePanel`() {
        machine.openPanel(ActivePanel.AudioSubtitles, playbackIntent = PlaybackIntent.Paused)

        assertEquals(ActivePanel.AudioSubtitles, machine.state.activePanel)
    }

    @Test
    fun `openPanel_episodes_pausesPlayback_whenPlaying`() {
        val effects = machine.openPanel(ActivePanel.Episodes, playbackIntent = PlaybackIntent.PlayRequested)

        assertTrue(effects.contains(ControlsStateMachine.Effect.PausePlayback))
    }

    @Test
    fun `openPanel_episodes_alwaysEmitsPausePlayback_evenWhenNotPlaying`() {
        // PausePlayback is always emitted for Episodes panel; wasPlayingBeforePanel
        // controls whether ResumePlayback is emitted on close.
        val effects = machine.openPanel(ActivePanel.Episodes, playbackIntent = PlaybackIntent.Paused)

        assertTrue(effects.contains(ControlsStateMachine.Effect.PausePlayback))
    }

    @Test
    fun `closePanel_afterEpisodes_doesNotResume_whenWasNotPlaying`() {
        machine.openPanel(ActivePanel.Episodes, playbackIntent = PlaybackIntent.Paused)

        val effects = machine.closePanel()

        assertFalse(effects.contains(ControlsStateMachine.Effect.ResumePlayback))
    }

    // ------------------------------------------------------------------
    // closePanel
    // ------------------------------------------------------------------

    @Test
    fun `closePanel_afterEpisodes_resumesPlayback_whenWasPlaying`() {
        machine.openPanel(ActivePanel.Episodes, playbackIntent = PlaybackIntent.PlayRequested)

        val effects = machine.closePanel()

        assertTrue(effects.contains(ControlsStateMachine.Effect.ResumePlayback))
    }

    @Test
    fun `closePanel_afterNonEpisodesPanel_doesNotResumePlayback`() {
        machine.openPanel(ActivePanel.AudioSubtitles, playbackIntent = PlaybackIntent.PlayRequested)

        val effects = machine.closePanel()

        assertFalse(effects.contains(ControlsStateMachine.Effect.ResumePlayback))
    }

    @Test
    fun `closePanel_restoresFocusTarget`() {
        machine.openPanel(ActivePanel.Episodes, playbackIntent = PlaybackIntent.Paused)

        machine.closePanel()

        assertEquals(FocusTarget.EpisodesButton, machine.state.focusTarget)
    }

    @Test
    fun `closePanel_restoresAudioSubtitlesOpener`() {
        machine.openPanel(ActivePanel.AudioSubtitles, playbackIntent = PlaybackIntent.Paused)

        machine.closePanel()

        assertEquals(FocusTarget.AudioSubtitlesButton, machine.state.focusTarget)
    }

    @Test
    fun `closePanel_restoresVideoSettingsOpener`() {
        machine.openPanel(ActivePanel.VideoSettings, playbackIntent = PlaybackIntent.Paused)

        machine.closePanel()

        assertEquals(FocusTarget.VideoSettingsButton, machine.state.focusTarget)
    }

    // ------------------------------------------------------------------
    // handleBack
    // ------------------------------------------------------------------

    @Test
    fun `handleBack_withOpenPanel_closesPanel`() {
        machine.openPanel(ActivePanel.VideoSettings, playbackIntent = PlaybackIntent.Paused)

        machine.handleBack()

        assertEquals(ActivePanel.None, machine.state.activePanel)
    }

    @Test
    fun `handleBack_withControlsVisible_noPanel_hidesControls`() {
        machine.showControls(FocusTarget.Buttons)

        machine.handleBack()

        assertFalse(machine.state.controlsVisible)
    }

    @Test
    fun `handleBack_nothingActive_returnsSaveAndExit`() {
        // Initial state: controls not visible, no panel open.
        val effects = machine.handleBack()

        assertTrue(effects.contains(ControlsStateMachine.Effect.SaveAndExit))
    }

    // ------------------------------------------------------------------
    // applyControlsVisibility
    // ------------------------------------------------------------------

    @Test
    fun `applyControlsVisibility_false_withOpenPanel_doesNotHide`() {
        machine.showControls(FocusTarget.Buttons)
        machine.openPanel(ActivePanel.AudioSubtitles, playbackIntent = PlaybackIntent.Paused)
        // After openPanel, controlsVisible is already false; set it manually via
        // a fresh showControls call is not possible while panel is open, so we
        // verify the guard: calling applyControlsVisibility(false) when panel is
        // open must leave activePanel intact and not crash.
        machine.applyControlsVisibility(false)

        assertEquals(ActivePanel.AudioSubtitles, machine.state.activePanel)
    }

    @Test
    fun `applyControlsVisibility_false_noPanel_hides`() {
        machine.showControls(FocusTarget.Buttons)

        machine.applyControlsVisibility(false)

        assertFalse(machine.state.controlsVisible)
    }
}
