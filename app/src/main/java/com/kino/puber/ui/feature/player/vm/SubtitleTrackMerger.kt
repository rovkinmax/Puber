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
     * before preparation, so a subtitle the manifest also publishes shows up twice.
     *
     * The two sets share no identifier — a rendition is addressed by its HLS playlist URL,
     * an API subtitle by its file path — so coverage is decided per language by count: the
     * side-loaded copies of a language are hidden only when the manifest offers at least as
     * many renditions of it. Anything short of that keeps the side-loaded tracks, so a
     * subtitle is never hidden behind a rendition that cannot stand in for it.
     */
    private fun enrichPlayerTracks(
        playerSubtitles: List<SubtitleTrackUIState>,
        apiSubtitles: List<SubtitleTrackUIState>,
    ): List<SubtitleTrackUIState> {
        val coveredLanguages = coveredLanguages(playerSubtitles)
        val apiMatches = playerSubtitles.map { track -> findExactIdentityMatch(track, apiSubtitles) }
        val claimedApiIndices = mutableSetOf<Int>()
        return playerSubtitles.mapIndexedNotNull { position, playerTrack ->
            val language = canonicalSubtitleLanguage(playerTrack.language)
            if (!playerTrack.isFromManifest && language in coveredLanguages) {
                return@mapIndexedNotNull null
            }
            apiMatches[position]
                ?.takeIf(claimedApiIndices::add)
                ?.let { apiIndex -> playerTrack.withApiMetadata(apiSubtitles[apiIndex]) }
                ?: playerTrack
        }
    }

    private fun coveredLanguages(playerSubtitles: List<SubtitleTrackUIState>): Set<String> {
        val (manifest, sideLoaded) = playerSubtitles.partition { it.isFromManifest }
        val manifestCounts = manifest.countByLanguage()
        return sideLoaded.countByLanguage()
            .filterKeys { it.isNotBlank() }
            .filter { (language, sideLoadedCount) ->
                manifestCounts.getOrDefault(language, 0) >= sideLoadedCount
            }
            .keys
    }

    private fun List<SubtitleTrackUIState>.countByLanguage(): Map<String, Int> =
        groupingBy { canonicalSubtitleLanguage(it.language) }.eachCount()

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
