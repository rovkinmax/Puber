package com.kino.puber.playertestfixtures

import android.content.Context
import java.io.InputStream
import java.io.InputStreamReader
import java.security.MessageDigest
import java.nio.charset.StandardCharsets
import java.util.Properties

/**
 * The single source of truth for the deterministic media pack used by player
 * host and instrumentation tests.
 *
 * The files are packaged as Android assets and JVM resources from the same
 * directory. Production modules do not depend on this test-only library.
 */
object PlayerTestFixtures {

    private const val ROOT = "player-fixtures"
    private const val MANIFEST = "$ROOT/fixture-manifest.properties"
    private const val SHA256SUMS = "$ROOT/SHA256SUMS"

    val catalog: FixtureCatalog by lazy { FixtureCatalog(loadProperties()) }

    fun catalog(context: Context): FixtureCatalog =
        FixtureCatalog(loadProperties(context))

    fun open(fixture: FixtureId, context: Context? = null): InputStream {
        return openPath(fixture.path, context)
    }

    fun openPath(path: String, context: Context? = null): InputStream {
        require(path.isRelativePath()) { "Fixture path must be relative: $path" }
        val resourcePath = "$ROOT/$path"
        return context?.assets?.open(resourcePath)
            ?: checkNotNull(javaClass.classLoader?.getResourceAsStream(resourcePath)) {
                "Fixture resource is not available: $resourcePath"
            }
    }

    fun readBytes(fixture: FixtureId, context: Context? = null): ByteArray =
        open(fixture, context).use(InputStream::readBytes)

    fun verifySha256(fixture: FixtureId, context: Context? = null): Boolean {
        val expected = (context?.let(::catalog) ?: catalog).metadata(fixture).sha256
        val actual = MessageDigest.getInstance("SHA-256")
            .digest(readBytes(fixture, context))
            .toHex()
        return actual == expected
    }

    fun committedChecksums(context: Context? = null): Map<String, String> =
        openPath(SHA256SUMS.removePrefix("$ROOT/"), context).bufferedReader().useLines { lines ->
            lines
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .associate { line ->
                    val (hash, path) = line.split(Regex("\\s+"), limit = 2)
                    path to hash
                }
        }

    private fun loadProperties(context: Context? = null): Properties =
        openPath(MANIFEST.removePrefix("$ROOT/"), context).use { stream ->
            Properties().apply {
                load(InputStreamReader(stream, StandardCharsets.UTF_8))
            }
        }

    private fun ByteArray.toHex(): String =
        joinToString("") { byte -> "%02x".format(byte) }

    private fun String.isRelativePath(): Boolean =
        isNotBlank() && !startsWith('/') && !contains('\n') && !contains('\r') &&
            split('/').none { it == ".." || it.isBlank() }
}

enum class FixtureId(
    val path: String,
) {
    ProgressiveMp4("progressive.mp4"),
    HlsMaster("hls/master.m3u8"),
    HlsVideo360Playlist("hls/video_360.m3u8"),
    HlsVideo720Playlist("hls/video_720.m3u8"),
    HlsAudioEnglishPlaylist("hls/audio_english.m3u8"),
    HlsAudioSpanishPlaylist("hls/audio_spanish.m3u8"),
    SubtitleWebVtt("subtitle.vtt"),
}

data class FixtureMetadata(
    val id: FixtureId,
    val durationMs: Long?,
    val language: String?,
    val label: String?,
    val cueStartMs: Long?,
    val cueEndMs: Long?,
    val sha256: String,
)

class FixtureCatalog internal constructor(
    private val properties: Properties,
) {
    val all: List<FixtureMetadata> = FixtureId.entries.map(::metadata)

    fun metadata(id: FixtureId): FixtureMetadata {
        val prefix = "fixture.${id.name}"
        return FixtureMetadata(
            id = id,
            durationMs = properties.getProperty("$prefix.duration_ms")?.toLongOrNull(),
            language = properties.getProperty("$prefix.language"),
            label = properties.getProperty("$prefix.label"),
            cueStartMs = properties.getProperty("$prefix.cue_start_ms")?.toLongOrNull(),
            cueEndMs = properties.getProperty("$prefix.cue_end_ms")?.toLongOrNull(),
            sha256 = checkNotNull(properties.getProperty("$prefix.sha256")) {
                "Missing SHA-256 for ${id.name}"
            },
        )
    }
}
