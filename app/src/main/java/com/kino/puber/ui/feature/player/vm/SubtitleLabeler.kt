package com.kino.puber.ui.feature.player.vm

import com.kino.puber.ui.feature.player.model.SubtitleTrackUIState
import java.util.Locale

/**
 * Builds the strings the subtitle picker shows.
 *
 * Every row is named after its language. A qualifier is appended only when two rows would
 * otherwise read identically, and raw manifest strings only reach the UI when they are
 * genuinely descriptive: CDN naming such as `RUS #03` and the subtitle file names Media3
 * uses as side-loaded track labels are rejected.
 */
internal class SubtitleLabeler(
    displayLanguageTag: String,
    private val forcedQualifier: String,
    private val variantLabel: (label: String, ordinal: Int) -> String,
    private val unknownLabel: (position: Int) -> String,
) {
    private val displayLocale: Locale = Locale.forLanguageTag(displayLanguageTag)

    fun apply(tracks: List<SubtitleTrackUIState>): List<SubtitleTrackUIState> {
        val labeled = tracks.mapIndexed { position, track ->
            track.copy(label = withForcedQualifier(baseLabel(track, position), track))
        }
        return disambiguate(labeled)
    }

    private fun baseLabel(track: SubtitleTrackUIState, position: Int): String =
        subtitleLanguageDisplayName(track.language, displayLocale)
            ?: track.readableDescriptiveLabel()
            ?: unknownLabel(position + 1)

    private fun withForcedQualifier(label: String, track: SubtitleTrackUIState): String =
        if (track.isForced == true) "$label$QUALIFIER_SEPARATOR$forcedQualifier" else label

    /**
     * Rows that still read the same are separated by their manifest labels when every one
     * of them has a distinct readable label, and by an ordinal otherwise.
     */
    private fun disambiguate(tracks: List<SubtitleTrackUIState>): List<SubtitleTrackUIState> {
        val collisions = tracks.groupBy { it.label }.filterValues { it.size > 1 }
        if (collisions.isEmpty()) return tracks

        val describable = collisions.filterValues(::hasDistinctReadableLabels).keys
        val ordinals = mutableMapOf<String, Int>()
        return tracks.map { track ->
            when {
                track.label !in collisions -> track
                track.label in describable ->
                    track.copy(label = withForcedQualifier(track.readableDescriptiveLabel()!!, track))
                else -> {
                    val ordinal = ordinals.merge(track.label, 1, Int::plus) ?: 1
                    track.copy(label = variantLabel(track.label, ordinal))
                }
            }
        }
    }

    private fun hasDistinctReadableLabels(group: List<SubtitleTrackUIState>): Boolean {
        val labels = group.mapNotNull { it.readableDescriptiveLabel() }
        return labels.size == group.size && labels.toSet().size == group.size
    }

    private companion object {
        const val QUALIFIER_SEPARATOR = " · "
    }
}

/**
 * A manifest label is worth showing only when it reads as a name. Anything that is really
 * a file name or a language code with a channel number is not.
 */
internal fun SubtitleTrackUIState.readableDescriptiveLabel(): String? {
    val candidate = descriptiveLabel?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val isFileName = SUBTITLE_FILE_EXTENSION.containsMatchIn(candidate) ||
        candidate == sourceFile ||
        candidate == url.stableSubtitleKey()
    val isLanguageCode = candidate.count(Char::isLetter) <= SHORTEST_DESCRIPTIVE_LABEL
    return candidate.takeUnless { isFileName || isLanguageCode }
}

private const val SHORTEST_DESCRIPTIVE_LABEL = 3
