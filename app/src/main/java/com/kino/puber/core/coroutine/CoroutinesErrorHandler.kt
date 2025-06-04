package com.kino.puber.core.coroutine

import com.kino.puber.core.logger.log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlin.coroutines.cancellation.CancellationException

@Suppress("FunctionName")
fun DefaultExceptionHandler(call: ((Throwable) -> Unit)? = null) =
    CoroutineExceptionHandler { _, t ->
        call?.invoke(t)
        t.log(t)
    }

fun <T> Flow<T>.handleErrors(): Flow<T> =
    catch { e -> log(e) }

@Suppress("TooGenericExceptionCaught")
suspend inline fun <T> tryOrNull(crossinline call: suspend () -> T): T? {
    return try {
        call()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        e.log(e)
        null
    }
}