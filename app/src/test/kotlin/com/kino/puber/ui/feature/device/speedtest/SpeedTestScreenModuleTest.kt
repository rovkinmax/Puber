package com.kino.puber.ui.feature.device.speedtest

import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.domain.interactor.device.IDeviceSettingInteractor
import com.kino.puber.domain.interactor.speedtest.SpeedTestInteractor
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.koinApplication
import org.koin.dsl.module

internal class SpeedTestScreenModuleTest {

    @Test
    fun buildModule_resolvesSpeedTestInteractor_withoutRandomDefinition() {
        val screenModule = SpeedTestScreen::class.java.declaredMethods
            .single { it.name == "buildModule" }
            .apply { isAccessible = true }
            .invoke(SpeedTestScreen(), SCOPE_ID, mockk<Scope>()) as Module
        val application = koinApplication {
            modules(
                module {
                    single<KinoPubApiClient> { mockk() }
                    single<IDeviceSettingInteractor> { mockk() }
                },
                screenModule,
            )
        }
        val scope = application.koin.createScope(SCOPE_ID, named(SCOPE_ID))

        try {
            assertNotNull(scope.get<SpeedTestInteractor>())
        } finally {
            scope.close()
            application.close()
        }
    }

    private companion object {
        const val SCOPE_ID = "SpeedTestScreenModuleTest"
    }
}
