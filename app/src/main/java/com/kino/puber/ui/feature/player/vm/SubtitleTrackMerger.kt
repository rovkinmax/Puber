package com.kino.puber.ui.feature.player.vm

import com.kino.puber.ui.feature.player.model.SubtitleTrackUIState
import com.kino.puber.ui.feature.player.model.isOff
import java.util.Locale

internal class SubtitleTrackMerger {

    fun merge(
        apiTracks: List<SubtitleTrackUIState>,
        playerTracks: List<SubtitleTrackUIState>,
    ): List<SubtitleTrackUIState> {
        val offTrack = apiTracks.firstOrNull { it.isOff } ?: SubtitleTrackUIState(
            index = 0,
            label = "",
            language = "",
            url = "",
        )
        val apiSubtitles = apiTracks.filterNot { it.isOff }
        val playerSubtitles = playerTracks.filterNot { it.isOff }
        if (playerSubtitles.isEmpty()) {
            return listOf(offTrack.copy(index = 0))
        }

        val availableApiIndices = apiSubtitles.indices.toMutableSet()
        val enrichedPlayerTracks = playerSubtitles.map { playerTrack ->
            findExactIdentityMatch(playerTrack, apiSubtitles, availableApiIndices)
                ?.let { apiIndex ->
                    availableApiIndices.remove(apiIndex)
                    playerTrack.withApiMetadata(apiSubtitles[apiIndex])
                }
                ?: playerTrack
        }
        return (listOf(offTrack) + enrichedPlayerTracks).mapIndexed { index, track ->
            track.copy(index = index)
        }
    }

    private fun findExactIdentityMatch(
        playerTrack: SubtitleTrackUIState,
        apiTracks: List<SubtitleTrackUIState>,
        availableApiIndices: Set<Int>,
    ): Int? {
        val playerIdentities = listOfNotNull(
            playerTrack.playerTrackUri,
            playerTrack.playerTrackId,
        ).filter { it.isNotEmpty() }
        if (playerIdentities.isEmpty()) return null
        return availableApiIndices.filter { apiIndex ->
            val apiTrack = apiTracks[apiIndex]
            val apiIdentities = listOfNotNull(apiTrack.sourceFile, apiTrack.url)
                .filter { it.isNotEmpty() }
            apiIdentities.any { apiIdentity ->
                playerIdentities.any { playerIdentity ->
                    sameSubtitleIdentity(apiIdentity, playerIdentity)
                }
            }
        }.singleOrNull()
    }

    private fun SubtitleTrackUIState.withApiMetadata(
        apiTrack: SubtitleTrackUIState,
    ): SubtitleTrackUIState = copy(
        url = apiTrack.url,
        sourceFile = apiTrack.sourceFile,
        isForced = apiTrack.isForced ?: isForced,
    )
}

internal fun sameSubtitleLanguage(first: String, second: String): Boolean {
    if (first.isBlank() || second.isBlank()) return false
    return canonicalSubtitleLanguage(first) == canonicalSubtitleLanguage(second)
}

internal fun subtitleTrackDisplayLabel(language: String, fallbackLabel: String): String {
    return canonicalSubtitleLanguage(language).ifEmpty { fallbackLabel }
}

private fun canonicalSubtitleLanguage(language: String): String {
    val normalized = language
        .trim()
        .lowercase(Locale.ROOT)
        .substringBefore('-')
        .substringBefore('_')
    return runCatching { Locale.forLanguageTag(normalized).isO3Language }
        .getOrNull()
        ?.takeIf { it.isNotBlank() && it != "und" }
        ?: normalized
}
