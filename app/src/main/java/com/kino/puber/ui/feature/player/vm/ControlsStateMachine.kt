package com.kino.puber.ui.feature.player.vm

import com.kino.puber.ui.feature.player.model.ActivePanel
import com.kino.puber.ui.feature.player.model.FocusTarget
import com.kino.puber.ui.feature.player.model.PlayerContentState

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
    private var wasPlayRequestedBeforePanel = false

    fun initialize(resumeDialogVisible: Boolean) {
        state = State(
            controlsVisible = !resumeDialogVisible,
            focusTarget = FocusTarget.Buttons.takeUnless { resumeDialogVisible },
            activePanel = ActivePanel.None,
        )
        lastPanelOpener = FocusTarget.Buttons
        wasPlayRequestedBeforePanel = false
    }

    fun showControls(focusTarget: FocusTarget): List<Effect> {
        return revealControls(focusTarget, Effect.ScheduleHide)
    }

    fun showControlsPersistently(focusTarget: FocusTarget): List<Effect> {
        return revealControls(focusTarget, Effect.CancelHide)
    }

    private fun revealControls(focusTarget: FocusTarget, effect: Effect): List<Effect> {
        state = state.copy(controlsVisible = true, focusTarget = focusTarget)
        return listOf(effect)
    }

    fun hideControls(): List<Effect> {
        state = state.copy(controlsVisible = false, focusTarget = null)
        return listOf(Effect.CancelHide)
    }

    fun openPanel(panel: ActivePanel, playbackIntent: PlaybackIntent): List<Effect> {
        lastPanelOpener = when (panel) {
            ActivePanel.Episodes -> FocusTarget.EpisodesButton
            ActivePanel.AudioSubtitles -> FocusTarget.AudioSubtitlesButton
            ActivePanel.VideoSettings -> FocusTarget.VideoSettingsButton
            ActivePanel.None -> FocusTarget.Buttons
        }

        val effects = mutableListOf<Effect>(Effect.CancelHide)

        if (panel == ActivePanel.Episodes) {
            wasPlayRequestedBeforePanel = playbackIntent == PlaybackIntent.PlayRequested
            effects.add(Effect.PausePlayback)
        }

        state = state.copy(activePanel = panel, controlsVisible = false, focusTarget = null)
        return effects
    }

    fun closePanel(): List<Effect> {
        val effects = mutableListOf<Effect>()

        if (state.activePanel == ActivePanel.Episodes && wasPlayRequestedBeforePanel) {
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

internal fun PlayerContentState.withControlsState(
    state: ControlsStateMachine.State,
): PlayerContentState {
    return copy(
        controlsVisible = state.controlsVisible,
        controlsFocusTarget = state.focusTarget,
        activePanel = state.activePanel,
    )
}
