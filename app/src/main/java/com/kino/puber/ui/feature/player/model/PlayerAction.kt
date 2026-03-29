package com.kino.puber.ui.feature.player.model

import com.kino.puber.core.ui.uikit.model.UIAction

internal sealed class PlayerAction : UIAction {

    // Playback
    data object TogglePlayPause : PlayerAction()
    data object SeekForward : PlayerAction()
    data object SeekBackward : PlayerAction()

    // Controls visibility
    data class ShowControls(val focusTarget: FocusTarget) : PlayerAction()
    data object HideControls : PlayerAction()
    data object ResetControlsTimer : PlayerAction()

    // Panels
    data object OpenAudioSubtitlesPanel : PlayerAction()
    data object OpenVideoSettingsPanel : PlayerAction()
    data object OpenEpisodesPanel : PlayerAction()
    data object ClosePanel : PlayerAction()

    // Audio & Subtitles selection
    data class SelectAudioTrack(val index: Int) : PlayerAction()
    data class SelectSubtitle(val index: Int) : PlayerAction()
    data class SelectSoundMode(val index: Int) : PlayerAction()
    data object CycleSubtitleSize : PlayerAction()

    // Video settings
    data class SelectQuality(val index: Int) : PlayerAction()
    data class SelectSpeed(val index: Int) : PlayerAction()
    data class SelectAspectRatio(val index: Int) : PlayerAction()

    // Episodes
    data class SelectEpisode(val seasonNumber: Int, val episodeNumber: Int) : PlayerAction()
    data class SelectEpisodeById(val episodeId: Int) : PlayerAction()
    data object NextEpisode : PlayerAction()
    data object CancelNextEpisodeCountdown : PlayerAction()

    // Skip segments
    data object SkipSegmentClicked : PlayerAction()
    data object CancelSkipSegment : PlayerAction()
    data object SkipSegmentCountdownFinished : PlayerAction()

    // Resume dialog
    data object ResumeFromPosition : PlayerAction()
    data object StartFromBeginning : PlayerAction()

    // Error
    data object RetryPlayback : PlayerAction()

    // Lifecycle
    data object OnBackground : PlayerAction()

    // Navigation
    data object OnBackPressed : PlayerAction()
}

internal enum class FocusTarget {
    SeekBar,
    Buttons,
    EpisodesButton,
    AudioSubtitlesButton,
    VideoSettingsButton,
}
