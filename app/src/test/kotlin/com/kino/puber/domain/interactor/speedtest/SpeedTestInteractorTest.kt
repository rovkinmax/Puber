package com.kino.puber.domain.interactor.speedtest

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.DeviceResponse
import com.kino.puber.data.api.models.DeviceResponseModel
import com.kino.puber.data.api.models.SettingList
import com.kino.puber.data.api.models.SettingOption
import com.kino.puber.data.api.models.SettingValue
import com.kino.puber.data.api.models.SettingsResponse
import com.kino.puber.domain.interactor.device.IDeviceSettingInteractor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

internal class SpeedTestInteractorTest {

    private val api = mockk<KinoPubApiClient>()
    private val deviceSettings = mockk<IDeviceSettingInteractor>()

    @Test
    fun knownServers_haveSourceDerivedIdsHostCodesAndShardRanges() {
        assertEquals(104_857_600L, SPEED_TEST_EXPECTED_BYTES)
        assertEquals(1, SpeedTestServer.AMSTERDAM.locationId)
        assertEquals("ams", SpeedTestServer.AMSTERDAM.hostCode)
        assertEquals(0..2, SpeedTestServer.AMSTERDAM.shards)
        assertEquals(2, SpeedTestServer.MOSCOW.locationId)
        assertEquals("msk", SpeedTestServer.MOSCOW.hostCode)
        assertEquals(5..6, SpeedTestServer.MOSCOW.shards)
        assertEquals(
            listOf(SpeedTestServer.AMSTERDAM, SpeedTestServer.MOSCOW),
            SpeedTestServer.knownServers,
        )
    }

    @Test
    fun speedMath_usesElapsedSecondsAndMebibits() {
        assertEquals(
            8.0,
            calculateSpeedMbitPerSecond(downloadedBytes = 1_048_576, elapsedMillis = 1_000),
        )
    }

    @Test
    fun buildProbeUrl_containsZeroPaddedShardCacheBusterAndOneHundredMiB() {
        assertEquals(
            "https://speed.ams-static-02.cdntogo.net/speedtest/garbage.php?r=0.25&ckSize=100",
            SpeedTestInteractor.buildProbeUrl(
                server = SpeedTestServer.AMSTERDAM,
                shard = 2,
                randomValue = 0.25,
            ),
        )
        assertEquals(
            "https://speed.msk-static-05.cdntogo.net/speedtest/garbage.php?r=0.5&ckSize=100",
            SpeedTestInteractor.buildProbeUrl(
                server = SpeedTestServer.MOSCOW,
                shard = 5,
                randomValue = 0.5,
            ),
        )
    }

    @Test
    fun serverMetadata_preservesKnownOptionFieldsAndIgnoresUnknownIds() = runTest {
        every { deviceSettings.getCurrentDeviceSettings() } returns flowOf(
            Result.success(
                deviceResponse(
                    serverOptions = listOf(
                        SettingOption(
                            id = SpeedTestServer.AMSTERDAM.locationId,
                            label = "Amsterdam API",
                            selected = 0,
                        ),
                        SettingOption(
                            id = SpeedTestServer.MOSCOW.locationId,
                            label = "Moscow API",
                            selected = 1,
                        ),
                        SettingOption(
                            id = 99,
                            label = "Unknown API",
                            selected = 1,
                        ),
                    ),
                ),
            ),
        )
        val interactor = interactor()

        assertEquals(
            listOf(
                SpeedTestServerMetadata(
                    server = SpeedTestServer.AMSTERDAM,
                    optionId = 1,
                    label = "Amsterdam API",
                    selected = 0,
                ),
                SpeedTestServerMetadata(
                    server = SpeedTestServer.MOSCOW,
                    optionId = 2,
                    label = "Moscow API",
                    selected = 1,
                ),
            ),
            interactor.serverMetadata(),
        )
    }

