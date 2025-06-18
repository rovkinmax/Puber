package com.kino.puber.core.coroutine

import com.kino.puber.core.error.ErrorEntity
import com.kino.puber.core.error.ErrorHandler
import kotlinx.coroutines.CoroutineExceptionHandler

fun ErrorHandler.handler(action: ((ErrorEntity) -> Unit)? = null): CoroutineExceptionHandler {
    return DefaultExceptionHandler(proceed(action))
}