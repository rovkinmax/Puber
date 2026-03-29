package com.kino.puber.core.ui.uikit.component.modifier

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.focus.FocusRequester
import com.kino.puber.core.ui.navigation.component.LocalScreenKey
import com.kino.puber.core.ui.uikit.component.drawer.DrawerValue
import com.kino.puber.core.ui.uikit.component.drawer.LocalDrawerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * When `false`, [rememberFocusRequesterOnLaunch] will not auto-request focus.
 * Set to `false` in TopTabs mode so that tab bar keeps focus control.
 */
val LocalAutoFocusOnLaunchEnabled = staticCompositionLocalOf { true }

@Composable
fun rememberFocusRequesterOnLaunch(): FocusRequester {
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val isDrawerOpen = LocalDrawerState.current?.currentValue == DrawerValue.Open
    val autoFocusEnabled = LocalAutoFocusOnLaunchEnabled.current
    var isFocusRequested by rememberSaveable(key = LocalScreenKey.current) {
        mutableStateOf(false)
    }

    if (!isFocusRequested && !isDrawerOpen && autoFocusEnabled) {
        SideEffect {
            scope.launch {
                delay(100)
                if (isFocusRequested.not()) {
                    isFocusRequested = true
                    focusRequester.requestFocus()
                }
            }
        }
    }
    return focusRequester
}
