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
    ): Int {
        val matchers = listOf(
            { subtitleIdentityMatch(tracks, preferredUrl) },
            { subtitleIdentityMatch(tracks, preferredPlayerTrackId) },
            {
                playerCoordinatesMatch(
                    tracks,
                    preferredPlayerGroupIndex,
                    preferredPlayerTrackIndex,
                )
            },
            { subtitleLanguageMatch(tracks, preferredLang) },
        )
        return matchers.firstNotNullOfOrNull { matcher ->
            matcher().takeIf { it >= 0 }
        } ?: NO_MATCH
    }

    private fun subtitleIdentityMatch(
        tracks: List<SubtitleTrackUIState>,
        preferredIdentity: String?,
    ): Int {
        if (preferredIdentity.isNullOrEmpty()) return NO_MATCH
        return tracks.withIndex().filter { (_, track) ->
            track.identities.any { identity -> sameSubtitleIdentity(identity, preferredIdentity) }
        }.singleOrNull()?.index ?: NO_MATCH
    }

    private fun playerCoordinatesMatch(
        tracks: List<SubtitleTrackUIState>,
        preferredGroupIndex: Int?,
        preferredTrackIndex: Int?,
    ): Int {
        if (preferredGroupIndex == null || preferredTrackIndex == null) return NO_MATCH
        return tracks.withIndex().filter { (_, track) ->
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
        tracks: List<SubtitleTrackUIState>,
        preferredLang: String?,
    ): Int {
        if (preferredLang == null) return NO_MATCH
        if (preferredLang.isEmpty()) return tracks.indexOfFirst { it.isOff }
        val matches = tracks.withIndex().filter {
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

private val SubtitleTrackUIState.identities: List<String>
    get() = listOfNotNull(url, sourceFile, playerTrackUri, playerTrackId)
        .filter { it.isNotEmpty() }
