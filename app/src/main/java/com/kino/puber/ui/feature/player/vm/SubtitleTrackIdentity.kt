package com.kino.puber.ui.feature.player.vm

import java.net.URI

private const val SUBTITLES_PATH_MARKER = "/subtitles/"

internal fun String.stableSubtitleKey(): String {
    if (isEmpty()) return ""
    val path = runCatching { URI(this).path }.getOrNull()
        ?: substringBefore('?').substringBefore('#')
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
    val path = (runCatching { URI(this).path }.getOrNull()
        ?: substringBefore('?').substringBefore('#'))
        .trim('/')
    return path.takeIf { candidate ->
        candidate.contains('/') || SUBTITLE_FILE_EXTENSION.containsMatchIn(candidate)
    }
}

internal val SUBTITLE_FILE_EXTENSION = Regex(
    pattern = """\.(srt|vtt|webvtt|ass|ssa|ttml|xml)$""",
    option = RegexOption.IGNORE_CASE,
)
