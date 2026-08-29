package com.kino.puber.ui.feature.player.vm

import com.kino.puber.ui.feature.player.model.SubtitleTrackUIState
import com.kino.puber.ui.feature.player.model.isOff
import java.util.Locale

internal class SubtitleTrackMerger(
    private val labeler: SubtitleLabeler,
) {

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

        val enrichedPlayerTracks = enrichPlayerTracks(playerSubtitles, apiSubtitles)
        val orderedPlayerTracks = orderByLanguage(enrichedPlayerTracks)
        return (listOf(offTrack) + labeler.apply(orderedPlayerTracks))
            .mapIndexed { index, track -> track.copy(index = index) }
    }

    /**
     * Player order follows the manifest, which is arbitrary from the viewer's side. Group
     * the rows by language in the order each language first appears, keeping every variant
     * of a language together and its partial variant last.
     */
    private fun orderByLanguage(
        tracks: List<SubtitleTrackUIState>,
    ): List<SubtitleTrackUIState> {
        val languageOrder = mutableMapOf<String, Int>()
        tracks.forEach { track ->
            languageOrder.getOrPut(orderingLanguage(track)) { languageOrder.size }
        }
        return tracks.withIndex()
            .sortedWith(
                compareBy(
                    { languageOrder.getValue(orderingLanguage(it.value)) },
                    { it.value.isForced == true },
                    { it.index },
                ),
            )
            .map { it.value }
    }

    private fun orderingLanguage(track: SubtitleTrackUIState): String =
        track.language.trim().lowercase(Locale.ROOT)

    /**
     * Every external subtitle is side-loaded because the manifest contents are unknown
     * before preparation, so a subtitle the manifest also publishes shows up twice. The
     * two tracks resolve to the same API entry, and the manifest rendition wins.
     */
    private fun enrichPlayerTracks(
        playerSubtitles: List<SubtitleTrackUIState>,
        apiSubtitles: List<SubtitleTrackUIState>,
    ): List<SubtitleTrackUIState> {
        val apiMatches = playerSubtitles.map { track -> findExactIdentityMatch(track, apiSubtitles) }
        val redundant = mutableSetOf<Int>()
        val owners = mutableMapOf<Int, Int>()
        apiMatches.withIndex()
            .mapNotNull { (position, apiIndex) -> apiIndex?.let { it to position } }
            .groupBy({ it.first }, { it.second })
            .forEach { (apiIndex, positions) ->
                val fromManifest = positions.filter { playerSubtitles[it].playerTrackUri != null }
                if (fromManifest.size == 1 && positions.size > 1) {
                    redundant += positions - fromManifest.toSet()
                    owners[apiIndex] = fromManifest.single()
                } else {
                    owners[apiIndex] = positions.first()
                }
            }

        return playerSubtitles.mapIndexedNotNull { position, playerTrack ->
            if (position in redundant) return@mapIndexedNotNull null
            apiMatches[position]
                ?.takeIf { apiIndex -> owners[apiIndex] == position }
                ?.let { apiIndex -> playerTrack.withApiMetadata(apiSubtitles[apiIndex]) }
                ?: playerTrack
        }
    }

    private fun findExactIdentityMatch(
        playerTrack: SubtitleTrackUIState,
        apiTracks: List<SubtitleTrackUIState>,
    ): Int? {
        val playerIdentities = listOfNotNull(
            playerTrack.playerTrackUri,
            playerTrack.playerTrackId,
        ).filter { it.isNotEmpty() }
        if (playerIdentities.isEmpty()) return null
        return apiTracks.indices.filter { apiIndex ->
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
