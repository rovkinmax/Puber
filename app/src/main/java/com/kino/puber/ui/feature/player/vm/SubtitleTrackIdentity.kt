package com.kino.puber.ui.feature.player.vm

import java.net.URI

private const val SUBTITLES_PATH_MARKER = "/subtitles/"

internal fun String.stableSubtitleKey(): String {
    if (isEmpty()) return ""
    val path = normalizedSubtitlePath()
    return path.substringAfter(SUBTITLES_PATH_MARKER, path)
}

internal fun sameSubtitleIdentity(first: String, second: String): Boolean {
    if (first == second) return true
    val firstPath = first.subtitleIdentityPathOrNull()
    val secondPath = second.subtitleIdentityPathOrNull()
    return firstPath != null && secondPath != null && (
        firstPath == secondPath ||
            firstPath.endsWith("/$secondPath") ||
            secondPath.endsWith("/$firstPath") ||
            firstPath.stableSubtitleKey() == secondPath.stableSubtitleKey()
        )
}

private fun String.subtitleIdentityPathOrNull(): String? {
    if (isEmpty()) return null
    val path = normalizedSubtitlePath().trim('/')
    return path.takeIf { candidate ->
        candidate.contains('/') || SUBTITLE_FILE_EXTENSION.containsMatchIn(candidate)
    }
}

/** KinoPub exposes an HLS rendition for a subtitle file as `<file>/index.m3u8`. */
private fun String.normalizedSubtitlePath(): String {
    val path = runCatching { URI(this).path }.getOrNull()
        ?: substringBefore('?').substringBefore('#')
    return HLS_SUBTITLE_PLAYLIST_SUFFIX.replace(path) { match -> match.groupValues[1] }
}

internal val SUBTITLE_FILE_EXTENSION = Regex(
    pattern = """\.(srt|vtt|webvtt|ass|ssa|ttml|xml)$""",
    option = RegexOption.IGNORE_CASE,
)

private val HLS_SUBTITLE_PLAYLIST_SUFFIX = Regex(
    pattern = """(\.(?:srt|vtt|webvtt|ass|ssa|ttml|xml))/index\.m3u8$""",
    option = RegexOption.IGNORE_CASE,
)
