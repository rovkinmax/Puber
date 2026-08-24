package com.kino.puber.core.system

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ConnectionTransportProviderTest {

    private val connectivityManager = mockk<ConnectivityManager>()
    private val network = mockk<Network>()
    private val capabilities = mockk<NetworkCapabilities>()

    @Test
    fun current_returnsEthernet_whenEthernetIsAvailable() {
        givenCapabilities(
            ethernet = true,
            wifi = false,
            cellular = false,
        )

        assertEquals(
            ConnectionTransport.Ethernet,
            ConnectionTransportProvider(connectivityManager).current(),
        )
    }

    @Test
    fun current_returnsWifi_whenWifiIsAvailable() {
        givenCapabilities(
            ethernet = false,
            wifi = true,
            cellular = false,
        )

        assertEquals(
            ConnectionTransport.Wifi,
            ConnectionTransportProvider(connectivityManager).current(),
        )
    }

    @Test
    fun current_returnsCellular_whenCellularIsAvailable() {
        givenCapabilities(
            ethernet = false,
            wifi = false,
            cellular = true,
        )

        assertEquals(
            ConnectionTransport.Cellular,
            ConnectionTransportProvider(connectivityManager).current(),
        )
    }

    @Test
    fun current_prefersEthernet_whenMultipleTransportsAreReported() {
        givenCapabilities(
            ethernet = true,
            wifi = true,
            cellular = true,
        )

        assertEquals(
            ConnectionTransport.Ethernet,
            ConnectionTransportProvider(connectivityManager).current(),
        )
    }

    @Test
    fun current_prefersWifi_overCellular_whenEthernetIsUnavailable() {
        givenCapabilities(
            ethernet = false,
            wifi = true,
            cellular = true,
        )

        assertEquals(
            ConnectionTransport.Wifi,
            ConnectionTransportProvider(connectivityManager).current(),
        )
    }

    @Test
    fun current_returnsUnknown_whenThereIsNoActiveNetwork() {
        every { connectivityManager.activeNetwork } returns null

        assertEquals(
            ConnectionTransport.Unknown,
            ConnectionTransportProvider(connectivityManager).current(),
        )
    }

    @Test
    fun current_returnsUnknown_whenNetworkCapabilitiesAreUnavailable() {
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns null

        assertEquals(
            ConnectionTransport.Unknown,
            ConnectionTransportProvider(connectivityManager).current(),
        )
    }

    @Test
    fun current_returnsUnknown_whenCapabilitiesHaveNoSupportedTransport() {
        givenCapabilities(
            ethernet = false,
            wifi = false,
            cellular = false,
        )

        assertEquals(
            ConnectionTransport.Unknown,
            ConnectionTransportProvider(connectivityManager).current(),
        )
    }

    private fun givenCapabilities(
        ethernet: Boolean,
        wifi: Boolean,
        cellular: Boolean,
    ) {
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
        every {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } returns ethernet
        every {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } returns wifi
        every {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        } returns cellular
    }
}
