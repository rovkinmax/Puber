package com.kino.puber.core.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.remember
import org.koin.compose.LocalKoinScope
import org.koin.compose.getKoin
import org.koin.core.Koin
import org.koin.core.context.GlobalContext
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.core.scope.ScopeID

internal class ScopeModuleManager(
    private val scopeName: String,
    private val moduleFactory: (ScopeID, Scope) -> Module,
    private val parentScope: Scope,
    private val koin: Koin = GlobalContext.get()
) : RememberObserver {

    private var scope: Scope? = null
    private var module: Module? = null

    fun initialize() {
        scope = koin.createScope(scopeName, named(scopeName)).apply {
            linkTo(parentScope)
        }
        module = moduleFactory(scope!!.id, parentScope).also {
            koin.loadModules(listOf(it))
        }
    }

    override fun onRemembered() {}

    override fun onAbandoned() {
        clearScope()
    }

    override fun onForgotten() {
        clearScope()
    }

    private fun clearScope() {
        scope?.close()
        scope = null

        module?.let {
            koin.unloadModules(listOf(it))
        }
        module = null
    }

    fun getScope(): Scope? = scope
}

@Composable
fun rememberDIScope(
    scopeName: String,
    koin: Koin = getKoin(),
    moduleFactory: (ScopeID, Scope) -> Module,
): Scope {
    val parentScope = LocalKoinScope.current
    return remember(scopeName, parentScope.id, koin) {
        ScopeModuleManager(
            scopeName = scopeName,
            moduleFactory = moduleFactory,
            parentScope = parentScope,
            koin = koin,
        ).apply { initialize() }
    }.getScope()!!
}