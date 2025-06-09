package com.kino.puber.core.error

interface ErrorHandler {
    fun proceed(action: ((ErrorEntity) -> Unit)? = null): (Throwable) -> Unit
    fun proceedInvoke(e: Throwable, action: ((ErrorEntity) -> Unit)? = null)
    fun map(e: Throwable): ErrorEntity
}