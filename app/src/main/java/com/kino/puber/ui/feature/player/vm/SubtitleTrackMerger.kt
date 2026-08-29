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

        val enrichedPlayerTracks = enrichPlayerTracks(playerSubtitles, apiSubtitles)
        val orderedPlayerTracks = orderByLanguage(enrichedPlayerTracks)
        return listOf(offTrack) + labeler.apply(orderedPlayerTracks)
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

    /**
     * Every external subtitle is side-loaded because the manifest contents are unknown
     * before preparation, so a subtitle the manifest also publishes shows up twice.
     *
     * Renditions and API subtitles share no identifier — a rendition is addressed by its
     * HLS playlist URL, an API subtitle by its file path — so the duplicate cannot be
     * identified per track. A manifest that publishes subtitles at all is treated as the
     * complete list and the side-loaded copies are hidden; the side-loaded tracks carry
     * playback only when the manifest offers no subtitles of its own.
     */
    private fun enrichPlayerTracks(
        playerSubtitles: List<SubtitleTrackUIState>,
        apiSubtitles: List<SubtitleTrackUIState>,
    ): List<SubtitleTrackUIState> {
        val manifestPublishesSubtitles = playerSubtitles.any { it.isFromManifest }
        val apiMatches = playerSubtitles.map { track -> findExactIdentityMatch(track, apiSubtitles) }
        val claimedApiIndices = mutableSetOf<Int>()
        return playerSubtitles.mapIndexedNotNull { position, playerTrack ->
            if (manifestPublishesSubtitles && !playerTrack.isFromManifest) {
                return@mapIndexedNotNull null
            }
            apiMatches[position]
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
