package com.kino.puber.core.ui.uikit.component.modifier

import androidx.compose.ui.Modifier

fun Modifier.ifElse(
    condition: Boolean,
    ifTrueModifier: Modifier,
    elseModifier: Modifier = Modifier
): Modifier = then(if (condition) ifTrueModifier else elseModifier)