package com.kino.puber.ui.feature.player.vm

import com.kino.puber.ui.feature.player.model.AudioTrackUIState
import com.kino.puber.ui.feature.player.model.SubtitleTrackUIState
import com.kino.puber.ui.feature.player.model.isOff

internal class AudioTrackPreferenceResolver {

    fun findAudioTrackIndex(
        tracks: List<AudioTrackUIState>,
        preferredLabel: String?,
        preferredLang: String?,
    ): Int {
        val matchers = listOf(
            { exactLabelMatch(tracks, preferredLabel) },
            { normalizedLabelMatch(tracks, preferredLabel) },
            { voiceTypeAndLanguageMatch(tracks, preferredLabel, preferredLang) },
            { languageMatch(tracks, preferredLang) },
        )
        return matchers.firstNotNullOfOrNull { matcher ->
            matcher().takeIf { it >= 0 }
        } ?: NO_MATCH
    }

    fun findSubtitleTrackIndex(
        tracks: List<SubtitleTrackUIState>,
        preferredLang: String?,
        preferredUrl: String?,
        preferredPlayerTrackId: String? = null,
        preferredPlayerGroupIndex: Int? = null,
        preferredPlayerTrackIndex: Int? = null,
        preferredPlayerTrackGroupId: String? = null,
        preferredIsForced: Boolean? = null,
        preferredDescriptiveLabel: String? = null,
        allowPositionFallback: Boolean = false,
    ): Int {
        // Empty language means Off only when there is no actual track identity.
        val hasIdentity = listOf(preferredUrl, preferredPlayerTrackId, preferredPlayerTrackGroupId)
            .any { !it.isNullOrEmpty() }
        val hasCoordinates = preferredPlayerGroupIndex != null || preferredPlayerTrackIndex != null
        if (preferredLang == "" && !hasIdentity && !hasCoordinates) {
            return tracks.indexOfFirst { it.isOff }
        }
        val compatibleTracks = tracks.withIndex().filter { (_, track) ->
            track.isOff || track.isSemanticallyCompatibleWith(
                preferredLang = preferredLang,
                preferredIsForced = preferredIsForced,
                preferredDescriptiveLabel = preferredDescriptiveLabel,
            )
        }
        val matchers = listOf(
            { subtitleIdentityMatch(compatibleTracks, preferredUrl) },
            { subtitleIdentityMatch(compatibleTracks, preferredPlayerTrackId) },
            {
                compatibleTracks.filter { (_, track) ->
                    !preferredPlayerTrackGroupId.isNullOrEmpty() &&
                        track.playerTrackGroupId == preferredPlayerTrackGroupId &&
                        track.playerTrackIndex == preferredPlayerTrackIndex
                }.singleOrNull()?.index ?: NO_MATCH
            },
            {
                if (allowPositionFallback) {
                    playerCoordinatesMatch(
                        compatibleTracks,
                        preferredPlayerGroupIndex,
                        preferredPlayerTrackIndex,
                    )
                } else {
                    NO_MATCH
                }
            },
            { subtitleDescriptiveLabelMatch(compatibleTracks, preferredDescriptiveLabel) },
            { subtitleLanguageMatch(compatibleTracks, preferredLang) },
        )
        return matchers.firstNotNullOfOrNull { matcher ->
            matcher().takeIf { it >= 0 }
        } ?: NO_MATCH
    }

    private fun subtitleIdentityMatch(
        tracks: List<IndexedValue<SubtitleTrackUIState>>,
        preferredIdentity: String?,
    ): Int {
        if (preferredIdentity.isNullOrEmpty()) return NO_MATCH
        return tracks.filter { (_, track) ->
            track.identities.any { identity -> sameSubtitleIdentity(identity, preferredIdentity) }
        }.singleOrNull()?.index ?: NO_MATCH
    }

    private fun subtitleDescriptiveLabelMatch(
        tracks: List<IndexedValue<SubtitleTrackUIState>>,
        preferredLabel: String?,
    ): Int {
        val normalizedLabel = preferredLabel?.normalizedPreferenceLabel() ?: return NO_MATCH
        return tracks.filter { (_, track) ->
            track.readableDescriptiveLabel()?.normalizedPreferenceLabel() == normalizedLabel
        }.singleOrNull()?.index ?: NO_MATCH
    }

