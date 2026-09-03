package com.kino.puber.ui.feature.player.vm

import com.kino.puber.ui.feature.player.model.SubtitleTrackUIState
import com.kino.puber.ui.feature.player.model.isOff

internal class SubtitleTrackMerger(
    private val labeler: SubtitleLabeler,
) {

    fun merge(
        apiTracks: List<SubtitleTrackUIState>,
        playerTracks: List<SubtitleTrackUIState>,
    ): List<SubtitleTrackUIState> {
        val offTrack = apiTracks.firstOrNull { it.isOff } ?: SubtitleTrackUIState(
            label = "",
            language = "",
            url = "",
        )
        val apiSubtitles = apiTracks.filterNot { it.isOff }
        val playerSubtitles = playerTracks.filterNot { it.isOff }
        if (playerSubtitles.isEmpty()) {
            return listOf(offTrack)
        }

        val selectedPlayerTracks = selectTracksByLanguage(playerSubtitles)
        val enrichedPlayerTracks = enrichPlayerTracks(selectedPlayerTracks, apiSubtitles)
        val orderedPlayerTracks = orderByLanguage(enrichedPlayerTracks)
        return listOf(offTrack) + labeler.apply(orderedPlayerTracks)
    }

    /**
     * API subtitles are side-loaded before the manifest is known. For each language,
     * keep the source with more tracks, preferring HLS on ties to avoid duplicate variants.
     * Compare discovered player tracks so every retained row can be selected for playback.
     */
    private fun selectTracksByLanguage(
        tracks: List<SubtitleTrackUIState>,
    ): List<SubtitleTrackUIState> = tracks.groupBy(::orderingLanguage).values.flatMap { languageTracks ->
        val (manifestTracks, sideLoadedTracks) = languageTracks.partition { it.isFromManifest }
        if (manifestTracks.size >= sideLoadedTracks.size) manifestTracks else sideLoadedTracks
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
        canonicalSubtitleLanguage(track.language)

    private fun enrichPlayerTracks(
        playerSubtitles: List<SubtitleTrackUIState>,
        apiSubtitles: List<SubtitleTrackUIState>,
    ): List<SubtitleTrackUIState> {
        val claimedApiIndices = mutableSetOf<Int>()
        return playerSubtitles.map { playerTrack ->
            findExactIdentityMatch(playerTrack, apiSubtitles)
                ?.takeIf(claimedApiIndices::add)
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
            playerTrack.playerTrackId?.withoutMergedSourcePrefix(),
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

private val SubtitleTrackUIState.isFromManifest: Boolean
    get() = playerTrackUri != null

// MergingMediaSource rewrites child track ids to "<childIndex>:<originalId>".
private fun String.withoutMergedSourcePrefix(): String = MERGED_SOURCE_PREFIX.replace(this, "")

private val MERGED_SOURCE_PREFIX = Regex("""^\d+:""")
