package com.kino.puber.domain.interactor.speedtest

const val SPEED_TEST_EXPECTED_BYTES: Long = 100L * 1024L * 1024L
private const val BITS_PER_BYTE = 8.0
private const val MILLIS_PER_SECOND = 1_000.0
private const val BITS_PER_MEGABIT = 1_048_576.0

enum class SpeedTestServer(
    val locationId: Int,
    val hostCode: String,
    val shards: IntRange,
) {
    AMSTERDAM(locationId = 1, hostCode = "ams", shards = 0..2),
    MOSCOW(locationId = 2, hostCode = "msk", shards = 5..6),
    ;

    companion object {
        val knownServers: List<SpeedTestServer> = entries

        fun fromLocationId(locationId: Int): SpeedTestServer? =
            entries.firstOrNull { it.locationId == locationId }
    }
}

data class SpeedTestServerMetadata(
    val server: SpeedTestServer,
    val optionId: Int,
    val label: String,
    val selected: Int,
)

data class SpeedTestProgress(
    val server: SpeedTestServer,
    val downloadedBytes: Long,
    val expectedBytes: Long,
    val elapsedMillis: Long,
    val megabitsPerSecond: Double,
)

data class SpeedTestResult(
    val server: SpeedTestServer,
    val downloadedBytes: Long,
    val expectedBytes: Long,
    val elapsedMillis: Long,
    val megabitsPerSecond: Double,
)

internal fun calculateSpeedMbitPerSecond(
    downloadedBytes: Long,
    elapsedMillis: Long,
): Double {
    if (downloadedBytes <= 0L || elapsedMillis <= 0L) return 0.0
    return downloadedBytes * BITS_PER_BYTE /
        (elapsedMillis / MILLIS_PER_SECOND) /
        BITS_PER_MEGABIT
}

sealed interface SpeedTestEvent {
    data class Started(val server: SpeedTestServer) : SpeedTestEvent

    data class Progress(
        val server: SpeedTestServer,
        val downloadedBytes: Long,
        val expectedBytes: Long,
        val elapsedMillis: Long,
        val megabitsPerSecond: Double,
    ) : SpeedTestEvent

    data class Completed(
        val server: SpeedTestServer,
        val downloadedBytes: Long,
        val expectedBytes: Long,
        val elapsedMillis: Long,
        val megabitsPerSecond: Double,
    ) : SpeedTestEvent

    data class Failed(
        val server: SpeedTestServer,
        val cause: Throwable,
    ) : SpeedTestEvent
}
