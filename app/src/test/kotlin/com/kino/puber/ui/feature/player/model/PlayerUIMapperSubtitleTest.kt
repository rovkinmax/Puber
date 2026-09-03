package com.kino.puber.ui.feature.player.model

import android.content.Context
import com.kino.puber.R
import com.kino.puber.data.api.models.SubtitleLink
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class PlayerUIMapperSubtitleTest {

    private val context = mockk<Context>(relaxed = true).also { context ->
        every { context.getString(R.string.player_subtitles_off) } returns "Off"
    }
    private val mapper = PlayerUIMapper(context)

    @Test
    fun mapSubtitleTracks_returnsOnlyOffTrack_whenInputIsEmpty() {
        val result = mapper.mapSubtitleTracks(emptyList())

        assertEquals(listOf(""), result.map { it.language })
        assertEquals(listOf(""), result.map { it.url })
    }

    @Test
    fun mapSubtitleTracks_preservesLanguagesAndIdentityMetadata_inApiOrder() {
        val embeddedUrl = "https://cdn.test/subtitles/russian.vtt"
        val externalUrl = "https://cdn.test/subtitles/english.vtt"

        val result = mapper.mapSubtitleTracks(
            listOf(
                SubtitleLink(
                    lang = "rus",
                    url = embeddedUrl,
                    embed = true,
                    forced = false,
                    file = "/a/71/russian.vtt",
                ),
                SubtitleLink(lang = "eng", url = externalUrl, embed = false),
            )
        )

        assertEquals(listOf("", "rus", "eng"), result.map { it.language })
        assertEquals(listOf("", embeddedUrl, externalUrl), result.map { it.url })
        assertEquals(listOf(null, "/a/71/russian.vtt", null), result.map { it.sourceFile })
        assertEquals(listOf(null, false, null), result.map { it.isForced })
    }

}
