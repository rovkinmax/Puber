package com.kino.puber.ui.feature.player.vm

import java.net.URI

private const val SUBTITLES_PATH_MARKER = "/subtitles/"

internal fun String.stableSubtitleKey(): String {
    if (isEmpty()) return ""
    val path = runCatching { URI(this).path }.getOrNull()
        ?: substringBefore('?').substringBefore('#')
    return path.substringAfter(SUBTITLES_PATH_MARKER, path)
}
