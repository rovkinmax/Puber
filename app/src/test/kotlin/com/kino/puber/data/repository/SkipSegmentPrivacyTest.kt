package com.kino.puber.data.repository

import com.kino.puber.core.di.ScopeModuleManager
import com.kino.puber.data.api.IntroDbAppApiClient
import com.kino.puber.data.api.TheIntroDbApiClient
import com.kino.puber.data.api.TmdbApiClient
import com.kino.puber.ui.feature.player.component.PlayerScreen
import com.kino.puber.ui.feature.player.model.PlayerScreenParams
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import timber.log.Timber

internal class SkipSegmentPrivacyTest {

    @Test
    fun transitiveSkipSegmentRuntimeDoesNotLogMediaIdentity() = runTest {
        val tmdbApiClient = mockk<TmdbApiClient>()
        val theIntroDbApiClient = mockk<TheIntroDbApiClient>()
        val introDbAppApiClient = mockk<IntroDbAppApiClient>()
        val tmdbIdRepository = mockk<TmdbIdRepository>()
        val logTree = CollectingLogTree()
        val service = SkipSegmentService(
            tmdbApiClient = tmdbApiClient,
            introDbClient = theIntroDbApiClient,
            introDbAppClient = introDbAppApiClient,
            tmdbIdRepository = tmdbIdRepository,
            segmentRepository = SkipSegmentRepository(),
        )
        every { tmdbIdRepository.getTmdbId(IMDB_ID) } returns null
        every { tmdbIdRepository.saveTmdbId(IMDB_ID, TMDB_ID) } just runs
        coEvery { tmdbApiClient.findByImdbId(IMDB_ID) } returns Result.success(TMDB_ID)
        coEvery {
            theIntroDbApiClient.getSegments(TMDB_ID, SEASON, EPISODE)
        } returns Result.success(emptyList())
        coEvery {
            introDbAppApiClient.getSegments(IMDB_ID, SEASON, EPISODE)
        } returns Result.success(emptyList())

        Timber.plant(logTree)
        try {
            assertTrue(service.getSegments(IMDB_ID, SEASON, EPISODE).isEmpty())
        } finally {
            Timber.uproot(logTree)
        }

        coVerify(exactly = 1) { tmdbApiClient.findByImdbId(IMDB_ID) }
        coVerify(exactly = 1) {
            theIntroDbApiClient.getSegments(TMDB_ID, SEASON, EPISODE)
        }
        coVerify(exactly = 1) {
            introDbAppApiClient.getSegments(IMDB_ID, SEASON, EPISODE)
        }
        assertTrue(logTree.messages.isEmpty())
    }

    @Test
    fun transitiveSkipSegmentSourcesContainNoApplicationLogging() {
        SKIP_SEGMENT_PATH_FILES.forEach { relativePath ->
            val source = String(
                Files.readAllBytes(resolveSource(relativePath)),
                StandardCharsets.UTF_8,
            )

            assertFalse(
                source.contains("com.kino.puber.core.logger.log"),
                "$relativePath imports application logging",
            )
            assertFalse(
                LOG_CALL_REGEX.containsMatchIn(source),
                "$relativePath emits application logging",
            )
        }
    }

    @Test
    fun parameterizedPlayerScopeDoesNotLogExactMediaIdentity() {
        val playerScopeName = PlayerScreen(
            PlayerScreenParams(
                itemId = ITEM_ID,
                seasonNumber = SEASON,
                episodeNumber = EPISODE,
                videoNumber = VIDEO_NUMBER,
            ),
        ).key
        val logTree = CollectingLogTree()
        val manager = ScopeModuleManager(
            scopeName = playerScopeName,
            moduleFactory = { _, _ -> mockk() },
            parentScope = mockk(),
            koin = mockk(),
        )

        Timber.plant(logTree)
        try {
            manager.onRemembered()
            manager.onAbandoned()
            manager.onForgotten()
        } finally {
            Timber.uproot(logTree)
        }

        assertTrue(logTree.messages.isEmpty())
    }

    private fun resolveSource(relativePath: String): Path {
        val workingDirectory = Path.of(System.getProperty("user.dir"))
        val candidates = listOf(
            workingDirectory.resolve("src/main/java").resolve(relativePath),
            workingDirectory.resolve("app/src/main/java").resolve(relativePath),
        )
        return candidates.firstOrNull(Files::exists)
            ?: error("Unable to locate source file: $relativePath")
    }

    private class CollectingLogTree : Timber.Tree() {
        val messages = mutableListOf<String>()

        override fun log(
            priority: Int,
            tag: String?,
            message: String,
            t: Throwable?,
        ) {
            messages += message
        }
    }

    private companion object {
        const val IMDB_ID = "tt-private-history-identity"
        const val TMDB_ID = 848_484
        const val ITEM_ID = 424_242
        const val SEASON = 713
        const val EPISODE = 927
        const val VIDEO_NUMBER = 535
        val LOG_CALL_REGEX = Regex("""\blog\s*\(""")
        val SKIP_SEGMENT_PATH_FILES = listOf(
            "com/kino/puber/ui/feature/player/vm/PlayerVM.kt",
            "com/kino/puber/ui/feature/player/component/PlayerScreen.kt",
            "com/kino/puber/domain/interactor/player/SkipSegmentInteractor.kt",
            "com/kino/puber/core/di/DIScope.kt",
            "com/kino/puber/core/di/ScopeModuleManager.kt",
            "com/kino/puber/data/repository/SkipSegmentService.kt",
            "com/kino/puber/data/api/TmdbApiClient.kt",
            "com/kino/puber/data/api/TheIntroDbApiClient.kt",
            "com/kino/puber/data/api/IntroDbAppApiClient.kt",
        )
    }
}
