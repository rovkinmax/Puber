package com.kino.puber.ui.feature.history.component

import com.kino.puber.R
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.domain.interactor.history.HistoryRowKey
import com.kino.puber.domain.interactor.history.HistorySemanticKey
import com.kino.puber.ui.feature.history.model.HistoryItemUIState
import com.kino.puber.ui.feature.history.model.HistoryPlaybackTarget
import com.kino.puber.ui.feature.player.model.PlayerStartMode
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class HistoryContextMenuTest {

    @Test
    fun playbackActionTitleUsesContinueForUnfinishedProgress() {
        val action = historyPlaybackMenuAction(
            historyItem(progressPercent = 0.42F, isWatched = false),
        )
        assertEquals(R.string.history_context_continue, action?.titleRes)
        assertEquals(PlayerStartMode.ResumeIfAvailable, action?.startMode)
    }

    @Test
    fun playbackActionTitleUsesStartOverWithoutResumableProgress() {
        listOf(
            historyItem(progressPercent = null, isWatched = false),
            historyItem(progressPercent = 0F, isWatched = false),
            historyItem(progressPercent = 0.42F, isWatched = true),
        ).forEach { item ->
            val action = historyPlaybackMenuAction(item)
            assertEquals(R.string.history_context_play, action?.titleRes)
            assertEquals(PlayerStartMode.StartFromBeginning, action?.startMode)
        }
    }

    @Test
    fun fallbackDetailsRowHasNoPlaybackMenuIntent() {
        assertNull(
            historyPlaybackMenuAction(
                historyItem(
                    progressPercent = 0.42F,
                    isWatched = false,
                    playbackTarget = HistoryPlaybackTarget.Details,
                ),
            ),
        )
    }

    @Test
    fun localizedPlaybackLabelsMatchApprovedCopy() {
        val strings = String(
            Files.readAllBytes(resolveStringsResource()),
            StandardCharsets.UTF_8,
        )

        assertTrue(
            strings.contains(
                """<string name="history_context_continue">Продолжить просмотр</string>""",
            ),
        )
        assertTrue(
            strings.contains(
                """<string name="history_context_play">Смотреть сначала</string>""",
            ),
        )
    }

    private fun historyItem(
        progressPercent: Float?,
        isWatched: Boolean,
        playbackTarget: HistoryPlaybackTarget = HistoryPlaybackTarget.Movie(videoNumber = 1),
    ): HistoryItemUIState {
        val semanticKey = if (playbackTarget == HistoryPlaybackTarget.Details) {
            null
        } else {
            HistorySemanticKey.Movie(itemId = 42, videoNumber = 1)
        }
        return HistoryItemUIState(
            itemId = 42,
            deletionMediaId = 2,
            rowKey = semanticKey?.let(HistoryRowKey::Media) ?: HistoryRowKey.DeletionMedia(2),
            semanticKey = semanticKey,
            videoNumber = semanticKey?.videoNumber,
            seasonNumber = null,
            episodeNumber = null,
            progressPercent = progressPercent,
            isWatched = isWatched,
            lastViewedAt = null,
            playbackTarget = playbackTarget,
            card = VideoItemUIState(
                id = 42,
                title = "Synthetic title",
                imageUrl = "",
                bigImageUrl = "",
            ),
        )
    }

    private fun resolveStringsResource(): Path {
        val workingDirectory = Path.of(System.getProperty("user.dir"))
        val candidates = listOf(
            workingDirectory.resolve("src/main/res/values/strings.xml"),
            workingDirectory.resolve("app/src/main/res/values/strings.xml"),
        )
        return candidates.firstOrNull(Files::exists)
            ?: error("Unable to locate values/strings.xml")
    }
}
