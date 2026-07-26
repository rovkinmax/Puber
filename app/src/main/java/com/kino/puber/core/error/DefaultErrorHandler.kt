package com.kino.puber.core.error

import com.kino.puber.R
import com.kino.puber.core.system.ResourceProvider

internal class DefaultErrorHandler(
    private val resources: ResourceProvider,
) : ErrorHandler {
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
            message = resources.getString(R.string.error_generic),
            code = "Unknown",
        )
    }
}
