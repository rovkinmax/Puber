package com.kino.puber.ui.feature.auth.vm

import com.kino.puber.core.error.ErrorEntity
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.domain.interactor.api.ApiDomainAutoResolveResult
import com.kino.puber.domain.interactor.api.ApiDomainInteractor
import com.kino.puber.domain.interactor.api.ApiDomainState
import com.kino.puber.domain.interactor.auth.IAuthInteractor
import com.kino.puber.domain.interactor.auth.model.AuthState
import com.kino.puber.domain.interactor.device.IDeviceInfoInteractor
import com.kino.puber.ui.feature.auth.model.AuthViewState
import com.kino.puber.util.FakeResourceProvider
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.IOException

class AuthVMTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    private val router = mockk<AppRouter>(relaxed = true)
    private val errorHandler = mockk<ErrorHandler>(relaxed = true)
    private val authInteractor = mockk<IAuthInteractor>()
    private val deviceInfoInteractor = mockk<IDeviceInfoInteractor>(relaxed = true)
    private val apiDomainInteractor = mockk<ApiDomainInteractor>(relaxed = true)

    private fun createVM(): AuthVM {
        every { apiDomainInteractor.getState() } returns ApiDomainState(
            domain = "api.example.com",
            customDomain = null,
        )
        return AuthVM(
            authInteractor = authInteractor,
            deviceInfoInteractor = deviceInfoInteractor,
            apiDomainInteractor = apiDomainInteractor,
            resources = FakeResourceProvider(),
            errorHandler = errorHandler,
            router = router,
        )
    }

    @Test
    fun onStart_skipsApiDomainAutoResolveAndShowsDeviceCode_whenNoTokenExists() {
        every { authInteractor.isAuthenticated() } returns false
        every { authInteractor.getAuthState() } returns flowOf(
            AuthState.Code(
                code = "123456",
                url = "https://example.com",
                expireTimeSeconds = 120,
            )
        )
        val vm = createVM()

        vm.testOnStart()

        val state = vm.testStateValue
        assertTrue(state is AuthViewState.Content)
        assertEquals("123456", (state as AuthViewState.Content).code)
        assertEquals("https://example.com", state.url)
        coVerify(exactly = 0) { apiDomainInteractor.autoResolveWorkingDomain() }
    }

    @Test
    fun onStart_keepsDomainAutoResolveFallback_whenTokenExists() {
        every { authInteractor.isAuthenticated() } returns true
        every { authInteractor.getAuthState() } returns flowOf(AuthState.Success)
        coEvery { apiDomainInteractor.autoResolveWorkingDomain() } returns ApiDomainAutoResolveResult.NotFound
        val vm = createVM()

        vm.testOnStart()

        val state = vm.testStateValue
        assertTrue(state is AuthViewState.Loading)
        assertTrue((state as AuthViewState.Loading).showMirrorHint)
        verify(exactly = 0) { authInteractor.getAuthState() }
    }

    @Test
    fun dispatchError_doesNotDetectAlternativeDomain_whenLoginRequired() {
        every { authInteractor.isAuthenticated() } returns false
        every { authInteractor.getAuthState() } returns flow { throw IOException("Login failed") }
        every { errorHandler.proceedInvoke(any(), any()) } answers {
            secondArg<((ErrorEntity) -> Unit)?>()?.invoke(
                ErrorEntity(
                    message = "Login failed",
                    code = "login_failed",
                )
            )
        }
        val vm = createVM()

        vm.testOnStart()

        verify(exactly = 1) { errorHandler.proceedInvoke(any(), any()) }
        coVerify(exactly = 0) { apiDomainInteractor.detectAndSaveAlternativeBuiltInDomain() }
    }
}
