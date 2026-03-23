package com.kino.puber.data.api.network

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import io.ktor.client.plugins.api.createClientPlugin
import java.io.IOException

class NoConnectivityException : IOException("No network connectivity")

fun createConnectivityPlugin(connectivityManager: ConnectivityManager) =
    createClientPlugin("ConnectivityPlugin") {
        onRequest { _, _ ->
            if (!connectivityManager.isNetworkAvailable()) {
                throw NoConnectivityException()
            }
        }
    }

private fun ConnectivityManager.isNetworkAvailable(): Boolean {
    val network = activeNetwork ?: return false
    val capabilities = getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
