package com.kino.puber.core.ui.navigation

import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.bottomSheet.BottomSheetNavigator

internal const val HiddenBottomSheetKey =
    "cafe.adriel.voyager.navigator.bottomSheet.HiddenBottomSheetScreen"

internal fun BottomSheetNavigator.puberPop() {
    val lastItem = lastItemOrNull
    pop()
}

internal fun BottomSheetNavigator.puberHide() {
    if (/*lastItemOrNull != null && */lastItemOrNull?.key != HiddenBottomSheetKey) {
        val items = items.toList()
        this.hide()
    }
}

internal fun BottomSheetNavigator.puberShow(screen: Screen) {
    show(screen)
}

internal fun Navigator.puberPush(screen: Screen) {
    push(screen)
}

internal fun Navigator.puberReplace(screen: Screen) {
    val lastItem = lastItemOrNull
    replace(screen)
}

internal fun Navigator.puberReplaceAll(vararg screen: Screen) {
    val items = items.toList()
    replaceAll(screen.toList())
}

internal fun Navigator.puberPop() {
    val lastItem = lastItemOrNull
    pop()
}

internal fun Navigator.puberPopUntil(predicate: (Screen) -> Boolean) {
    val poppedScreens = mutableListOf<Screen>()
    popUntil {
        val popped = predicate(it)
        if (popped) {
            poppedScreens.add(it)
        }
        return@popUntil popped
    }
}