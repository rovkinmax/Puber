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
        val availablePlayerIndices = playerSubtitles.indices.toMutableSet()
        val matches = mutableMapOf<Int, Int>()

        apiSubtitles.forEachIndexed { apiIndex, apiTrack ->
            findExactIdentityMatch(apiTrack, playerSubtitles, availablePlayerIndices)?.let { playerIndex ->
                matches[apiIndex] = playerIndex
                availablePlayerIndices.remove(playerIndex)
            }
        }

        apiSubtitles.forEachIndexed { apiIndex, apiTrack ->
            if (apiIndex in matches || !apiTrack.isEmbedded) return@forEachIndexed
            findEmbeddedLanguageMatch(apiTrack, playerSubtitles, availablePlayerIndices)?.let { playerIndex ->
                matches[apiIndex] = playerIndex
                availablePlayerIndices.remove(playerIndex)
            }
        }

        val merged = buildList {
            add(offTrack)
            apiSubtitles.forEachIndexed { apiIndex, apiTrack ->
                val playerTrack = matches[apiIndex]?.let(playerSubtitles::get)
                add(if (playerTrack == null) apiTrack else apiTrack.withPlayerIdentity(playerTrack))
            }
            availablePlayerIndices.forEach { playerIndex ->
                add(playerSubtitles[playerIndex])
            }
        }
        return merged.mapIndexed { index, track -> track.copy(index = index) }
    }

    private fun findExactIdentityMatch(
        apiTrack: SubtitleTrackUIState,
        playerTracks: List<SubtitleTrackUIState>,
        availablePlayerIndices: Set<Int>,
    ): Int? {
        val apiKey = apiTrack.url.stableSubtitleKey().takeIf { it.isNotEmpty() } ?: return null
        return availablePlayerIndices.firstOrNull { playerIndex ->
            val playerTrack = playerTracks[playerIndex]
            val playerIdKey = playerTrack.playerTrackId
                ?.stableSubtitleKey()
                ?.takeIf { it.isNotEmpty() }
            playerTrack.playerTrackId == apiTrack.url ||
                playerIdKey == apiKey ||
                playerTrack.label == apiKey
        }
    }

    private fun findEmbeddedLanguageMatch(
        apiTrack: SubtitleTrackUIState,
        playerTracks: List<SubtitleTrackUIState>,
        availablePlayerIndices: Set<Int>,
    ): Int? {
        val languageMatches = availablePlayerIndices.filter { playerIndex ->
            sameSubtitleLanguage(apiTrack.language, playerTracks[playerIndex].language)
        }
        if (languageMatches.isEmpty()) return null
        return apiTrack.isForced?.let { forced ->
            languageMatches.firstOrNull { playerTracks[it].isForced == forced }
                ?: languageMatches.singleOrNull()
        } ?: languageMatches.first()
    }

    private fun SubtitleTrackUIState.withPlayerIdentity(
        playerTrack: SubtitleTrackUIState,
    ): SubtitleTrackUIState = copy(
        playerTrackId = playerTrack.playerTrackId,
        playerGroupIndex = playerTrack.playerGroupIndex,
        playerTrackIndex = playerTrack.playerTrackIndex,
        isForced = isForced ?: playerTrack.isForced,
    )
}

internal fun sameSubtitleLanguage(first: String, second: String): Boolean {
    if (first.isBlank() || second.isBlank()) return false
    return canonicalSubtitleLanguage(first) == canonicalSubtitleLanguage(second)
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
