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
    ): Int {
        val matchers = listOf(
            { exactSubtitleUrlMatch(tracks, preferredUrl) },
            { stableSubtitleUrlMatch(tracks, preferredUrl) },
            { exactPlayerTrackIdMatch(tracks, preferredPlayerTrackId) },
            { subtitleLanguageMatch(tracks, preferredLang) },
        )
        return matchers.firstNotNullOfOrNull { matcher ->
            matcher().takeIf { it >= 0 }
        } ?: NO_MATCH
    }

    private fun exactSubtitleUrlMatch(
        tracks: List<SubtitleTrackUIState>,
        preferredUrl: String?,
    ): Int {
        if (preferredUrl.isNullOrEmpty()) return NO_MATCH
        return tracks.indexOfFirst { it.url == preferredUrl }
    }

    private fun stableSubtitleUrlMatch(
        tracks: List<SubtitleTrackUIState>,
        preferredUrl: String?,
    ): Int {
        if (preferredUrl.isNullOrEmpty()) return NO_MATCH
        val preferredKey = preferredUrl.stableSubtitleKey()
        return tracks.indexOfFirst { it.url.stableSubtitleKey() == preferredKey }
    }

    private fun exactPlayerTrackIdMatch(
        tracks: List<SubtitleTrackUIState>,
        preferredPlayerTrackId: String?,
    ): Int {
        if (preferredPlayerTrackId.isNullOrEmpty()) return NO_MATCH
        return tracks.indexOfFirst { it.playerTrackId == preferredPlayerTrackId }
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
