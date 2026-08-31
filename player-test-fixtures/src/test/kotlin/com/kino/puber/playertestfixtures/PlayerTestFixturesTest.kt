package com.kino.puber.playertestfixtures

import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerTestFixturesTest {

    @Test
    fun everyCatalogEntry_isPresentAndMatchesCommittedSha256() {
        val catalog = PlayerTestFixtures.catalog

        assertEquals(FixtureId.entries.toSet(), catalog.all.map(FixtureMetadata::id).toSet())
        FixtureId.entries.forEach { fixture ->
            assertTrue("$fixture is missing or changed", PlayerTestFixtures.verifySha256(fixture))
        }
    }

    @Test
    fun everyCommittedPackFile_matchesItsSha256AndExpectedFileSet() {
        val checksums = PlayerTestFixtures.committedChecksums()

        assertEquals(
            setOf(
                "fixture-manifest.properties",
                "hls/audio_english_000.ts",
                "hls/audio_english_001.ts",
                "hls/audio_english_002.ts",
                "hls/audio_english.m3u8",
                "hls/audio_spanish_000.ts",
                "hls/audio_spanish_001.ts",
                "hls/audio_spanish_002.ts",
                "hls/audio_spanish.m3u8",
                "hls/master.m3u8",
                "hls/video_360_000.ts",
                "hls/video_360_001.ts",
                "hls/video_360.m3u8",
                "hls/video_720_000.ts",
                "hls/video_720_001.ts",
                "hls/video_720.m3u8",
                "progressive.mp4",
                "subtitle.vtt",
            ),
            checksums.keys,
        )
        checksums.forEach { (path, expected) ->
            val actual = MessageDigest.getInstance("SHA-256")
                .digest(PlayerTestFixtures.openPath(path).use { it.readBytes() })
                .joinToString("") { "%02x".format(it) }
            assertEquals(path, expected, actual)
        }
    }

    @Test
    fun catalog_containsPlaybackTracksAndKnownSubtitleCue() {
        val catalog = PlayerTestFixtures.catalog

        assertEquals(4_000L, catalog.metadata(FixtureId.ProgressiveMp4).durationMs)
        assertEquals("en", catalog.metadata(FixtureId.HlsAudioEnglishPlaylist).language)
        assertEquals("Español", catalog.metadata(FixtureId.HlsAudioSpanishPlaylist).label)
        assertEquals(1_000L, catalog.metadata(FixtureId.SubtitleWebVtt).cueStartMs)
        assertEquals(2_000L, catalog.metadata(FixtureId.SubtitleWebVtt).cueEndMs)
    }

    @Test
    fun hlsMaster_referencesBothVideoVariantsAndAudioRenditions() {
        val master = PlayerTestFixtures.readBytes(FixtureId.HlsMaster).decodeToString()

        assertTrue(master.contains("video_360.m3u8"))
        assertTrue(master.contains("video_720.m3u8"))
        assertTrue(master.contains("RESOLUTION=640x360"))
        assertTrue(master.contains("RESOLUTION=1280x720"))
        assertTrue(master.contains("audio_english.m3u8"))
        assertTrue(master.contains("audio_spanish.m3u8"))
    }

    @Test
    fun everyHlsPlaylist_referencesCommittedSegments() {
        listOf(
            FixtureId.HlsVideo360Playlist,
            FixtureId.HlsVideo720Playlist,
            FixtureId.HlsAudioEnglishPlaylist,
            FixtureId.HlsAudioSpanishPlaylist,
        ).forEach { playlist ->
            val segments = PlayerTestFixtures.readBytes(playlist)
                .decodeToString()
                .lineSequence()
                .filter { it.endsWith(".ts") }
                .toList()

            assertTrue("$playlist must contain at least two segments", segments.size >= 2)
            segments.forEach { segment ->
                val path = "${playlist.path.substringBeforeLast('/')}/$segment"
                assertTrue("$path is not in the committed checksum manifest", path in PlayerTestFixtures.committedChecksums())
                PlayerTestFixtures.openPath(path).use { input ->
                    assertTrue("$path is empty", input.readBytes().isNotEmpty())
                }
            }
        }
    }

    @Test
    fun fixtureHashes_useSha256LowercaseHex() {
        PlayerTestFixtures.catalog.all.forEach { metadata ->
            assertEquals(64, metadata.sha256.length)
            assertEquals(metadata.sha256.lowercase(), metadata.sha256)
            assertEquals(
                metadata.sha256,
                MessageDigest.getInstance("SHA-256")
                    .digest(PlayerTestFixtures.readBytes(metadata.id))
                    .joinToString("") { "%02x".format(it) },
            )
        }
    }
}
