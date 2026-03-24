package com.kino.puber.core.ui

import com.kino.puber.core.ui.navigation.AppRouter

/**
 * Headless ViewModel for FlowScreen entry points.
 * Has no ViewState — only triggers initial navigation in [onStart].
 */
abstract class PuberFlowVM(router: AppRouter) : PuberVM<Unit>(router) {
    override val initialViewState: Unit = Unit
}
