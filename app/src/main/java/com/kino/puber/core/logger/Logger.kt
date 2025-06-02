package com.kino.puber.core.logger

import timber.log.Timber


fun Any.log(message: String, tag: String = this.getTag()) {
    Timber.tag("Puber: $tag").d(message)
}

fun Any.log(
    throwable: Throwable,
    message: String = "Something went wrong",
    tag: String = this.getTag()
) {
    Timber.tag("Puber: $tag").w(throwable, message)
}

private fun Any.getTag(): String {
    return (javaClass.enclosingClass?.simpleName ?: javaClass.simpleName)
}