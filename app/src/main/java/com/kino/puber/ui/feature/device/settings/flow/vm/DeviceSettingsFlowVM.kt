package com.kino.puber.ui.feature.device.settings.flow.vm

import com.kino.puber.core.ui.PuberFlowVM
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.ui.feature.device.settings.DeviceSettingsScreen

internal class DeviceSettingsFlowVM(
    router: AppRouter,
) : PuberFlowVM(router) {

    override fun onStart() {
        router.newRootScreen(DeviceSettingsScreen())
    }
}
