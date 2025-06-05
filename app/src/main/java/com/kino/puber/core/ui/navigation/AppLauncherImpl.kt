package com.kino.puber.core.ui.navigation

import android.app.Activity

internal class AppLauncherImpl : AppLauncher {

    private var activity: Activity? = null

    override fun restart() {
        activity?.recreate()
    }

    override fun finish() {
        activity?.finish()
    }

    override fun bind(activity: Activity) {
        this.activity = activity
    }

    override fun unbind() {
        activity = null
    }
}