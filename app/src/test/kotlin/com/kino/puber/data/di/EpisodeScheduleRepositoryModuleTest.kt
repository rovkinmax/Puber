package com.kino.puber.data.di

import com.kino.puber.data.api.TmdbApiClient
import com.kino.puber.data.repository.EpisodeScheduleRepository
import com.kino.puber.data.repository.TmdbCastRepository
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.koin.dsl.koinApplication
import org.koin.dsl.module

internal class EpisodeScheduleRepositoryModuleTest {

    @Test
    fun repositoryModule_resolvesTmdbRepositories_withoutDispatcherBinding() {
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
            assertNotNull(application.koin.get<TmdbCastRepository>())
        } finally {
            application.close()
        }
    }
}
