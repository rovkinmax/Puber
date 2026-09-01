package com.kino.puber.ui.feature.player.component

import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import io.github.kakaocup.compose.node.element.ComposeScreen
import io.github.kakaocup.compose.node.element.KNode

internal class PlayerComposeScreen(
    semanticsProvider: SemanticsNodeInteractionsProvider,
) : ComposeScreen<PlayerComposeScreen>(
    semanticsProvider = semanticsProvider,
    viewBuilderAction = {
        useUnmergedTree = true
        hasTestTag(PlayerScreenTestTags.Root)
    },
) {

    val playerSurface: KNode = child {
        useUnmergedTree = true
        hasTestTag(PlayerScreenTestTags.Surface)
    }

    val playPauseButton: KNode = child {
        useUnmergedTree = true
        hasTestTag(PlayerScreenTestTags.PlayPause)
    }

    val seekBar: KNode = child {
        useUnmergedTree = true
        hasTestTag(PlayerScreenTestTags.SeekBar)
    }

    val markWatchedButton: KNode = tagged(PlayerScreenTestTags.MarkWatched)
    val episodesButton: KNode = tagged(PlayerScreenTestTags.Episodes)
    val audioSubtitlesButton: KNode = tagged(PlayerScreenTestTags.AudioSubtitles)
    val videoSettingsButton: KNode = tagged(PlayerScreenTestTags.VideoSettings)
    val resumeButton: KNode = tagged(PlayerScreenTestTags.ResumeContinue)
    val retryButton: KNode = tagged(PlayerScreenTestTags.Retry)
    val nextEpisodeButton: KNode = tagged(PlayerScreenTestTags.NextEpisode)

    val focusedMarkWatchedButton: KNode = focusedTag(PlayerScreenTestTags.MarkWatched)
    val focusedEpisodesButton: KNode = focusedTag(PlayerScreenTestTags.Episodes)
    val focusedAudioSubtitlesButton: KNode = focusedTag(PlayerScreenTestTags.AudioSubtitles)
    val focusedVideoSettingsButton: KNode = focusedTag(PlayerScreenTestTags.VideoSettings)
    val focusedResumeButton: KNode = focusedTag(PlayerScreenTestTags.ResumeContinue)
    val focusedRetryButton: KNode = focusedTag(PlayerScreenTestTags.Retry)
    val focusedNextEpisodeButton: KNode = focusedTag(PlayerScreenTestTags.NextEpisode)

    fun panelItem(group: String, index: Int): KNode =
        tagged(PlayerScreenTestTags.panelItem(group, index))

    fun focusedPanelItem(group: String, index: Int): KNode =
        focusedTag(PlayerScreenTestTags.panelItem(group, index))

    fun tagged(tag: String): KNode = child {
        useUnmergedTree = true
        hasTestTag(tag)
    }

    fun focusedTag(tag: String): KNode {
        val tagMatcher = hasTestTag(tag)
        val matcher = isFocused() and (
            tagMatcher or
                hasAnyDescendant(tagMatcher)
            )
        return child {
            useUnmergedTree = true
            addSemanticsMatcher(matcher)
        }
    }

    fun text(text: String, substring: Boolean = false): KNode = child {
        useUnmergedTree = true
        hasText(text, substring = substring)
    }

    fun focusedText(text: String, substring: Boolean = false): KNode {
        val textMatcher = hasText(text, substring = substring)
        val matcher = isFocused() and (textMatcher or hasAnyDescendant(textMatcher))
        return child {
            useUnmergedTree = true
            addSemanticsMatcher(matcher)
        }
    }
}
