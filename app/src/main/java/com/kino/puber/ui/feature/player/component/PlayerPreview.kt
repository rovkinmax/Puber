package com.kino.puber.ui.feature.player.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Devices.TV_1080p
import androidx.compose.ui.tooling.preview.Preview
import com.kino.puber.core.ui.uikit.component.moviesList.VideoGridItemUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoGridUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.ui.feature.player.model.ActivePanel
import com.kino.puber.ui.feature.player.model.AudioTrackUIState
import com.kino.puber.ui.feature.player.model.PlayerContentState
import com.kino.puber.ui.feature.player.model.PlayerUIMapper
import com.kino.puber.ui.feature.player.model.PlayerViewState
import com.kino.puber.ui.feature.player.model.QualityUIState
import com.kino.puber.ui.feature.player.model.ResumeDialogState
import com.kino.puber.ui.feature.player.model.SeekIndicatorState
import com.kino.puber.ui.feature.player.model.SoundModeUIState
import com.kino.puber.ui.feature.player.model.SubtitleSize
import com.kino.puber.ui.feature.player.model.SubtitleTrackUIState

// region Preview Data

private val previewAudioTracks = listOf(
    AudioTrackUIState(0, "Русский · ПИФАГОР", "ru"),
    AudioTrackUIState(1, "Русский · аудиодескрипция", "ru"),
    AudioTrackUIState(2, "English · Original", "en"),
)

private val previewSubtitleTracks = listOf(
    SubtitleTrackUIState(0, "Выкл.", "", ""),
    SubtitleTrackUIState(1, "Русские", "ru", "https://example.com/ru.vtt"),
    SubtitleTrackUIState(2, "Русские для слабослышащих", "ru", "https://example.com/ru-sdh.vtt"),
    SubtitleTrackUIState(3, "English", "en", "https://example.com/en.vtt"),
    SubtitleTrackUIState(4, "Узбекские · Созданы нейросетью", "uz", "https://example.com/uz.vtt"),
)

private val previewSoundModes = listOf(
    SoundModeUIState(0, "Стерео 2.0"),
)

private val previewQualities = listOf(
    QualityUIState(0, "1080p", 1080, 1920, 1080),
    QualityUIState(1, "720p", 720, 1280, 720),
    QualityUIState(2, "480p", 480, 854, 480),
)

private val previewEpisodes = VideoGridUIState(
    list = listOf(
        VideoGridItemUIState.Title("1 сезон, 10 серий"),
        VideoGridItemUIState.Items(
            listOf(
                VideoItemUIState(1, "1. Привет фром Марс", "", ""),
                VideoItemUIState(2, "2. Как мы поезд пропустили", "", ""),
                VideoItemUIState(3, "3. Не шутите с плутонцами!", "", ""),
                VideoItemUIState(4, "4. Как не надо спасать мир", "", ""),
            )
        ),
        VideoGridItemUIState.Title("2 сезон, 10 серий"),
        VideoGridItemUIState.Items(
            listOf(
                VideoItemUIState(11, "1. Венеровский централ", "", ""),
                VideoItemUIState(12, "2. Космический мусор", "", ""),
                VideoItemUIState(13, "3. Извинигород", "", ""),
                VideoItemUIState(14, "4. Дед-Код", "", ""),
            )
        ),
    )
)

private fun previewSeriesContent(
    controlsVisible: Boolean = true,
    activePanel: ActivePanel = ActivePanel.None,
    seekIndicator: SeekIndicatorState? = null,
    nextEpisodeCountdown: Int? = null,
    resumeDialog: ResumeDialogState? = null,
) = PlayerContentState(
    title = "Кибердеревня",
    subtitle = "2 сезон, 6 серия, Что там, за железным занавесом?",
    isPlaying = true,
    currentPosition = 630_000L,
    duration = 1_647_000L,
    bufferedPosition = 900_000L,
    controlsVisible = controlsVisible,
    controlsFocusTarget = null,
    activePanel = activePanel,
    seekIndicator = seekIndicator,
    audioTracks = previewAudioTracks,
    selectedAudioTrackIndex = 0,
    subtitleTracks = previewSubtitleTracks,
    selectedSubtitleIndex = 0,
    soundModes = previewSoundModes,
    selectedSoundModeIndex = 0,
    subtitleSize = SubtitleSize.MEDIUM,
    qualities = previewQualities,
    selectedQualityIndex = 0,
    speeds = PlayerUIMapper.SPEEDS,
    selectedSpeedIndex = PlayerUIMapper.DEFAULT_SPEED_INDEX,
    aspectRatios = PlayerUIMapper.ASPECT_RATIOS,
    selectedAspectRatioIndex = PlayerUIMapper.DEFAULT_ASPECT_RATIO_INDEX,
    isMovie = false,
    hasNextEpisode = true,
    nextEpisodeCountdown = nextEpisodeCountdown,
    resumeDialog = resumeDialog,
    episodes = previewEpisodes,
    currentEpisodeId = 13,
)

