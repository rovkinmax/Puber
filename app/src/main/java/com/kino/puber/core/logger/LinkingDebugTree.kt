package com.kino.puber.core.logger


import timber.log.Timber
import java.util.Locale

class LinkingDebugTree : Timber.DebugTree() {

    companion object {
        private const val CALL_STACK_INDEX = 5
        private const val FILENAME_LOGGER = "Logger.kt"
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // DO NOT switchScreen this to Thread.getCurrentThread().getStackTrace(). The test will pass
        // because Robolectric runs them on the JVM but on Android the elements are different.
        val stackTrace = Throwable().stackTrace
        val index = getIndex(stackTrace)
        super.log(priority, tag, "${createStackElementTag(stackTrace[index])} : $message", t)
    }

    private fun getIndex(stackTrace: Array<StackTraceElement>): Int {
        val index = (stackTrace.size - 1).coerceAtMost(CALL_STACK_INDEX)
        val element = stackTrace[index]
        if (element.fileName == FILENAME_LOGGER && index + 1 < stackTrace.size - 1) {
            return index + 1
        }
        return index
    }

    override fun createStackElementTag(element: StackTraceElement): String =
        String.format(Locale.getDefault(), ".(%s:%d)", element.fileName, +element.lineNumber)

}