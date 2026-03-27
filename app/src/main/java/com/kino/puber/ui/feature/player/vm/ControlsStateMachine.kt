package com.kino.puber.ui.feature.player.vm

import com.kino.puber.ui.feature.player.model.ActivePanel
import com.kino.puber.ui.feature.player.model.FocusTarget

internal class ControlsStateMachine {

    data class State(
        val controlsVisible: Boolean = false,
        val focusTarget: FocusTarget? = null,
        val activePanel: ActivePanel = ActivePanel.None,
    )

    sealed class Effect {
        data object ScheduleHide : Effect()
        data object CancelHide : Effect()
        data object PausePlayback : Effect()
        data object ResumePlayback : Effect()
        data object SaveAndExit : Effect()
    }

    var state = State()
        private set

    private var lastPanelOpener: FocusTarget = FocusTarget.Buttons
    private var wasPlayingBeforePanel = false

    fun showControls(focusTarget: FocusTarget): List<Effect> {
        state = state.copy(controlsVisible = true, focusTarget = focusTarget)
        return listOf(Effect.ScheduleHide)
    }

    fun hideControls(): List<Effect> {
        state = state.copy(controlsVisible = false, focusTarget = null)
        return listOf(Effect.CancelHide)
    }

    fun openPanel(panel: ActivePanel, isPlaying: Boolean): List<Effect> {
        lastPanelOpener = when (panel) {
            ActivePanel.Episodes -> FocusTarget.EpisodesButton
            ActivePanel.AudioSubtitles -> FocusTarget.AudioSubtitlesButton
            ActivePanel.VideoSettings -> FocusTarget.VideoSettingsButton
            ActivePanel.None -> FocusTarget.Buttons
        }

        val effects = mutableListOf<Effect>(Effect.CancelHide)

        if (panel == ActivePanel.Episodes) {
            wasPlayingBeforePanel = isPlaying
            effects.add(Effect.PausePlayback)
        }

        state = state.copy(activePanel = panel, controlsVisible = false, focusTarget = null)
        return effects
    }

    fun closePanel(): List<Effect> {
        val effects = mutableListOf<Effect>()

        if (state.activePanel == ActivePanel.Episodes && wasPlayingBeforePanel) {
            effects.add(Effect.ResumePlayback)
        }

        state = state.copy(
            activePanel = ActivePanel.None,
            controlsVisible = true,
            focusTarget = lastPanelOpener,
        )
        effects.add(Effect.ScheduleHide)
        return effects
    }

    fun handleBack(): List<Effect> {
        return when {
            state.activePanel != ActivePanel.None -> closePanel()
            state.controlsVisible -> hideControls()
            else -> listOf(Effect.SaveAndExit)
        }
    }

    fun applyControlsVisibility(visible: Boolean) {
        if (!visible && state.activePanel == ActivePanel.None) {
            state = state.copy(controlsVisible = false, focusTarget = null)
        }
    }
}
