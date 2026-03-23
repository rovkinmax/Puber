package com.kino.puber.data.api.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress

object DnsOverHttpsFactory {

    fun create(): DnsOverHttps {
        val bootstrapClient = OkHttpClient.Builder().build()
        return DnsOverHttps.Builder()
            .client(bootstrapClient)
            .url("https://dns.google/dns-query".toHttpUrl())
            .bootstrapDnsHosts(
                InetAddress.getByName("8.8.4.4"),
                InetAddress.getByName("8.8.8.8"),
            )
            .post(true)
            .includeIPv6(false)
            .build()
    }
}
