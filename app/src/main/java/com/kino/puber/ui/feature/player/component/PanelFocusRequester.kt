package com.kino.puber.ui.feature.player.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester

@Composable
internal fun rememberRequestingFocusRequester(
    focusKey: Any?,
    isFocusOwner: Boolean,
): FocusRequester {
    val focusRequester = remember { FocusRequester() }
    val latestIsFocusOwner by rememberUpdatedState(isFocusOwner)
    LaunchedEffect(focusKey, isFocusOwner) {
        if (isFocusOwner) {
            focusRequester.requestWhenAttached(
                isOwner = { latestIsFocusOwner },
            )
        }
    }
    return focusRequester
}

internal suspend fun FocusRequester.requestWhenAttached(
    isOwner: () -> Boolean,
    fallback: FocusRequester? = null,
): Boolean = requestFocusWithFallback(
    isOwner = isOwner,
    awaitNextAttempt = { withFrameNanos { } },
    requestPrimaryFocus = { runCatching { requestFocus() }.getOrDefault(false) },
    requestFallbackFocus = fallback?.let { fallbackRequester ->
        { runCatching { fallbackRequester.requestFocus() }.getOrDefault(false) }
    },
)

internal suspend fun requestFocusWithRetry(
    isOwner: () -> Boolean,
    awaitNextAttempt: suspend () -> Unit,
    requestFocus: () -> Boolean,
): Boolean = requestFocusWithFallback(
    isOwner = isOwner,
    awaitNextAttempt = awaitNextAttempt,
    requestPrimaryFocus = requestFocus,
    requestFallbackFocus = null,
)

internal suspend fun requestFocusWithFallback(
    isOwner: () -> Boolean,
    awaitNextAttempt: suspend () -> Unit,
    requestPrimaryFocus: () -> Boolean,
    requestFallbackFocus: (() -> Boolean)?,
): Boolean {
    val primaryFocused = requestBoundedFocus(isOwner, awaitNextAttempt, requestPrimaryFocus)
    val fallback = requestFallbackFocus
    return when {
        primaryFocused -> true
        !isOwner() -> false
        fallback == null -> false
        else -> requestBoundedFocus(isOwner, awaitNextAttempt, fallback)
    }
}

private suspend fun requestBoundedFocus(
    isOwner: () -> Boolean,
    awaitNextAttempt: suspend () -> Unit,
    requestFocus: () -> Boolean,
): Boolean {
    repeat(FOCUS_REQUEST_ATTEMPTS) {
        awaitNextAttempt()
        if (!isOwner()) return false
        if (requestFocus()) return true
    }
    return false
}

private const val FOCUS_REQUEST_ATTEMPTS = 30
