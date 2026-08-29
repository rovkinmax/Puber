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
        canonicalSubtitleLanguage(track.language)

    /**
     * Every external subtitle is side-loaded because the manifest contents are unknown
     * before preparation, so a subtitle the manifest also publishes shows up twice. The
     * two tracks resolve to the same API entry, and the manifest rendition wins.
     */
    /**
     * Every external subtitle is side-loaded because the manifest contents are unknown
     * before preparation. Once they are known, a side-loaded subtitle whose language the
     * manifest already publishes is redundant: the renditions are segmented with the
     * stream and are what the KinoPub web player offers.
     */
    private fun enrichPlayerTracks(
        playerSubtitles: List<SubtitleTrackUIState>,
        apiSubtitles: List<SubtitleTrackUIState>,
    ): List<SubtitleTrackUIState> {
        val manifestLanguages = playerSubtitles
            .filter { it.isFromManifest }
            .mapTo(mutableSetOf()) { canonicalSubtitleLanguage(it.language) }
        val apiMatches = playerSubtitles.map { track -> findExactIdentityMatch(track, apiSubtitles) }
        val claimedApiIndices = mutableSetOf<Int>()
        return playerSubtitles.mapIndexedNotNull { position, playerTrack ->
            if (playerTrack.isRedundantSideLoad(manifestLanguages)) {
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

private fun SubtitleTrackUIState.isRedundantSideLoad(manifestLanguages: Set<String>): Boolean =
    !isFromManifest &&
        language.isNotBlank() &&
        canonicalSubtitleLanguage(language) in manifestLanguages

// MergingMediaSource rewrites child track ids to "<childIndex>:<originalId>".
private fun String.withoutMergedSourcePrefix(): String = MERGED_SOURCE_PREFIX.replace(this, "")

private val MERGED_SOURCE_PREFIX = Regex("""^\d+:""")
