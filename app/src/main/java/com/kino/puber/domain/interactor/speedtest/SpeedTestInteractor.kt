package com.kino.puber.domain.interactor.speedtest

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.domain.interactor.device.IDeviceSettingInteractor
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow

internal class SpeedTestInteractor(
    private val apiClient: KinoPubApiClient,
    private val deviceSettingInteractor: IDeviceSettingInteractor,
    private val random: Random = Random.Default,
) {

    suspend fun serverMetadata(): List<SpeedTestServerMetadata> {
        val deviceResponse = deviceSettingInteractor.getCurrentDeviceSettings()
            .firstOrNull()
            ?.getOrNull()
            ?: return emptyList()

        return deviceResponse.device.settings.serverLocation.value.mapNotNull { option ->
            SpeedTestServer.fromLocationId(option.id)?.let { server ->
                SpeedTestServerMetadata(
                    server = server,
                    optionId = option.id,
                    label = option.label,
                    selected = option.selected,
                )
            }
        }
    }

    fun run(): Flow<SpeedTestEvent> = flow {
        for (server in SpeedTestServer.knownServers) {
            coroutineContext.ensureActive()
            emit(SpeedTestEvent.Started(server))
            runServer(server)?.let { error ->
                emit(
                    SpeedTestEvent.Failed(
                        server = server,
                        cause = error,
                    ),
                )
            }
        }
    }

    private suspend fun FlowCollector<SpeedTestEvent>.runServer(
        server: SpeedTestServer,
    ): Throwable? {
        var lastFailure: Throwable? = null
        for (shard in shuffledShards(server)) {
            coroutineContext.ensureActive()
            var hasPositiveProgress = false
            val result = apiClient.streamSpeedTest(
                server = server,
                url = buildProbeUrl(
                    server = server,
                    shard = shard,
                    randomValue = random.nextDouble(),
                ),
                onProgress = { progress ->
                    if (progress.downloadedBytes > 0) {
                        hasPositiveProgress = true
                    }
                    emit(
                        SpeedTestEvent.Progress(
                            server = progress.server,
                            downloadedBytes = progress.downloadedBytes,
                            expectedBytes = progress.expectedBytes,
                            elapsedMillis = progress.elapsedMillis,
                            megabitsPerSecond = progress.megabitsPerSecond,
                        ),
                    )
                },
                expectedBytes = SPEED_TEST_EXPECTED_BYTES,
            )
            val failure = result.exceptionOrNull()
            if (failure is CancellationException) throw failure
            if (failure == null) {
                val measurement = result.getOrThrow()
                emit(
                    SpeedTestEvent.Completed(
                        server = measurement.server,
                        downloadedBytes = measurement.downloadedBytes,
                        expectedBytes = measurement.expectedBytes,
                        elapsedMillis = measurement.elapsedMillis,
                        megabitsPerSecond = measurement.megabitsPerSecond,
                    ),
                )
                return null
            }
            if (hasPositiveProgress) return failure
            lastFailure = failure
        }
        return lastFailure
    }

    private fun shuffledShards(server: SpeedTestServer): List<Int> =
        server.shards.shuffled(random)

    companion object {
        private const val SPEED_TEST_HOST_SUFFIX = "cdntogo.net"

        internal fun buildProbeUrl(
            server: SpeedTestServer,
            shard: Int,
            randomValue: Double,
        ): String {
            require(shard in server.shards) {
                "Shard $shard is not configured for ${server.name}"
            }
            val paddedShard = shard.toString().padStart(2, '0')
            return String.format(
                Locale.US,
                "https://speed.%s-static-%s.%s/speedtest/garbage.php?r=%s&ckSize=100",
                server.hostCode,
                paddedShard,
                SPEED_TEST_HOST_SUFFIX,
                randomValue,
            )
        }
    }
}
