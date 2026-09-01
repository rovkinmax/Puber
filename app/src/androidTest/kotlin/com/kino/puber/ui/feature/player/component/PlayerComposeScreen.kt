package com.kino.puber.ui.feature.player.component

import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasAnyDescendant
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
