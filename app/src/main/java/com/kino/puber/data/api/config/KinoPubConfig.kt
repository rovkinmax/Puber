package com.kino.puber.data.api.config

import android.util.Base64

object KinoPubConfig {

    private val defaultDomain: String by lazy {
        val encoded = "=02bj5Ccr1SZjlmdyV2c"
        String(Base64.decode(encoded.reversed().toByteArray(), Base64.DEFAULT)).trim()
    }

    @Volatile
    private var domainOverride: String? = null

    private val apiDomain: String get() = domainOverride ?: defaultDomain

    val MAIN_API_BASE_URL: String get() = "https://api.$apiDomain/v1/"
    val OAUTH_BASE_URL: String get() = "https://api.$apiDomain/oauth2/"
    val EXTRA_API_BASE_URL: String get() = "https://api.$apiDomain/"

    const val GRANT_TYPE_DEVICE_CODE = "device_code"
    const val GRANT_TYPE_DEVICE_TOKEN = "device_token"
    const val GRANT_TYPE_REFRESH_TOKEN = "refresh_token"

    fun setDomainOverride(domain: String?) {
        domainOverride = domain
    }
}
