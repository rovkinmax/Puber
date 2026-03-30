package com.kino.puber.domain.interactor.player

import com.kino.puber.data.api.models.SkipSegment
import com.kino.puber.data.api.models.SkipSegmentType
import com.kino.puber.data.repository.PlayerPreferencesRepository
import com.kino.puber.data.repository.SkipSegmentService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SkipSegmentInteractorTest {

    private val service = mockk<SkipSegmentService>()
    private val preferences = mockk<PlayerPreferencesRepository>()
    private lateinit var interactor: SkipSegmentInteractor

    @BeforeEach
    fun setup() {
        every { preferences.skipIntroEnabled } returns true
        every { preferences.skipRecapEnabled } returns true
        every { preferences.skipCreditsEnabled } returns true
        interactor = SkipSegmentInteractor(service, preferences)
    }

    // region findActiveSegment

    @Test
    fun findActiveSegment_returnsSegment_whenPositionInsideRange() {
        val segment = SkipSegment(SkipSegmentType.INTRO, startMs = 1000, endMs = 5000)
        val result = interactor.findActiveSegment(listOf(segment), positionMs = 3000)
        assertNotNull(result)
        assertEquals(SkipSegmentType.INTRO, result!!.type)
    }

    @Test
    fun findActiveSegment_returnsNull_whenPositionOutsideRange() {
        val segment = SkipSegment(SkipSegmentType.INTRO, startMs = 1000, endMs = 5000)
        assertNull(interactor.findActiveSegment(listOf(segment), positionMs = 6000))
    }

    @Test
    fun findActiveSegment_returnsNull_whenPositionBeforeRange() {
        val segment = SkipSegment(SkipSegmentType.INTRO, startMs = 1000, endMs = 5000)
        assertNull(interactor.findActiveSegment(listOf(segment), positionMs = 500))
    }

    @Test
    fun findActiveSegment_returnsSegment_whenPositionAtExactStart() {
        val segment = SkipSegment(SkipSegmentType.INTRO, startMs = 1000, endMs = 5000)
        assertNotNull(interactor.findActiveSegment(listOf(segment), positionMs = 1000))
    }

    @Test
    fun findActiveSegment_returnsSegment_whenPositionAtExactEnd() {
        val segment = SkipSegment(SkipSegmentType.INTRO, startMs = 1000, endMs = 5000)
        assertNotNull(interactor.findActiveSegment(listOf(segment), positionMs = 5000))
    }

    @Test
    fun findActiveSegment_returnsNull_whenIntroDisabled() {
        every { preferences.skipIntroEnabled } returns false
        val segment = SkipSegment(SkipSegmentType.INTRO, startMs = 1000, endMs = 5000)
        assertNull(interactor.findActiveSegment(listOf(segment), positionMs = 3000))
    }

    @Test
    fun findActiveSegment_returnsNull_whenRecapDisabled() {
        every { preferences.skipRecapEnabled } returns false
        val segment = SkipSegment(SkipSegmentType.RECAP, startMs = 0, endMs = 3000)
        assertNull(interactor.findActiveSegment(listOf(segment), positionMs = 1000))
    }

    @Test
    fun findActiveSegment_returnsNull_whenCreditsDisabled() {
        every { preferences.skipCreditsEnabled } returns false
        val segment = SkipSegment(SkipSegmentType.CREDITS, startMs = 50000, endMs = 60000)
        assertNull(interactor.findActiveSegment(listOf(segment), positionMs = 55000))
    }

    @Test
    fun findActiveSegment_returnsFirstMatchingSegment_whenMultipleOverlap() {
        val intro = SkipSegment(SkipSegmentType.INTRO, startMs = 0, endMs = 5000)
        val recap = SkipSegment(SkipSegmentType.RECAP, startMs = 0, endMs = 3000)
        val result = interactor.findActiveSegment(listOf(intro, recap), positionMs = 2000)
        assertEquals(SkipSegmentType.INTRO, result!!.type)
    }

    @Test
    fun findActiveSegment_handlesNullEndMs() {
        val segment = SkipSegment(SkipSegmentType.CREDITS, startMs = 50000, endMs = null)
        assertNotNull(interactor.findActiveSegment(listOf(segment), positionMs = 99999))
    }

    @Test
    fun findActiveSegment_returnsNull_whenEmptyList() {
        assertNull(interactor.findActiveSegment(emptyList(), positionMs = 1000))
    }

    // endregion

    // region findCreditsSegment

    @Test
    fun findCreditsSegment_returnsCredits_whenPresent() {
        val intro = SkipSegment(SkipSegmentType.INTRO, startMs = 0, endMs = 5000)
        val credits = SkipSegment(SkipSegmentType.CREDITS, startMs = 50000, endMs = 60000)
        val result = interactor.findCreditsSegment(listOf(intro, credits))
        assertNotNull(result)
        assertEquals(SkipSegmentType.CREDITS, result!!.type)
    }

    @Test
    fun findCreditsSegment_returnsNull_whenNoCreditsSegment() {
        assertNull(interactor.findCreditsSegment(listOf(SkipSegment(SkipSegmentType.INTRO, 0, 5000))))
    }

    @Test
    fun findCreditsSegment_returnsCredits_evenWhenCreditsSettingDisabled() {
        every { preferences.skipCreditsEnabled } returns false
        val credits = SkipSegment(SkipSegmentType.CREDITS, startMs = 50000, endMs = 60000)
        assertNotNull(interactor.findCreditsSegment(listOf(credits)))
    }

    // endregion

    // region isSegmentTypeEnabled

    @Test
    fun isSegmentTypeEnabled_previewUsesIntroSetting() {
        every { preferences.skipIntroEnabled } returns false
        assertEquals(false, interactor.isSegmentTypeEnabled(SkipSegmentType.PREVIEW))
        every { preferences.skipIntroEnabled } returns true
        assertEquals(true, interactor.isSegmentTypeEnabled(SkipSegmentType.PREVIEW))
    }

    @Test
    fun isSegmentTypeEnabled_respectsEachSetting() {
        every { preferences.skipIntroEnabled } returns true
        every { preferences.skipRecapEnabled } returns false
        every { preferences.skipCreditsEnabled } returns true

        assertEquals(true, interactor.isSegmentTypeEnabled(SkipSegmentType.INTRO))
        assertEquals(false, interactor.isSegmentTypeEnabled(SkipSegmentType.RECAP))
        assertEquals(true, interactor.isSegmentTypeEnabled(SkipSegmentType.CREDITS))
    }

    // endregion
}