    @Test
    fun run_exhaustsServerShardsThenContinuesAfterFailure() = runTest {
        val amsterdamFailure = IOException("raw-amsterdam-sentinel")
        val amsterdamUrls = mutableListOf<String>()
        every { deviceSettings.getCurrentDeviceSettings() } returns flowOf(
            Result.success(
                deviceResponse(
                    serverOptions = listOf(
                        SettingOption(
                            id = SpeedTestServer.AMSTERDAM.locationId,
                            label = "Amsterdam",
                            selected = 1,
                        ),
                    ),
                ),
            ),
        )
        coEvery {
            api.streamSpeedTest(any(), any(), any(), any())
        } coAnswers {
            val server = firstArg<SpeedTestServer>()
            if (server == SpeedTestServer.AMSTERDAM) {
                amsterdamUrls += secondArg<String>()
                return@coAnswers Result.failure(amsterdamFailure)
            }
            val callback = thirdArg<suspend (SpeedTestProgress) -> Unit>()
            callback(
                SpeedTestProgress(
                    server = server,
                    downloadedBytes = 100,
                    expectedBytes = 100,
                    elapsedMillis = 1_000,
                    megabitsPerSecond = 0.8,
                ),
            )
            Result.success(
                SpeedTestResult(
                    server = server,
                    downloadedBytes = 100,
                    expectedBytes = 100,
                    elapsedMillis = 1_000,
                    megabitsPerSecond = 0.8,
                ),
            )
        }

        val events = interactor(Random(1)).run().toList()

        assertEquals(
            listOf(
                SpeedTestEvent.Started(SpeedTestServer.AMSTERDAM),
                SpeedTestEvent.Failed(
                    server = SpeedTestServer.AMSTERDAM,
                    cause = amsterdamFailure,
                ),
                SpeedTestEvent.Started(SpeedTestServer.MOSCOW),
                SpeedTestEvent.Progress(
                    server = SpeedTestServer.MOSCOW,
                    downloadedBytes = 100,
                    expectedBytes = 100,
                    elapsedMillis = 1_000,
                    megabitsPerSecond = 0.8,
                ),
                SpeedTestEvent.Completed(
                    server = SpeedTestServer.MOSCOW,
                    downloadedBytes = 100,
                    expectedBytes = 100,
                    elapsedMillis = 1_000,
                    megabitsPerSecond = 0.8,
                ),
            ),
            events,
        )
        assertConfiguredAmsterdamHosts(amsterdamUrls)
        coVerify(exactly = SpeedTestServer.AMSTERDAM.shards.count()) {
            api.streamSpeedTest(SpeedTestServer.AMSTERDAM, any(), any(), any())
        }
        coVerify(exactly = 1) {
            api.streamSpeedTest(SpeedTestServer.MOSCOW, any(), any(), any())
        }
        assertTrue(events.last() is SpeedTestEvent.Completed)
    }

    @Test
    fun run_retriesEveryConfiguredShardBeforePreProgressRecovery() = runTest {
        val amsterdamAttempts = AtomicInteger()
        val amsterdamUrls = mutableListOf<String>()
        coEvery {
            api.streamSpeedTest(any(), any(), any(), any())
        } coAnswers {
            val server = firstArg<SpeedTestServer>()
            val url = secondArg<String>()
            val callback = thirdArg<suspend (SpeedTestProgress) -> Unit>()
            if (server == SpeedTestServer.AMSTERDAM) {
                amsterdamUrls += url
                if (amsterdamAttempts.getAndIncrement() < 2) {
                    return@coAnswers Result.failure(IOException("unreachable shard"))
                }
            }
            callback(
                SpeedTestProgress(
                    server = server,
                    downloadedBytes = 100,
                    expectedBytes = 100,
                    elapsedMillis = 1_000,
                    megabitsPerSecond = 0.8,
                ),
            )
            Result.success(
                SpeedTestResult(
                    server = server,
                    downloadedBytes = 100,
                    expectedBytes = 100,
                    elapsedMillis = 1_000,
                    megabitsPerSecond = 0.8,
                ),
            )
        }

        val events = interactor(Random(1)).run().toList()

        assertEquals(SpeedTestServer.AMSTERDAM.shards.count(), amsterdamAttempts.get())
        assertConfiguredAmsterdamHosts(amsterdamUrls)
        assertTrue(
            events.none {
                it is SpeedTestEvent.Failed && it.server == SpeedTestServer.AMSTERDAM
            },
        )
        assertTrue(
            events.any {
                it is SpeedTestEvent.Completed && it.server == SpeedTestServer.AMSTERDAM
            },
        )
    }

    @Test
    fun run_deliversPositiveProgressAndCompletion_whenCallbackUsesIoDispatcher() = runTest {
        coEvery {
            api.streamSpeedTest(any(), any(), any(), any())
        } coAnswers {
            val server = firstArg<SpeedTestServer>()
            val callback = thirdArg<suspend (SpeedTestProgress) -> Unit>()
            val progress = SpeedTestProgress(
                server = server,
                downloadedBytes = 1_024,
                expectedBytes = SPEED_TEST_EXPECTED_BYTES,
                elapsedMillis = 250,
                megabitsPerSecond = 0.03125,
            )
            withContext(Dispatchers.IO) {
                callback(progress)
            }
            Result.success(
                SpeedTestResult(
                    server = server,
                    downloadedBytes = SPEED_TEST_EXPECTED_BYTES,
                    expectedBytes = SPEED_TEST_EXPECTED_BYTES,
                    elapsedMillis = 1_000,
                    megabitsPerSecond = 800.0,
                ),
            )
        }

        val events = interactor(Random(1)).run().toList()

        assertEquals(
            SpeedTestServer.knownServers.flatMap { server ->
                listOf(
                    SpeedTestEvent.Started(server),
                    SpeedTestEvent.Progress(
                        server = server,
                        downloadedBytes = 1_024,
                        expectedBytes = SPEED_TEST_EXPECTED_BYTES,
                        elapsedMillis = 250,
                        megabitsPerSecond = 0.03125,
                    ),
                    SpeedTestEvent.Completed(
                        server = server,
                        downloadedBytes = SPEED_TEST_EXPECTED_BYTES,
                        expectedBytes = SPEED_TEST_EXPECTED_BYTES,
                        elapsedMillis = 1_000,
                        megabitsPerSecond = 800.0,
                    ),
                )
            },
            events,
        )
    }

