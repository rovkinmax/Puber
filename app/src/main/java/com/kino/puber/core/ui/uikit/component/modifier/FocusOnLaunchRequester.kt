package com.kino.puber.core.ui.uikit.component.modifier

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import com.kino.puber.core.ui.navigation.component.LocalScreenKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun rememberFocusRequesterOnLaunch(): FocusRequester {
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var isFocusRequested by rememberSaveable(key = LocalScreenKey.current) {
        mutableStateOf(false)
    }

    SideEffect {
        scope.launch {
            // hack to force focus on launch
            // if you know how to fix it, please do
            delay(100)
            if (isFocusRequested.not()) {
                isFocusRequested = true
                focusRequester.requestFocus()
            }
        }
    }
    return focusRequester
}