package com.kino.puber.core.error

internal class DefaultErrorHandler() : ErrorHandler {
    override fun proceed(action: ((ErrorEntity) -> Unit)?): (Throwable) -> Unit {
        return { e -> proceedInvoke(e, action) }
    }

    override fun proceedInvoke(
        e: Throwable, action: ((ErrorEntity) -> Unit)?
    ) {
        action?.invoke(map(e))
    }

    override fun map(e: Throwable): ErrorEntity {
        return ErrorEntity(
            message = e.message.orEmpty(), code = "Unknown"
        )
    }

}