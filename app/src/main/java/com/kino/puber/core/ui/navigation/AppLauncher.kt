package com.kino.puber.core.ui.navigation

import android.app.Activity

interface AppLauncher {
    fun restart()
    fun finish()

    fun bind(activity: Activity)

    fun unbind()
}