package com.kino.puber.core.ui.uikit.model

import androidx.compose.runtime.Immutable

@Immutable
internal data class ApiDomainDialogState(
    val currentDomain: String,
    val customDomain: String?,
    val isDetecting: Boolean = false,
) {
    val initialInput: String get() = customDomain.orEmpty()
}
