package com.kino.puber.data.repository

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

internal class EpisodeSchedulePrivacyTest {

    @Test
    fun scheduleSourcesDoNotLogIdentityCredentialsUrlsOrResponses() {
        SCHEDULE_SOURCE_FILES.forEach { relativePath ->
            val source = String(
                Files.readAllBytes(resolveSource(relativePath)),
                StandardCharsets.UTF_8,
            )

            assertFalse(source.contains("Timber."), "$relativePath uses Timber logging")
            assertFalse(source.contains("android.util.Log"), "$relativePath uses Android logging")
            assertFalse(source.contains("println("), "$relativePath prints runtime data")
            assertFalse(source.contains("response.bodyAsText"), "$relativePath exposes raw responses")
        }
    }

    private fun resolveSource(relativePath: String): Path {
        val workingDirectory = Path.of(System.getProperty("user.dir"))
        val candidates = listOf(
            workingDirectory.resolve("src/main/java").resolve(relativePath),
            workingDirectory.resolve("app/src/main/java").resolve(relativePath),
        )
        return candidates.firstOrNull(Files::exists)
            ?: error("Unable to locate source: $relativePath")
    }

    private companion object {
        val SCHEDULE_SOURCE_FILES = listOf(
            "com/kino/puber/data/api/TmdbApiClient.kt",
            "com/kino/puber/data/repository/EpisodeScheduleRepository.kt",
            "com/kino/puber/domain/interactor/schedule/EpisodeScheduleInteractor.kt",
            "com/kino/puber/ui/feature/episodeschedule/model/EpisodeScheduleUIMapper.kt",
            "com/kino/puber/ui/feature/episodeschedule/vm/EpisodeScheduleVM.kt",
        )
    }
}
