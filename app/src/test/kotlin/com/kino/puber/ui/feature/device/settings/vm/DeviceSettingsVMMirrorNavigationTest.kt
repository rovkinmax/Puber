package com.kino.puber.ui.feature.device.settings.vm

import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.data.preferences.NavigationPreferencesRepository
import com.kino.puber.data.repository.PlayerPreferencesRepository
import com.kino.puber.domain.interactor.api.ApiDomainDetectionResult
import com.kino.puber.domain.interactor.api.ApiDomainInteractor
import com.kino.puber.domain.interactor.api.ApiDomainState
import com.kino.puber.domain.interactor.api.ApiDomainUpdateResult
import com.kino.puber.domain.interactor.device.IDeviceInfoInteractor
import com.kino.puber.domain.interactor.device.IDeviceSettingInteractor
import com.kino.puber.domain.interactor.update.IAppUpdateInteractor
import com.kino.puber.ui.feature.device.settings.mappers.DeviceUiSettingsMapper
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsActions
import com.kino.puber.util.FakeResourceProvider
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.Called
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class DeviceSettingsVMMirrorNavigationTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    private val router = mockk<AppRouter>(relaxed = true)
    private val deviceSettingInteractor = mockk<IDeviceSettingInteractor>(relaxed = true)
    private val deviceInfoInteractor = mockk<IDeviceInfoInteractor>(relaxed = true)
    private val deviceUiSettingsMapper = mockk<DeviceUiSettingsMapper>(relaxed = true)
    private val playerPreferencesRepository = mockk<PlayerPreferencesRepository>(relaxed = true)
    private val navigationPreferencesRepository = mockk<NavigationPreferencesRepository>(relaxed = true)
    private val apiDomainInteractor = mockk<ApiDomainInteractor>(relaxed = true)
    private val appUpdateInteractor = mockk<IAppUpdateInteractor>(relaxed = true)
    private val errorHandler = mockk<ErrorHandler>(relaxed = true)

    @Test
    fun saveApiDomain_keepsDeviceSettingsOpen() {
        val state = ApiDomainState(
            domain = "api.custom.example",
            customDomain = "api.custom.example",
        )
        every { apiDomainInteractor.saveCustomDomain("api.custom.example") } returns
            ApiDomainUpdateResult.Success(state)
        val vm = createVM()

        vm.onAction(DeviceSettingsActions.OpenApiDomainDialog)
        assertTrue(vm.testStateValue.isApiDomainDialogOpen)
        clearMocks(router, answers = false)

        vm.onAction(DeviceSettingsActions.SaveApiDomain("api.custom.example"))

        assertEquals("api.custom.example", vm.testStateValue.apiDomain.currentDomain)
        assertEquals("api.custom.example", vm.testStateValue.apiDomain.customDomain)
        assertFalse(vm.testStateValue.isApiDomainDialogOpen)
        verify { router wasNot Called }
    }

    @Test
    fun resetApiDomain_keepsDeviceSettingsOpen() {
        val state = ApiDomainState(
            domain = "service-kp.com",
            customDomain = null,
        )
        every { apiDomainInteractor.resetToDefault() } returns state
        val vm = createVM()

        vm.onAction(DeviceSettingsActions.OpenApiDomainDialog)
        assertTrue(vm.testStateValue.isApiDomainDialogOpen)
        clearMocks(router, answers = false)

        vm.onAction(DeviceSettingsActions.ResetApiDomain)

        assertEquals("service-kp.com", vm.testStateValue.apiDomain.currentDomain)
        assertEquals(null, vm.testStateValue.apiDomain.customDomain)
        assertFalse(vm.testStateValue.isApiDomainDialogOpen)
        verify { router wasNot Called }
    }

    @Test
    fun detectApiDomain_keepsDeviceSettingsOpen() {
        val state = ApiDomainState(
            domain = "api.detected.example",
            customDomain = "api.detected.example",
        )
        coEvery { apiDomainInteractor.detectAndSaveWorkingDomain() } returns
            ApiDomainDetectionResult.Success(state)
        val vm = createVM()

        vm.onAction(DeviceSettingsActions.OpenApiDomainDialog)
        assertTrue(vm.testStateValue.isApiDomainDialogOpen)
        clearMocks(router, answers = false)

        vm.onAction(DeviceSettingsActions.DetectApiDomain)
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        assertEquals("api.detected.example", vm.testStateValue.apiDomain.currentDomain)
        assertEquals("api.detected.example", vm.testStateValue.apiDomain.customDomain)
        assertFalse(vm.testStateValue.isApiDomainDialogOpen)
        verify { router wasNot Called }
    }

    private fun createVM(): DeviceSettingsVM {
        every { apiDomainInteractor.getState() } returns ApiDomainState(
            domain = "service-kp.com",
            customDomain = null,
        )
        return DeviceSettingsVM(
            deviceSettingInteractor = deviceSettingInteractor,
            deviceInfoInteractor = deviceInfoInteractor,
            deviceUiSettingsMapper = deviceUiSettingsMapper,
            playerPreferencesRepository = playerPreferencesRepository,
            navigationPreferencesRepository = navigationPreferencesRepository,
            apiDomainInteractor = apiDomainInteractor,
            appUpdateInteractor = appUpdateInteractor,
            errorHandler = errorHandler,
            resources = FakeResourceProvider(),
            router = router,
        )
    }
}
