package com.kino.puber.data.api.config

import android.os.Build
import com.kino.puber.BuildConfig

object UserAgentBuilder {

    /**
     * Builds User-Agent string according to the format:
     * kinopub/{versionName} device/{deviceModel} os/Android{androidVersion} username/{username}
     */
    fun build(username: String? = null): String {
        val components = mutableListOf<String>()

        // Application: kinopub/ + version from versionName
        components.add("kinopub/${BuildConfig.VERSION_NAME}")

        // Device: device/ + device model
        components.add("device/${Build.MODEL}")

        // OS: os/Android + Android version
        components.add("os/Android${Build.VERSION.RELEASE}")

        // Username: username/ + username from settings (if provided)
        username?.let { user ->
            components.add("username/$user")
        }

        return components.joinToString(" ")
    }
} 