private fun previewMovieContent(
    controlsVisible: Boolean = true,
    activePanel: ActivePanel = ActivePanel.None,
    resumeDialog: ResumeDialogState? = null,
) = PlayerContentState(
    title = "Граф Монте-Кристо",
    subtitle = null,
    isPlaying = true,
    currentPosition = 240_000L,
    duration = 10_679_000L,
    bufferedPosition = 600_000L,
    controlsVisible = controlsVisible,
    controlsFocusTarget = null,
    activePanel = activePanel,
    seekIndicator = null,
    audioTracks = previewAudioTracks.take(2),
    selectedAudioTrackIndex = 0,
    subtitleTracks = previewSubtitleTracks.take(3),
    selectedSubtitleIndex = 0,
    soundModes = previewSoundModes,
    selectedSoundModeIndex = 0,
    subtitleSize = SubtitleSize.MEDIUM,
    qualities = previewQualities,
    selectedQualityIndex = 0,
    speeds = PlayerUIMapper.SPEEDS,
    selectedSpeedIndex = PlayerUIMapper.DEFAULT_SPEED_INDEX,
    aspectRatios = PlayerUIMapper.ASPECT_RATIOS,
    selectedAspectRatioIndex = PlayerUIMapper.DEFAULT_ASPECT_RATIO_INDEX,
    isMovie = true,
    hasNextEpisode = false,
    nextEpisodeCountdown = null,
    resumeDialog = resumeDialog,
    episodes = null,
    currentEpisodeId = null,
)

// endregion

// region Previews

@Preview(name = "Loading", device = TV_1080p)
@Composable
private fun LoadingPreview() = PuberTheme {
    PlayerScreenContent(
        state = PlayerViewState.Loading,
        onAction = {},
        exoPlayer = { null },
    )
}

@Preview(name = "Error", device = TV_1080p)
@Composable
private fun ErrorPreview() = PuberTheme {
    PlayerScreenContent(
        state = PlayerViewState.Error("Ошибка воспроизведения: не удалось загрузить поток"),
        onAction = {},
        exoPlayer = { null },
    )
}

@Preview(name = "Series — controls visible", device = TV_1080p)
@Composable
private fun SeriesControlsPreview() = PuberTheme {
    PlayerScreenContent(
        state = PlayerViewState.Content(previewSeriesContent()),
        onAction = {},
        exoPlayer = { null },
    )
}

@Preview(name = "Movie — controls visible", device = TV_1080p)
@Composable
private fun MovieControlsPreview() = PuberTheme {
    PlayerScreenContent(
        state = PlayerViewState.Content(previewMovieContent()),
        onAction = {},
        exoPlayer = { null },
    )
}

@Preview(name = "Series — controls hidden + seek forward", device = TV_1080p)
@Composable
private fun SeekForwardPreview() = PuberTheme {
    PlayerScreenContent(
        state = PlayerViewState.Content(
            previewSeriesContent(
                controlsVisible = false,
                seekIndicator = SeekIndicatorState(
                    isForward = true,
                    offsetText = "+10с",
                    targetTimeText = "10:40",
                ),
            )
        ),
        onAction = {},
        exoPlayer = { null },
    )
}

@Preview(name = "Series — controls hidden + seek backward", device = TV_1080p)
@Composable
private fun SeekBackwardPreview() = PuberTheme {
    PlayerScreenContent(
        state = PlayerViewState.Content(
            previewSeriesContent(
                controlsVisible = false,
                seekIndicator = SeekIndicatorState(
                    isForward = false,
                    offsetText = "−30с",
                    targetTimeText = "5:30",
                ),
            )
        ),
        onAction = {},
        exoPlayer = { null },
    )
}

@Preview(name = "Audio & Subtitles panel", device = TV_1080p)
@Composable
private fun AudioSubtitlesPanelPreview() = PuberTheme {
    PlayerScreenContent(
        state = PlayerViewState.Content(
            previewSeriesContent(
                controlsVisible = false,
                activePanel = ActivePanel.AudioSubtitles,
            )
        ),
        onAction = {},
        exoPlayer = { null },
    )
}

@Preview(name = "Video Settings panel", device = TV_1080p)
@Composable
private fun VideoSettingsPanelPreview() = PuberTheme {
    PlayerScreenContent(
        state = PlayerViewState.Content(
            previewSeriesContent(
                controlsVisible = false,
                activePanel = ActivePanel.VideoSettings,
            )
        ),
        onAction = {},
        exoPlayer = { null },
    )
}

@Preview(name = "Episodes panel", device = TV_1080p)
@Composable
private fun EpisodesPanelPreview() = PuberTheme {
    PlayerScreenContent(
        state = PlayerViewState.Content(
            previewSeriesContent(
                controlsVisible = false,
                activePanel = ActivePanel.Episodes,
            )
        ),
        onAction = {},
        exoPlayer = { null },
    )
}

@Preview(name = "Resume dialog", device = TV_1080p)
@Composable
private fun ResumeDialogPreview() = PuberTheme {
    PlayerScreenContent(
        state = PlayerViewState.Content(
            previewMovieContent(
                controlsVisible = false,
                resumeDialog = ResumeDialogState(
                    savedPosition = 3_600_000L,
                    formattedTime = "1:00:00",
                ),
            )
        ),
        onAction = {},
        exoPlayer = { null },
    )
}

@Preview(name = "Next episode countdown", device = TV_1080p)
@Composable
private fun NextEpisodeCountdownPreview() = PuberTheme {
    PlayerScreenContent(
        state = PlayerViewState.Content(
            previewSeriesContent(
                controlsVisible = false,
                nextEpisodeCountdown = 5,
            )
        ),
        onAction = {},
        exoPlayer = { null },
    )
}

@Preview(name = "Series — paused with controls", device = TV_1080p)
@Composable
private fun PausedPreview() = PuberTheme {
    PlayerScreenContent(
        state = PlayerViewState.Content(
            previewSeriesContent().copy(isPlaying = false)
        ),
        onAction = {},
        exoPlayer = { null },
    )
}

// endregion
