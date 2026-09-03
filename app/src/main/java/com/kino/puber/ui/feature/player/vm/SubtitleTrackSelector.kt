package com.kino.puber.ui.feature.player.vm

import com.kino.puber.ui.feature.player.model.SubtitleTrackUIState

/**
 * A text track exposed by the player, flattened out of the Media3 track groups.
 *
 * [groupId] is `TrackGroup.id`. MergingMediaSource rewrites the ids of its children to
 * `"<childIndex>:<originalId>"`, so once side-loaded subtitles are merged into an HLS
 * source the id is unique per track group and stable across track updates. Plain sources
 * may leave it blank, which is why every match below is uniqueness-checked.
 */
internal data class PlayerTextTrack(
    val groupId: String,
    val groupIndex: Int,
    val trackIndex: Int,
    val formatId: String? = null,
    val formatLabel: String? = null,
    val language: String? = null,
)

/**
 * Resolves the player text track a picker row points at.
 *
 * The rules never guess: every candidate set is reduced with `singleOrNull`, so an
 * ambiguous match yields nothing and falls through to the next rule rather than
 * selecting an arbitrary track.
 */
internal class SubtitleTrackSelector {

    fun select(
        track: SubtitleTrackUIState,
        candidates: List<PlayerTextTrack>,
    ): PlayerTextTrack? {
        if (candidates.isEmpty()) return null
        return matchByTrackGroupId(track, candidates)
            ?: matchByFormatId(track, candidates)
            ?: matchByCoordinates(track, candidates)
            ?: matchByLanguage(track, candidates)
    }

    /** Exact and order independent: survives track groups being added or reordered. */
    private fun matchByTrackGroupId(
        track: SubtitleTrackUIState,
        candidates: List<PlayerTextTrack>,
    ): PlayerTextTrack? {
        val groupId = track.playerTrackGroupId?.takeIf { it.isNotEmpty() } ?: return null
        val trackIndex = track.playerTrackIndex ?: return null
        return candidates
            .filter { it.groupId == groupId && it.trackIndex == trackIndex }
            .singleOrNull()
    }

    private fun matchByFormatId(
        track: SubtitleTrackUIState,
        candidates: List<PlayerTextTrack>,
    ): PlayerTextTrack? {
        val formatId = track.playerTrackId?.takeIf { it.isNotEmpty() } ?: return null
        return candidates.filter { it.formatId == formatId }.singleOrNull()
    }

    /** Positional fallback for tracks the manifest exposes without any usable identity. */
    private fun matchByCoordinates(
        track: SubtitleTrackUIState,
        candidates: List<PlayerTextTrack>,
    ): PlayerTextTrack? {
        val groupIndex = track.playerGroupIndex ?: return null
        val trackIndex = track.playerTrackIndex ?: return null
        return candidates
            .filter { it.groupIndex == groupIndex && it.trackIndex == trackIndex }
            .singleOrNull()
    }

    private fun matchByLanguage(
        track: SubtitleTrackUIState,
        candidates: List<PlayerTextTrack>,
    ): PlayerTextTrack? {
        if (track.language.isEmpty()) return null
        return candidates
            .filter { candidate ->
                candidate.language?.let { sameSubtitleLanguage(it, track.language) } == true
            }
            .singleOrNull()
    }
}
