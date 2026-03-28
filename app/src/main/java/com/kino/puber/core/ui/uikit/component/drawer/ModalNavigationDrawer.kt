/*
 * Forked from androidx.tv.material3 (tv-material:1.1.0-beta01)
 * Original: https://android.googlesource.com/platform/frameworks/support/+/refs/heads/main/tv/tv-material/src/main/java/androidx/tv/material3/NavigationDrawer.kt
 *
 * Reason: DrawerSheet unconditionally opens the drawer on any focus gain (onFocusChanged → setValue(Open)).
 * When an overlay (bottom sheet) is dismissed, focus escapes to the drawer before any external
 * workaround can restore it. The fix adds an `isOverlayActive` guard to DrawerState that suppresses
 * focus-driven drawer opening while an overlay is visible.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.kino.puber.core.ui.uikit.component.drawer

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.zIndex
import androidx.compose.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawerScope

val LocalDrawerState = staticCompositionLocalOf<DrawerState?> { null }

/** States that the drawer can exist in. */
enum class DrawerValue {
    Closed,
    Open,
}

/**
 * State of the [ModalNavigationDrawer].
 *
 * Adds [isOverlayActive] — when `true`, focus-driven opening is suppressed inside [DrawerSheet].
 */
class DrawerState(initialValue: DrawerValue = DrawerValue.Closed) {
    var currentValue by mutableStateOf(initialValue)
        private set

    /**
     * Guards against the drawer opening when an overlay (bottom sheet) is dismissed.
     *
     * ## Problem
     * The original [DrawerSheet] unconditionally opens the drawer on any `hasFocus=true`
     * event via `onFocusChanged`. When a bottom sheet closes, the focused composable is removed
     * from composition, and the Android TV focus system sends focus to the drawer's focus group
     * before any external mechanism can intercept it.
     *
     * ## Solution
     * Set to `true` when showing a full-screen overlay.
     * While active, [DrawerSheet] swallows all focus events and redirects `hasFocus=true`
     * to [contentFocusRequester]. The flag stays active through focus bounces (the TV focus
     * system may bounce focus between drawer and content several times before settling).
     *
     * ## Related issues
     * - `focusRestorer()` does not save focus state when focus jumps to an overlay
     *   (only on D-pad exit): [#296551299](https://issuetracker.google.com/issues/296551299)
     * - `saveFocusedChild/restoreFocusedChild` unreliable with LazyColumn:
     *   [#290645002](https://issuetracker.google.com/issues/290645002)
     */
    var isOverlayActive by mutableStateOf(false)

    /**
     * Focus requester for the main content area. Set via `SideEffect` in `MainScreenContent`.
     * Used by [DrawerSheet] to redirect escaped focus when [isOverlayActive] is `true`.
     */
    var contentFocusRequester: FocusRequester? = null

    fun setValue(drawerValue: DrawerValue) {
        currentValue = drawerValue
    }

    companion object {
        val Saver =
            Saver<DrawerState, DrawerValue>(
                save = { it.currentValue },
                restore = { DrawerState(it) },
            )
    }
}

@Composable
fun rememberDrawerState(initialValue: DrawerValue): DrawerState {
    return rememberSaveable(saver = DrawerState.Saver) { DrawerState(initialValue) }
}

@Composable
fun ModalNavigationDrawer(
    drawerContent: @Composable NavigationDrawerScope.(DrawerValue) -> Unit,
    modifier: Modifier = Modifier,
    drawerState: DrawerState = rememberDrawerState(DrawerValue.Closed),
    scrimBrush: Brush = SolidColor(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
    content: @Composable () -> Unit,
) {
    val localDensity = LocalDensity.current
    val closedDrawerWidth: MutableState<Dp?> = remember { mutableStateOf(null) }
    val internalDrawerModifier =
        Modifier.zIndex(Float.MAX_VALUE).onSizeChanged {
            if (closedDrawerWidth.value == null && drawerState.currentValue == DrawerValue.Closed) {
                with(localDensity) { closedDrawerWidth.value = it.width.toDp() }
            }
        }

    Box(modifier = modifier) {
        DrawerSheet(
            modifier = internalDrawerModifier.align(Alignment.CenterStart),
            drawerState = drawerState,
            sizeAnimationFinishedListener = { _, targetSize ->
                if (drawerState.currentValue == DrawerValue.Closed) {
                    with(localDensity) { closedDrawerWidth.value = targetSize.width.toDp() }
                }
            },
            content = drawerContent,
        )

        content()

        if (drawerState.currentValue == DrawerValue.Open) {
            Canvas(Modifier.fillMaxSize()) { drawRect(scrimBrush) }
        }
    }
}

@Composable
private fun DrawerSheet(
    modifier: Modifier = Modifier,
    drawerState: DrawerState = remember { DrawerState() },
    sizeAnimationFinishedListener: ((initialValue: IntSize, targetValue: IntSize) -> Unit)? = null,
    content: @Composable NavigationDrawerScope.(DrawerValue) -> Unit,
) {
    var initializationComplete: Boolean by remember { mutableStateOf(false) }
    var focusState by remember { mutableStateOf<FocusState?>(null) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(key1 = drawerState.currentValue) {
        if (drawerState.currentValue == DrawerValue.Open && focusState?.hasFocus == false) {
            focusRequester.requestFocus()
        }
        initializationComplete = true
    }

    val internalModifier =
        Modifier.focusRequester(focusRequester)
            .animateContentSize(finishedListener = sizeAnimationFinishedListener)
            .fillMaxHeight()
            .then(modifier)
            .onFocusChanged {
                focusState = it

                if (initializationComplete) {
                    // While an overlay (bottom sheet) is active, suppress all focus-driven
                    // drawer state changes. On hasFocus=true, redirect focus to content area.
                    // The guard stays active through focus bounces (drawer↔content) until
                    // reset by FlowComponent after the overlay is fully dismissed + settled.
                    if (drawerState.isOverlayActive) {
                        if (it.hasFocus) {
                            drawerState.contentFocusRequester?.requestFocus()
                        }
                        return@onFocusChanged
                    }
                    drawerState.setValue(if (it.hasFocus) DrawerValue.Open else DrawerValue.Closed)
                }
            }
            .focusGroup()

    Box(modifier = internalModifier) {
        NavigationDrawerScopeImpl(drawerState.currentValue == DrawerValue.Open).apply {
            content(drawerState.currentValue)
        }
    }
}

private class NavigationDrawerScopeImpl(override val hasFocus: Boolean) : NavigationDrawerScope