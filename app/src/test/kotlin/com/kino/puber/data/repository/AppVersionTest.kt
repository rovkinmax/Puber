package com.kino.puber.data.repository

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppVersionTest {

    @Test
    fun parse_returnsVersion_whenRawVersionHasThreeNumericComponents() {
        val result = AppVersion.parse("1.4.0")

        assertEquals(AppVersion(major = 1, minor = 4, patch = 0), result)
    }

    @Test
    fun parse_returnsVersion_whenRawVersionHasLeadingLowercaseV() {
        val result = AppVersion.parse("v1.4.1")

        assertEquals(AppVersion(major = 1, minor = 4, patch = 1), result)
    }

    @Test
    fun parse_returnsVersion_whenRawVersionHasLeadingUppercaseV() {
        val result = AppVersion.parse("V2.0.3")

        assertEquals(AppVersion(major = 2, minor = 0, patch = 3), result)
    }

    @Test
    fun parse_returnsVersion_whenRawVersionHasSuffix() {
        val result = AppVersion.parse("1.4.0-dev")

        assertEquals(AppVersion(major = 1, minor = 4, patch = 0), result)
    }

    @Test
    fun parse_returnsNull_whenRawVersionHasMalformedTags() {
        val malformedTags = listOf(
            "",
            "v",
            "1.4",
            "1.4.0.1",
            "1.x.0",
            "1..0",
            "1.4.-1",
            "release-1.4.0",
            "1.4.0+build",
        )

        malformedTags.forEach { raw ->
            assertNull(AppVersion.parse(raw), "Expected '$raw' to be rejected")
        }
    }

    @Test
    fun compareTo_ordersVersionsByMajorMinorAndPatch() {
        val olderPatch = AppVersion(major = 1, minor = 4, patch = 0)
        val newerPatch = AppVersion(major = 1, minor = 4, patch = 1)
        val newerMinor = AppVersion(major = 1, minor = 5, patch = 0)
        val newerMajor = AppVersion(major = 2, minor = 0, patch = 0)

        assertTrue(newerPatch > olderPatch)
        assertTrue(newerMinor > newerPatch)
        assertTrue(newerMajor > newerMinor)
        assertFalse(olderPatch > newerMajor)
    }

    @Test
    fun equals_returnsTrue_whenVersionPartsMatch() {
        assertEquals(
            AppVersion(major = 1, minor = 4, patch = 0),
            AppVersion(major = 1, minor = 4, patch = 0),
        )
    }

    @Test
    fun toString_returnsNormalizedSemanticVersion() {
        val result = AppVersion.parse("v1.4.0-dev")

        assertEquals("1.4.0", result.toString())
    }
}
