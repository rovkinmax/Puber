package com.kino.puber.ui.feature.player.model

import androidx.compose.runtime.Immutable

@Immutable
internal data class AudioTrackUIState(
    val index: Int,
    val label: String,
    val language: String,
)

@Immutable
internal data class SubtitleTrackUIState(
    val index: Int,
    val label: String,
    val language: String,
    val url: String,
)

@Immutable
internal data class SoundModeUIState(
    val index: Int,
    val label: String,
)

@Immutable
internal data class QualityUIState(
    val index: Int,
    val label: String,
    val qualityId: Int?,
    val width: Int?,
    val height: Int?,
)

@Immutable
internal data class SpeedUIState(
    val index: Int,
    val label: String,
    val speed: Float,
)

@Immutable
internal data class AspectRatioUIState(
    val index: Int,
    val label: String,
    val mode: AspectRatioMode,
)

internal enum class AspectRatioMode {
    AUTO,
    STRETCH,
    CROP,
}

@Immutable
internal data class BufferPresetUIState(
    val index: Int,
    val label: String,
    val preset: BufferPreset,
)

enum class BufferPreset {
    AUTO,
    SMALL,
    MEDIUM,
    LARGE,
    MAX,
}

@Immutable
internal data class SeekIndicatorState(
    val isForward: Boolean,
    val offsetText: String,
    val targetTimeText: String,
)

@Immutable
internal data class PlayPauseIndicatorState(
    val isPlaying: Boolean,
)

@Immutable
internal data class ResumeDialogState(
    val savedPosition: Long,
    val formattedTime: String,
    val episodeInfo: String? = null,
)
