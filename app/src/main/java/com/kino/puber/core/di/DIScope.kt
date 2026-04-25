package com.kino.puber.core.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import org.koin.core.module.Module
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeID
import org.koin.dsl.module

val LocalPuberKoinScope: ProvidableCompositionLocal<Scope?> = staticCompositionLocalOf { null }

@Composable
fun DIScope(
    scopeName: String,
    moduleFactory: (scopeId: ScopeID, parentScope: Scope) -> Module = { _, _ ->
        module {}
    },
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        value = LocalPuberKoinScope provides rememberDIScope(
            scopeName = scopeName,
            moduleFactory = moduleFactory,
        ),
        content = content,
    )
}