package com.kino.puber.core.paginator

import com.kino.puber.core.coroutine.handler
import com.kino.puber.core.error.ErrorEntity
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.ui.PuberVM
import com.kino.puber.core.ui.navigation.AppRouter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

abstract class PagingVM<T, VS>(
    protected val paginator: Paginator.Store<T>,
    router: AppRouter,
    override val errorHandler: ErrorHandler,
) : PuberVM<VS>(router) {

    private val pagingJob = SupervisorJob()
    private val pagingScope = CoroutineScope(coroutineContext + pagingJob)
    protected var isFullDataNext = false
    protected var isFullDataPrev = false
    protected open val errorHandlerGeneral by lazy { errorHandler.handler(::setGeneralError) }
    protected open val errorHandlerPaging by lazy { errorHandler.handler(::setPageError) }

    protected fun init(key: Any? = null) {
        paginator.render = ::dispatchListState

        launch {
            paginator.sideEffects
                .collect(::dispatchSideEffects)
        }

        launch {
            newItemsFlow()
                .collect(::setNewItem)
        }

        launch {
            updateItemsFlow()
                .collect(::updateItem)
        }

        launch {
            deleteItemsFlow()
                .collect(::deleteItem)
        }

        launch { resetPaging(key = key) }
    }

    protected open fun newItemsFlow(): Flow<T> = flow { }

    protected open fun updateItemsFlow(): Flow<T> = flow { }

    protected open fun deleteItemsFlow(): Flow<T> = flow { }

    protected open fun setNewItem(item: T) = paginator.itemAdded(item)

    protected fun updateItem(item: T) = paginator.itemUpdated(item)

    protected open fun deleteItem(item: T) = paginator.itemDeleted(item)

    protected fun replace(list: List<T>, key: T? = null) = paginator.replace(list, key)

    protected fun setNextPage(list: List<T>) = paginator.nextPage(list)

    protected fun setPrevPage(list: List<T>) = paginator.prevPage(list)

    protected fun setPageError(error: ErrorEntity) = paginator.pageError(error)

    protected fun setGeneralError(error: ErrorEntity) = paginator.error(error)

    protected fun refresh() = paginator.refresh()

    protected abstract fun dispatchListState(state: Paginator.State)

    @Suppress("UNCHECKED_CAST")
    private fun dispatchSideEffects(sideEffect: Paginator.SideEffect) {
        when (sideEffect) {
            Paginator.SideEffect.LoadFirstPage -> {
                cancelLoading()
                onLoadFirstPage()
            }

            is Paginator.SideEffect.LoadFirstPageWithKey -> {
                cancelLoading()
                onLoadFirstPageWithKey(sideEffect.key)
            }

            is Paginator.SideEffect.LoadNextPage<*> -> onLoadNextPage(sideEffect.key as T?)
            is Paginator.SideEffect.LoadPrevPage<*> -> onLoadingPrevPage(sideEffect.key as T?)
        }
    }

    protected abstract fun onLoadFirstPage()

    protected open fun onLoadFirstPageWithKey(key: Any) {}

    protected abstract fun onLoadNextPage(key: T?)

    protected open fun onLoadingPrevPage(key: T?) {}

    protected fun notifyLoadNextPage() {
        if (isFullDataNext.not()) {
            paginator.loadNext()
        }
    }

    protected fun notifyLoadingPrev(forced: Boolean = false) {
        if (isFullDataPrev.not() || forced) {
            if (forced) {
                isFullDataPrev = false
            }
            paginator.loadPrev()
        }
    }

    protected fun pagingLaunch(
        context: CoroutineContext = EmptyCoroutineContext,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend CoroutineScope.() -> Unit,
    ): Job {
        return pagingScope.launch(context = context, start) { block.invoke(this) }
    }

    protected open fun resetPaging(key: Any? = null) {
        isFullDataNext = false
        isFullDataPrev = false
        cancelLoading()
        if (key == null) {
            paginator.restart()
        } else {
            paginator.restartWithKey(key)
        }
    }

    private fun cancelLoading() {
        pagingJob.cancelChildren(CancellationException(("Stop paging employee expenses")))
    }
}