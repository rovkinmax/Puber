package com.kino.puber.ui.feature.player.vm

import com.kino.puber.BuildConfig
import com.kino.puber.core.logger.log
import com.kino.puber.data.api.models.SubtitleLink
import com.kino.puber.ui.feature.player.model.SubtitleTrackUIState
import com.kino.puber.ui.feature.player.model.isOff

internal const val SUBTITLE_DIAGNOSTICS_TAG = "SubtitleDiagnostics"

internal object SubtitleDiagnostics {

    fun record(message: String) {
        if (!BuildConfig.DEBUG) return
        this.log(message, tag = SUBTITLE_DIAGNOSTICS_TAG)
    }

    fun recordTracks(stage: String, tracks: List<SubtitleTrackUIState>) {
        if (!BuildConfig.DEBUG) return
        record("$stage count=${tracks.size}")
        tracks.forEachIndexed { index, track ->
            record("$stage position=${index + 1} ${track.subtitleDiagnosticSummary()}")
        }
    }

    fun recordApiPayload(subtitles: List<SubtitleLink>?) {
        if (!BuildConfig.DEBUG) return
        record("api-payload count=${subtitles.orEmpty().size}")
        subtitles.orEmpty().forEachIndexed { index, subtitle ->
            record(
                "api-payload position=${index + 1} " +
                    "lang=${subtitle.lang.subtitleDiagnosticValue()} " +
                    "shift=${subtitle.shift} embed=${subtitle.embed} forced=${subtitle.forced} " +
                    "file=${subtitle.file.subtitleDiagnosticValue()} " +
                    "urlKey=${subtitle.url.stableSubtitleKey().subtitleDiagnosticValue()}",
            )
        }
    }
}

internal fun String?.subtitleDiagnosticValue(): String = when {
    this == null -> "<null>"
    isEmpty() -> "<empty>"
    else -> replace("\n", "\\n").replace("\r", "\\r")
}

internal fun SubtitleTrackUIState.subtitleDiagnosticSummary(): String {
    val origin = when {
        isOff -> "off"
        playerTrackUri != null -> "manifest"
        playerGroupIndex != null -> "side-loaded"
        url.isNotEmpty() -> "api"
        else -> "unknown"
    }
    return buildString {
        append("origin=")
        append(origin)
        append(" label=")
        append(label.subtitleDiagnosticValue())
        append(" language=")
        append(language.subtitleDiagnosticValue())
        append(" descriptive=")
        append(descriptiveLabel.subtitleDiagnosticValue())
        append(" forced=")
        append(isForced)
        append(" playerId=")
        append(playerTrackId.subtitleDiagnosticValue())
        append(" groupId=")
        append(playerTrackGroupId.subtitleDiagnosticValue())
        append(" position=")
        append(playerGroupIndex)
        append(':')
        append(playerTrackIndex)
    }
}
