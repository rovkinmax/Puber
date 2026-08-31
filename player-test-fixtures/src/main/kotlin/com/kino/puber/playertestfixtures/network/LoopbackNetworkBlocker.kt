package com.kino.puber.playertestfixtures.network

import android.content.Context
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

object LoopbackNetworkBlocker {
    private var installedSelector: BlockingProxySelector? = null

    @Synchronized
    fun install(context: Context, allowedOrigin: String) {
        val normalizedOrigin = allowedOrigin.removeSuffix("/").lowercase()
        if (installedSelector?.allowedOrigin == normalizedOrigin) return
        val selector = BlockingProxySelector(
            allowedOrigin = normalizedOrigin,
            journal = LoopbackNetworkJournal(context.applicationContext),
        )
        ProxySelector.setDefault(selector)
        installedSelector = selector
    }

    private class BlockingProxySelector(
        val allowedOrigin: String,
        private val journal: LoopbackNetworkJournal,
    ) : ProxySelector() {
        override fun select(uri: URI): List<Proxy> {
            requireNotNull(uri) { "URI must not be null" }
            return if (origin(uri) == allowedOrigin) {
                listOf(Proxy.NO_PROXY)
            } else {
                journal.record(uri)
                listOf(DENY_PROXY)
            }
        }

        override fun connectFailed(uri: URI, sa: SocketAddress?, ioe: java.io.IOException) {
            if (origin(uri) != allowedOrigin) journal.record(uri)
        }

        private fun origin(uri: URI): String {
            val scheme = uri.scheme.orEmpty().lowercase()
            val host = uri.host.orEmpty().lowercase()
            val port = uri.port.takeIf { it >= 0 } ?: when (scheme) {
                "http" -> 80
                "https" -> 443
                else -> -1
            }
            return "$scheme://$host:$port"
        }
    }

    private val DENY_PROXY = Proxy(
        Proxy.Type.HTTP,
        InetSocketAddress.createUnresolved("127.0.0.1", 9),
    )
}
