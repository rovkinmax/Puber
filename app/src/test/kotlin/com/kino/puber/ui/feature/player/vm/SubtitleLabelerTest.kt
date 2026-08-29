package com.kino.puber.ui.feature.player.vm

import com.kino.puber.ui.feature.player.model.SubtitleTrackUIState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class SubtitleLabelerTest {

    private val labeler = SubtitleLabeler(
        displayLanguageTag = "ru",
        forcedQualifier = "частичные",
        variantLabel = { label, ordinal -> "$label · вариант $ordinal" },
        unknownLabel = { position -> "Субтитры $position" },
    )

    @Test
    fun apply_namesRowsByLocalizedLanguage_forTwoAndThreeLetterCodes() {
        val labels = labeler.apply(
            listOf(track("rus"), track("en"), track("spa"), track("uk")),
        ).map { it.label }

        assertEquals(listOf("Русский", "Английский", "Испанский", "Украинский"), labels)
    }

    @Test
    fun apply_marksPartialTrack() {
        val labels = labeler.apply(listOf(track("rus"), track("rus", forced = true)))
            .map { it.label }

        assertEquals(listOf("Русский", "Русский · частичные"), labels)
    }

    @Test
    fun apply_neverShowsSubtitleFileNames() {
        val labels = labeler.apply(
            listOf(
                track("spa", descriptive = "29725.srt"),
                track("spa", descriptive = "29727.srt"),
            ),
        ).map { it.label }

        assertEquals(listOf("Испанский · вариант 1", "Испанский · вариант 2"), labels)
    }

    @Test
    fun apply_neverShowsCdnChannelNumbering() {
        val labels = labeler.apply(
            listOf(
                track("rus", descriptive = "RUS #03"),
                track("rus", descriptive = "RUS #05"),
            ),
        ).map { it.label }

        assertEquals(listOf("Русский · вариант 1", "Русский · вариант 2"), labels)
    }

    @Test
    fun apply_usesManifestLabels_whenTheyReadAsNamesAndSeparateTheRows() {
        val labels = labeler.apply(
            listOf(
                track("rus", descriptive = "Русские полные"),
                track("rus", descriptive = "Русские SDH"),
            ),
        ).map { it.label }

        assertEquals(listOf("Русские полные", "Русские SDH"), labels)
    }

    @Test
    fun apply_keepsPartialMarker_whenAManifestLabelSeparatesTheRows() {
        val labels = labeler.apply(
            listOf(
                track("rus", descriptive = "Русские полные", forced = true),
                track("rus", descriptive = "Русские SDH", forced = true),
            ),
        ).map { it.label }

        assertEquals(
            listOf("Русские полные · частичные", "Русские SDH · частичные"),
            labels,
        )
    }

    @Test
    fun apply_doesNotQualifyRowsThatAlreadyReadDifferently() {
        val labels = labeler.apply(
            listOf(track("rus", descriptive = "RUS #03"), track("eng", descriptive = "ENG #01")),
        ).map { it.label }

        assertEquals(listOf("Русский", "Английский"), labels)
    }

    @Test
    fun apply_fallsBackToPositionalName_whenNothingIdentifiesTheLanguage() {
        val labels = labeler.apply(listOf(track(""), track("", descriptive = "Комментарии режиссёра")))
            .map { it.label }

        assertEquals(listOf("Субтитры 1", "Комментарии режиссёра"), labels)
    }

    @Test
    fun apply_leavesUnknownLanguageCodeVisible_ratherThanInventingAName() {
        val labels = labeler.apply(listOf(track("qqq"))).map { it.label }

        assertEquals(listOf("Субтитры 1"), labels)
    }

    private fun track(
        language: String,
        descriptive: String? = null,
        forced: Boolean = false,
    ) = SubtitleTrackUIState(
        label = "",
        language = language,
        url = "",
        isForced = forced,
        descriptiveLabel = descriptive,
    )
}
