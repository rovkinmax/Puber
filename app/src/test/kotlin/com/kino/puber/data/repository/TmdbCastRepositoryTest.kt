package com.kino.puber.data.repository

import com.kino.puber.data.api.TmdbApiClient
import com.kino.puber.data.api.models.TmdbAggregateCreditsResponse
import com.kino.puber.data.api.models.TmdbCastCredit
import com.kino.puber.data.api.models.TmdbConfigurationResponse
import com.kino.puber.data.api.models.TmdbCreditsResponse
import com.kino.puber.data.api.models.TmdbImageConfiguration
import com.kino.puber.data.api.models.TmdbMediaKind
import com.kino.puber.data.api.models.TmdbMediaRef
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class TmdbCastRepositoryTest {

    @Test
    fun bareNumericMovieIdUsesMovieCreditsAndBuildsConfiguredProfileUrl() = runTest {
        val client = configuredClient()
        coEvery { client.findMediaByImdbId("tt0000123") } returns Result.success(
            TmdbMediaRef(id = 7, kind = TmdbMediaKind.MOVIE),
        )
        coEvery { client.getMovieCredits(7) } returns Result.success(
            TmdbCreditsResponse(
                cast = listOf(TmdbCastCredit(name = "  Actor One  ", profilePath = "/one.jpg")),
            ),
        )
        coEvery { client.getConfiguration() } returns Result.success(configuration())
        val repository = TmdbCastRepository(client)

        assertEquals(
            listOf("Actor One" to "https://image.tmdb.org/t/p/w185/one.jpg"),
            repository.getCast("0000123").map { it.name to it.profileUrl },
        )
        coVerify(exactly = 1) { client.findMediaByImdbId("tt0000123") }
        coVerify(exactly = 1) { client.getMovieCredits(7) }
        coVerify(exactly = 0) { client.getTvAggregateCredits(any()) }
    }

    @Test
    fun tvUsesAggregateCredits() = runTest {
        val client = configuredClient()
        coEvery { client.findMediaByImdbId("tt0000456") } returns Result.success(
            TmdbMediaRef(id = 8, kind = TmdbMediaKind.TV),
        )
        coEvery { client.getTvAggregateCredits(8) } returns Result.success(
            TmdbAggregateCreditsResponse(
                cast = listOf(TmdbCastCredit(name = "Actor Two", profilePath = null)),
            ),
        )
        coEvery { client.getConfiguration() } returns Result.success(configuration())
        val repository = TmdbCastRepository(client)

        assertEquals(listOf("Actor Two" to null), repository.getCast("tt0000456").map { it.name to it.profileUrl })
        coVerify(exactly = 1) { client.getTvAggregateCredits(8) }
        coVerify(exactly = 0) { client.getMovieCredits(any()) }
    }

    @Test
    fun repeatedRequestUsesShortLivedCastAndConfigurationCaches() = runTest {
        val client = configuredClient()
        coEvery { client.findMediaByImdbId("tt0000789") } returns Result.success(
            TmdbMediaRef(id = 9, kind = TmdbMediaKind.MOVIE),
        )
        coEvery { client.getMovieCredits(9) } returns Result.success(
            TmdbCreditsResponse(listOf(TmdbCastCredit(name = "Actor"))),
        )
        coEvery { client.getConfiguration() } returns Result.success(configuration())
        val repository = TmdbCastRepository(client)

        repository.getCast("tt0000789")
        repository.getCast("TT0000789")

        coVerify(exactly = 1) { client.findMediaByImdbId("tt0000789") }
        coVerify(exactly = 1) { client.getMovieCredits(9) }
        coVerify(exactly = 1) { client.getConfiguration() }
    }

    @Test
    fun invalidInputAndEmptyTokenSkipRemoteCalls() = runTest {
        val client = mockk<TmdbApiClient>()
        every { client.isConfigured } returns false
        val repository = TmdbCastRepository(client)

        assertTrue(repository.getCast(" ").isEmpty())
        assertTrue(repository.getCast("tt0000123").isEmpty())
        coVerify(exactly = 0) { client.findMediaByImdbId(any()) }
    }

    @Test
    fun malformedNonBlankImdbTitleIdsKeepPlaceholdersAndSkipRemoteCalls() = runTest {
        val client = configuredClient()
        coEvery { client.findMediaByImdbId(any()) } returns Result.success(null)
        val repository = TmdbCastRepository(client)

        val malformedIds = listOf(
            "tt123",
            "123456",
            "nm0000123",
            "tt123456x",
            "tt1234567-extra",
        )

        assertTrue(malformedIds.all { imdbId -> repository.getCast(imdbId).isEmpty() })
        coVerify(exactly = 0) { client.findMediaByImdbId(any()) }
        coVerify(exactly = 0) { client.getMovieCredits(any()) }
        coVerify(exactly = 0) { client.getTvAggregateCredits(any()) }
        coVerify(exactly = 0) { client.getConfiguration() }
    }

    @Test
    fun failedFindOrCreditsReturnEmptyCast() = runTest {
        val client = configuredClient()
        coEvery { client.findMediaByImdbId("tt0001001") } returns Result.failure(IllegalStateException())
        coEvery { client.findMediaByImdbId("tt0001002") } returns Result.success(null)
        coEvery { client.findMediaByImdbId("tt0001003") } returns Result.success(
            TmdbMediaRef(id = 11, kind = TmdbMediaKind.MOVIE),
        )
        coEvery { client.getMovieCredits(11) } returns Result.failure(IllegalStateException())
        val repository = TmdbCastRepository(client)

        assertTrue(repository.getCast("tt0001001").isEmpty())
        assertTrue(repository.getCast("tt0001002").isEmpty())
        assertTrue(repository.getCast("tt0001003").isEmpty())
    }

    @Test
    fun configurationFailureKeepsNamesWithNoPhoto() = runTest {
        val client = configuredClient()
        coEvery { client.findMediaByImdbId("tt0002001") } returns Result.success(
            TmdbMediaRef(id = 10, kind = TmdbMediaKind.TV),
        )
        coEvery { client.getTvAggregateCredits(10) } returns Result.success(
            TmdbAggregateCreditsResponse(
                cast = listOf(
                    TmdbCastCredit(name = "Actor One", profilePath = "/one.jpg"),
                    TmdbCastCredit(name = null, profilePath = "/ignored.jpg"),
                ),
            ),
        )
        coEvery { client.getConfiguration() } returns Result.failure(IllegalStateException())
        val repository = TmdbCastRepository(client)

        assertEquals(listOf("Actor One" to null), repository.getCast("tt0002001").map { it.name to it.profileUrl })
    }

    @Test
    fun configuredProfileUrl_preservesAbsolutePathAndUsesSharedPreferredSize() = runTest {
        val client = configuredClient()
        coEvery { client.findMediaByImdbId("tt0003001") } returns Result.success(
            TmdbMediaRef(id = 12, kind = TmdbMediaKind.MOVIE),
        )
        coEvery { client.getMovieCredits(12) } returns Result.success(
            TmdbCreditsResponse(
                cast = listOf(
                    TmdbCastCredit(name = "Absolute", profilePath = "https://cdn.example/actor.jpg"),
                    TmdbCastCredit(name = "Relative", profilePath = "/relative.jpg"),
                ),
            ),
        )
        coEvery { client.getConfiguration() } returns Result.success(
            TmdbConfigurationResponse(
                images = TmdbImageConfiguration(
                    secureBaseUrl = "https://image.tmdb.org/t/p/",
                    profileSizes = listOf("w500", "w185"),
                ),
            ),
        )

        assertEquals(
            listOf(
                "https://cdn.example/actor.jpg",
                "https://image.tmdb.org/t/p/w185/relative.jpg",
            ),
            TmdbCastRepository(client).getCast("tt0003001").map { it.profileUrl },
        )
    }

    @Test
    fun coldLoad_doesNotRunApiContinuationsOnCallerThread() = runTest {
        val client = configuredClient()
        val callbackThreads = mutableListOf<String>()
        coEvery { client.findMediaByImdbId("tt0004001") } coAnswers {
            callbackThreads += Thread.currentThread().name
            Result.success(TmdbMediaRef(id = 13, kind = TmdbMediaKind.MOVIE))
        }
        coEvery { client.getMovieCredits(13) } coAnswers {
            callbackThreads += Thread.currentThread().name
            Result.success(TmdbCreditsResponse(listOf(TmdbCastCredit(name = "Actor"))))
        }
        coEvery { client.getConfiguration() } coAnswers {
            callbackThreads += Thread.currentThread().name
            Result.success(configuration())
        }
        val repository = TmdbCastRepository(client)
        val callerDispatcher = Executors
            .newSingleThreadExecutor { runnable -> Thread(runnable, "details-main") }
            .asCoroutineDispatcher()

        try {
            withContext(callerDispatcher) {
                repository.getCast("tt0004001")
            }
        } finally {
            callerDispatcher.close()
        }

        assertTrue(callbackThreads.isNotEmpty())
        assertTrue(callbackThreads.none { it == "details-main" }, callbackThreads.toString())
    }

    @Test
    fun concurrentNormalizedRequests_shareOneColdLoad() = runTest {
        val client = configuredClient()
        val loadStarted = CompletableDeferred<Unit>()
        val releaseLoad = CompletableDeferred<Unit>()
        var findCalls = 0
        coEvery { client.findMediaByImdbId("tt0005001") } coAnswers {
            findCalls += 1
            loadStarted.complete(Unit)
            releaseLoad.await()
            Result.success(TmdbMediaRef(id = 14, kind = TmdbMediaKind.MOVIE))
        }
        coEvery { client.getMovieCredits(14) } returns
            Result.success(TmdbCreditsResponse(listOf(TmdbCastCredit(name = "Actor"))))
        coEvery { client.getConfiguration() } returns Result.success(configuration())
        val repository = TmdbCastRepository(client)

        val first = async(start = CoroutineStart.UNDISPATCHED) {
            repository.getCast("tt0005001")
        }
        loadStarted.await()
        val second = async(start = CoroutineStart.UNDISPATCHED) {
            repository.getCast("TT0005001")
        }
        releaseLoad.complete(Unit)
        runCurrent()

        assertEquals(listOf("Actor"), first.await().map { it.name })
        assertEquals(listOf("Actor"), second.await().map { it.name })
        assertEquals(1, findCalls)
    }

    @Test
    fun cancelledColdLoad_propagatesAndSubsequentRequestRetries() = runTest {
        val client = configuredClient()
        val firstCallStarted = CompletableDeferred<Unit>()
        var findCalls = 0
        coEvery { client.findMediaByImdbId("tt0006001") } coAnswers {
            findCalls += 1
            if (findCalls == 1) {
                firstCallStarted.complete(Unit)
                awaitCancellation()
            }
            Result.success(TmdbMediaRef(id = 15, kind = TmdbMediaKind.MOVIE))
        }
        coEvery { client.getMovieCredits(15) } returns
            Result.success(TmdbCreditsResponse(listOf(TmdbCastCredit(name = "Recovered actor"))))
        coEvery { client.getConfiguration() } returns Result.success(configuration())
        val repository = TmdbCastRepository(client)

        val cancelled = async(start = CoroutineStart.UNDISPATCHED) {
            repository.getCast("tt0006001")
        }
        firstCallStarted.await()
        cancelled.cancelAndJoin()

        assertEquals(
            listOf("Recovered actor"),
            repository.getCast("tt0006001").map { it.name },
        )
        assertEquals(2, findCalls)
    }

    private fun configuredClient(): TmdbApiClient {
        val client = mockk<TmdbApiClient>()
        every { client.isConfigured } returns true
        return client
    }

    private fun configuration() = TmdbConfigurationResponse(
        images = TmdbImageConfiguration(
            secureBaseUrl = "https://image.tmdb.org/t/p/",
            profileSizes = listOf("w92", "w185"),
        ),
    )
}
