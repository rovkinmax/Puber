package com.kino.puber.ui.feature.player.vm

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import com.kino.puber.data.api.models.SubtitleLink
import java.util.Locale

internal class PlaybackMediaItemFactory {

    fun build(streamUrl: String, subtitles: List<SubtitleLink>?): MediaItem {
        val builder = MediaItem.Builder().setUri(streamUrl)
        if (!subtitles.isNullOrEmpty()) {
            val subtitleConfigs = subtitles.map { subtitle ->
                val stableKey = subtitle.url.stableSubtitleKey()
                MediaItem.SubtitleConfiguration.Builder(subtitle.url.toUri())
                    .setMimeType(subtitleMimeType(subtitle.url))
                    .setLanguage(subtitle.lang)
                    .setLabel(stableKey)
                    .setId(stableKey)
                    .build()
            }
            builder.setSubtitleConfigurations(subtitleConfigs)
        }
        return builder.build()
    }

    fun subtitleMimeType(url: String): String {
        val normalizedUrl = url
            .substringBefore('?')
            .substringBefore('#')
            .lowercase(Locale.ROOT)
        return when {
            normalizedUrl.endsWith(".vtt") || normalizedUrl.endsWith(".webvtt") -> MimeTypes.TEXT_VTT
            normalizedUrl.endsWith(".ass") || normalizedUrl.endsWith(".ssa") -> MimeTypes.TEXT_SSA
            normalizedUrl.endsWith(".ttml") || normalizedUrl.endsWith(".xml") -> MimeTypes.APPLICATION_TTML
            else -> MimeTypes.APPLICATION_SUBRIP
        }
    }
}
