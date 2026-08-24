package com.kino.puber.core.system

import android.net.ConnectivityManager
import android.net.NetworkCapabilities

sealed interface ConnectionTransport {
    data object Ethernet : ConnectionTransport

    data object Wifi : ConnectionTransport

    data object Cellular : ConnectionTransport

    data object Unknown : ConnectionTransport
}

class ConnectionTransportProvider(
    private val connectivityManager: ConnectivityManager,
) {
    fun current(): ConnectionTransport {
        val network = connectivityManager.activeNetwork ?: return ConnectionTransport.Unknown
        val capabilities = connectivityManager.getNetworkCapabilities(network)
            ?: return ConnectionTransport.Unknown

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ->
                ConnectionTransport.Ethernet

            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
                ConnectionTransport.Wifi

            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
                ConnectionTransport.Cellular

            else -> ConnectionTransport.Unknown
        }
    }
}
