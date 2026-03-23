package com.kino.puber.data.api.config

import android.os.Build
import com.kino.puber.BuildConfig

object UserAgentBuilder {

    /**
     * Builds User-Agent string according to the format:
     * kinopub/{versionName} device/{deviceModel} os/Android{androidVersion} id/{androidId} username/{username}
     */
    fun build(username: String? = null, androidId: String? = null): String {
        val components = mutableListOf<String>()

        components.add("kinopub/${BuildConfig.VERSION_NAME}")
        components.add("device/${Build.MODEL}")
        components.add("os/Android${Build.VERSION.RELEASE}")

        androidId?.let { components.add("id/$it") }
        username?.let { components.add("username/$it") }

        return components.joinToString(" ")
    }
}
