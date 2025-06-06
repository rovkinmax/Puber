package com.kino.puber.core.ui.navigation

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

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

    companion object {
        @Composable
        fun rememberAppLauncher(): AppLauncher {

            val activity = LocalActivity.current
            val lifecycleOwner = LocalLifecycleOwner.current

            val appLauncher = remember { AppLauncherImpl() }

            DisposableEffect(activity, lifecycleOwner) {

                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_START -> activity?.let { appLauncher.bind(it) }
                        Lifecycle.Event.ON_STOP -> appLauncher.unbind()
                        else -> {}
                    }
                }

                activity?.let(appLauncher::bind)

                lifecycleOwner.lifecycle.addObserver(observer)

                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                    appLauncher.unbind()
                }
            }

            return appLauncher
        }
    }
}