    @Test
    fun run_failsRegionWithoutFallbackAfterPositiveProgress() = runTest {
        val amsterdamFailure = IOException("post-progress failure")
        val amsterdamUrls = mutableListOf<String>()
        val amsterdamProgress = SpeedTestEvent.Progress(
            server = SpeedTestServer.AMSTERDAM,
            downloadedBytes = 1_024,
            expectedBytes = SPEED_TEST_EXPECTED_BYTES,
            elapsedMillis = 250,
            megabitsPerSecond = 0.03125,
        )
        coEvery {
            api.streamSpeedTest(any(), any(), any(), any())
        } coAnswers {
            val server = firstArg<SpeedTestServer>()
            val callback = thirdArg<suspend (SpeedTestProgress) -> Unit>()
            if (server == SpeedTestServer.AMSTERDAM) {
                amsterdamUrls += secondArg<String>()
                callback(
                    SpeedTestProgress(
                        server = server,
                        downloadedBytes = amsterdamProgress.downloadedBytes,
                        expectedBytes = amsterdamProgress.expectedBytes,
                        elapsedMillis = amsterdamProgress.elapsedMillis,
                        megabitsPerSecond = amsterdamProgress.megabitsPerSecond,
                    ),
                )
                return@coAnswers Result.failure(amsterdamFailure)
            }
            Result.success(
                SpeedTestResult(
                    server = server,
                    downloadedBytes = 100,
                    expectedBytes = 100,
                    elapsedMillis = 1_000,
                    megabitsPerSecond = 0.8,
                ),
            )
        }

        val events = interactor(Random(1)).run().toList()

        assertEquals(
            listOf(
                SpeedTestEvent.Started(SpeedTestServer.AMSTERDAM),
                amsterdamProgress,
                SpeedTestEvent.Failed(
                    server = SpeedTestServer.AMSTERDAM,
                    cause = amsterdamFailure,
                ),
                SpeedTestEvent.Started(SpeedTestServer.MOSCOW),
                SpeedTestEvent.Completed(
                    server = SpeedTestServer.MOSCOW,
                    downloadedBytes = 100,
                    expectedBytes = 100,
                    elapsedMillis = 1_000,
                    megabitsPerSecond = 0.8,
                ),
            ),
            events,
        )
        assertEquals(1, amsterdamUrls.size)
        assertTrue(URI(amsterdamUrls.single()).host in CONFIGURED_AMSTERDAM_HOSTS)
        coVerify(exactly = 1) {
            api.streamSpeedTest(SpeedTestServer.AMSTERDAM, any(), any(), any())
        }
        coVerify(exactly = 1) {
            api.streamSpeedTest(SpeedTestServer.MOSCOW, any(), any(), any())
        }
    }

    @Test
    fun run_rethrowsCancellationAndDoesNotStartLaterServer() = runTest {
        val cancellation = CancellationException("stop")
        coEvery {
            api.streamSpeedTest(any(), any(), any(), any())
        } returns Result.failure(cancellation)

        val thrown = runCatching {
            interactor(Random(1)).run().toList()
        }.exceptionOrNull()

        assertTrue(thrown is CancellationException)
        assertEquals(cancellation.message, thrown?.message)
        coVerify(exactly = 1) {
            api.streamSpeedTest(SpeedTestServer.AMSTERDAM, any(), any(), any())
        }
        coVerify(exactly = 0) {
            api.streamSpeedTest(SpeedTestServer.MOSCOW, any(), any(), any())
        }
    }

    private fun interactor(random: Random = Random(1)) =
        SpeedTestInteractor(api, deviceSettings, random)

    private fun assertConfiguredAmsterdamHosts(urls: List<String>) {
        val attemptedHosts = urls.map { URI(it).host }
        assertEquals(CONFIGURED_AMSTERDAM_HOSTS, attemptedHosts.toSet())
        assertEquals(attemptedHosts.size, attemptedHosts.toSet().size)
    }

    private fun deviceResponse(serverOptions: List<SettingOption>): DeviceResponse =
        DeviceResponse(
            status = 200,
            device = DeviceResponseModel(
                id = 1,
                title = "Synthetic",
                hardware = "tv",
                software = "test",
                created = 0,
                updated = 0,
                lastSeen = 0,
                isBrowser = false,
                settings = SettingsResponse(
                    supportSsl = SettingValue(1, "SSL"),
                    supportHevc = SettingValue(1, "HEVC"),
                    supportHdr = SettingValue(1, "HDR"),
                    support4k = SettingValue(1, "4K"),
                    mixedPlaylist = SettingValue(1, "Mixed"),
                    serverLocation = SettingList(
                        type = "list",
                        label = "Server",
                        value = serverOptions,
                    ),
                    streamingType = SettingList(
                        type = "list",
                        label = "Streaming",
                        value = emptyList(),
                    ),
                ),
            ),
        )

    private companion object {
        val CONFIGURED_AMSTERDAM_HOSTS = setOf(
            "speed.ams-static-00.cdntogo.net",
            "speed.ams-static-01.cdntogo.net",
            "speed.ams-static-02.cdntogo.net",
        )
    }
}
