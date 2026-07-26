package com.kino.puber.core.logger

import timber.log.Timber

private val sensitiveLogPolicy = SensitiveRequestLogPolicy()

fun Any.log(message: String, tag: String = this.getTag()) {
    Timber.tag("Puber: $tag").d(sensitiveLogPolicy.sanitizeText(message))
}

fun Any.log(
    throwable: Throwable,
    message: String = "Something went wrong",
    tag: String = this.getTag()
) {
    Timber.tag("Puber: $tag").w(
        sensitiveLogPolicy.sanitizeThrowable(throwable),
        sensitiveLogPolicy.sanitizeText(message),
    )
}

private fun Any.getTag(): String {
    return (javaClass.enclosingClass?.simpleName ?: javaClass.simpleName)
}