    private fun playerCoordinatesMatch(
        tracks: List<IndexedValue<SubtitleTrackUIState>>,
        preferredGroupIndex: Int?,
        preferredTrackIndex: Int?,
    ): Int {
        if (preferredGroupIndex == null || preferredTrackIndex == null) return NO_MATCH
        return tracks.filter { (_, track) ->
            track.playerGroupIndex == preferredGroupIndex &&
                track.playerTrackIndex == preferredTrackIndex
        }.singleOrNull()?.index ?: NO_MATCH
    }

    private fun exactLabelMatch(
        tracks: List<AudioTrackUIState>,
        preferredLabel: String?,
    ): Int {
        if (preferredLabel == null) return NO_MATCH
        return tracks.indexOfFirst { it.label == preferredLabel }
    }

    private fun normalizedLabelMatch(
        tracks: List<AudioTrackUIState>,
        preferredLabel: String?,
    ): Int {
        if (preferredLabel == null) return NO_MATCH
        val coreLabel = preferredLabel.withoutNumberPrefix()
        return tracks.indexOfFirst { it.label.withoutNumberPrefix() == coreLabel }
    }

    private fun voiceTypeAndLanguageMatch(
        tracks: List<AudioTrackUIState>,
        preferredLabel: String?,
        preferredLang: String?,
    ): Int {
        if (preferredLabel == null || preferredLang == null) return NO_MATCH
        val savedType = extractVoiceType(preferredLabel) ?: return NO_MATCH
        return tracks.indexOfFirst { track ->
            extractVoiceType(track.label) == savedType && track.language == preferredLang
        }
    }

    private fun languageMatch(
        tracks: List<AudioTrackUIState>,
        preferredLang: String?,
    ): Int {
        if (preferredLang == null) return NO_MATCH
        return tracks.indexOfFirst { it.language == preferredLang }
    }

    private fun subtitleLanguageMatch(
        tracks: List<IndexedValue<SubtitleTrackUIState>>,
        preferredLang: String?,
    ): Int {
        if (preferredLang.isNullOrEmpty()) return NO_MATCH
        val matches = tracks.filter {
            sameSubtitleLanguage(it.value.language, preferredLang)
        }
        val manifestMatches = matches.filter { it.value.playerTrackId != null }
        return manifestMatches.singleOrNull()?.index ?: matches.singleOrNull()?.index ?: NO_MATCH
    }

    /** Extracts voice type from HLS labels like "03. Многоголосый. Red Head Sound (RUS)". */
    fun extractVoiceType(label: String): String? {
        val core = label.withoutNumberPrefix()
        val withoutLang = core.substringBeforeLast(" (")
        return withoutLang.substringBefore(". ").trim().takeIf { it.isNotEmpty() }
    }

    private fun String.withoutNumberPrefix(): String {
        return replace(NUMBER_PREFIX_REGEX, "")
    }

    private companion object {
        const val NO_MATCH = -1
        val NUMBER_PREFIX_REGEX = Regex("""^\d+\.\s*""")
    }
}

private fun SubtitleTrackUIState.isSemanticallyCompatibleWith(
    preferredLang: String?,
    preferredIsForced: Boolean?,
    preferredDescriptiveLabel: String?,
): Boolean {
    val languageMatches = preferredLang.isNullOrEmpty() ||
        sameSubtitleLanguage(language, preferredLang)
    val forcedMatches = preferredIsForced == null || isForced == preferredIsForced
    val preferredLabel = preferredDescriptiveLabel?.normalizedPreferenceLabel()
    val candidateLabel = readableDescriptiveLabel()?.normalizedPreferenceLabel()
    val labelMatches = preferredLabel == null || preferredLabel == candidateLabel
    return languageMatches && forcedMatches && labelMatches
}

private fun String.normalizedPreferenceLabel(): String? =
    trim().lowercase().takeIf { it.isNotEmpty() }

private val SubtitleTrackUIState.identities: List<String>
    get() = listOfNotNull(url, sourceFile, playerTrackUri, playerTrackId, playerTrackGroupId)
        .filter { it.isNotEmpty() }
