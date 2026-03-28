package com.kino.puber.ui.feature.player.model

import androidx.compose.runtime.Immutable
import com.kino.puber.core.ui.uikit.component.moviesList.VideoGridUIState
import com.kino.puber.data.api.models.SkipSegmentType
import com.kino.puber.domain.model.SubtitleSize
import com.kino.puber.ui.feature.player.model.FocusTarget
import com.kino.puber.ui.feature.player.vm.PlaybackController

@Immutable
internal sealed class PlayerViewState {
    data object Loading : PlayerViewState()
    data class Error(val message: String) : PlayerViewState()
    data class Content(val content: PlayerContentState) : PlayerViewState()
}

@Immutable
internal data class PlayerContentState(
    // Title
    val title: String,
    val subtitle: String?,

    // Playback
    val isPlaying: Boolean,
    val currentPosition: Long,
    val duration: Long,
    val bufferedPosition: Long,

    // Controls
    val controlsVisible: Boolean,
    val controlsFocusTarget: FocusTarget?,
    val activePanel: ActivePanel,
    val seekIndicator: SeekIndicatorState?,
    val playPauseIndicator: PlayPauseIndicatorState?,

    // Audio & Subtitles
    val audioTracks: List<AudioTrackUIState>,
    val selectedAudioTrackIndex: Int,
    val subtitleTracks: List<SubtitleTrackUIState>,
    val selectedSubtitleIndex: Int,
    val soundModes: List<SoundModeUIState>,
    val selectedSoundModeIndex: Int,
    val subtitleSize: SubtitleSize,

    // Video settings
    val qualities: List<QualityUIState>,
    val selectedQualityIndex: Int,
    val speeds: List<SpeedUIState>,
    val selectedSpeedIndex: Int,
    val aspectRatios: List<AspectRatioUIState>,
    val selectedAspectRatioIndex: Int,

    // Content type
    val isMovie: Boolean,
    val hasNextEpisode: Boolean,

    // Next episode countdown
    val nextEpisodeCountdown: Int?,

    // Skip segment overlay
    val activeSkipSegment: SkipSegmentUIState? = null,

    // Resume dialog
    val resumeDialog: ResumeDialogState?,

    // Episodes grid (series only)
    val episodes: VideoGridUIState?,
    val currentEpisodeId: Int?,

    // Debug overlay
    val debugInfo: PlaybackController.DebugInfo? = null,
)

@Immutable
internal data class SkipSegmentUIState(
    val label: String,
    val targetPositionMs: Long,
    val type: SkipSegmentType,
    val countdown: Int,
)

@Immutable
internal enum class ActivePanel {
    None,
    AudioSubtitles,
    VideoSettings,
    Episodes,
}
