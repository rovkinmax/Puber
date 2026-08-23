package com.kino.puber.data.di

import com.kino.puber.data.api.TmdbApiClient
import com.kino.puber.data.repository.EpisodeScheduleRepository
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.koin.dsl.koinApplication
import org.koin.dsl.module

internal class EpisodeScheduleRepositoryModuleTest {

    @Test
    fun repositoryModule_resolvesEpisodeScheduleRepository_withoutTestSeamDefinitions() {
        val application = koinApplication {
            modules(
                module {
                    single<TmdbApiClient> { mockk() }
                },
                repositoryModule,
            )
        }

        try {
            assertNotNull(application.koin.get<EpisodeScheduleRepository>())
        } finally {
            application.close()
        }
    }
}
