package com.kino.puber.ui.feature.player.vm

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import com.kino.puber.data.api.models.SubtitleLink
import com.kino.puber.domain.interactor.player.StreamSource
import java.util.Locale

internal class PlaybackMediaItemFactory {

    fun build(stream: StreamSource, subtitles: List<SubtitleLink>?): MediaItem {
        val builder = MediaItem.Builder().setUri(stream.url)
        if (stream.isHls) {
            builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        }
        // `embed` describes the original source container. KinoPub's generated HLS
        // manifest may omit that track, so HLS keeps every API subtitle as a fallback.
        val subtitleConfigs = subtitles.orEmpty().mapNotNull { subtitle ->
            if (!subtitle.shouldSideLoad(stream.isHls)) return@mapNotNull null
            val stableKey = subtitle.url.stableSubtitleKey()
            MediaItem.SubtitleConfiguration.Builder(subtitle.url.toUri())
                .setMimeType(subtitleMimeType(subtitle.url))
                .setLanguage(subtitle.lang)
                .setLabel(stableKey)
                .setId(stableKey)
                .build()
        }
        if (subtitleConfigs.isNotEmpty()) {
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
