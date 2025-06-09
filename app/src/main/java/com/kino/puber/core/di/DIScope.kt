package com.kino.puber.core.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import org.koin.compose.LocalKoinScope
import org.koin.core.module.Module
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeID
import org.koin.dsl.module

@Composable
fun DIScope(
    scopeName: String,
    moduleFactory: (scopeId: ScopeID, parentScope: Scope) -> Module = { _, _ ->
        module {}
    },
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        value = LocalKoinScope provides rememberDIScope(
            scopeName = scopeName,
            moduleFactory = moduleFactory,
        ),
        content = content,
    )
}