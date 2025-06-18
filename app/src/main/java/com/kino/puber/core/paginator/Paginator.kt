@file:Suppress("TooManyFunctions")

package com.kino.puber.core.paginator


import com.kino.puber.core.collections.EquallyFunction
import com.kino.puber.core.collections.replaceOrInsertAtStart
import com.kino.puber.core.error.ErrorEntity
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.util.Collections


@Suppress("UNCHECKED_CAST")
object Paginator {

    sealed class State(
        val isLoadingEmpty: Boolean,
        val isRefreshing: Boolean,
        val isContentEmpty: Boolean,
        val isLoadingNext: Boolean,
        val errorGeneral: ErrorEntity?,
    ) {
        object Empty : State(
            isLoadingEmpty = false,
            isRefreshing = false,
            isContentEmpty = true,
            isLoadingNext = false,
            errorGeneral = null,
        )

        object Loading : State(
            isLoadingEmpty = true,
            isRefreshing = false,
            isContentEmpty = false,
            isLoadingNext = false,
            errorGeneral = null,
        )

        data class Refreshing<T>(val data: List<T>) : State(
            isLoadingEmpty = data.isEmpty(),
            isRefreshing = data.isNotEmpty(),
            isContentEmpty = false,
            isLoadingNext = false,
            errorGeneral = null,
        )

        data class LoadingPrev<T>(val data: List<T>) : State(
            isLoadingEmpty = false,
            isRefreshing = false,
            isContentEmpty = false,
            isLoadingNext = false,
            errorGeneral = null,
        )

        data class LoadingNext<T>(val data: List<T>) : State(
            isLoadingEmpty = false,
            isRefreshing = false,
            isContentEmpty = false,
            isLoadingNext = true,
            errorGeneral = null,
        )

        data class PageErrorNext<T>(val data: List<T>, val error: ErrorEntity) : State(
            isLoadingEmpty = false,
            isRefreshing = false,
            isContentEmpty = false,
            isLoadingNext = false,
            errorGeneral = null,
        )

        data class PageErrorPrev<T>(val data: List<T>, val error: ErrorEntity) : State(
            isLoadingEmpty = false,
            isRefreshing = false,
            isContentEmpty = false,
            isLoadingNext = false,
            errorGeneral = null,
        )

        data class Data<T>(val data: List<T>, val key: T? = null) : State(
            isLoadingEmpty = false,
            isRefreshing = false,
            isContentEmpty = false,
            isLoadingNext = false,
            errorGeneral = null,
        )

        data class ErrorEmpty(val error: ErrorEntity) : State(
            isLoadingEmpty = false,
            isRefreshing = false,
            isContentEmpty = false,
            isLoadingNext = false,
            errorGeneral = error,
        )

        data class Error<T>(val data: List<T>, val error: ErrorEntity) : State(
            isLoadingEmpty = false,
            isRefreshing = false,
            isContentEmpty = false,
            isLoadingNext = false,
            errorGeneral = error,
        )

        override fun toString(): String = javaClass.simpleName
    }

    sealed class Action {
        object Refresh : Action()
        object Restart : Action()
        data class RestartWithKey(val key: Any) : Action()
        object LoadNext : Action()
        object LoadPrev : Action()
        data class Error(val error: ErrorEntity) : Action()
        data class PageError(val error: ErrorEntity) : Action()
        data class Replace<T>(val items: List<T>, val key: T? = null) : Action()
        data class NextPage<T>(val items: List<T>) : Action()
        data class PrevPage<T>(val items: List<T>) : Action()
        data class ItemUpdated<T>(val item: T) : Action()
        data class ItemDeleted<T>(val item: T) : Action()
        data class ItemAdded<T>(val item: T) : Action()

        override fun toString(): String = javaClass.simpleName
    }

    sealed class SideEffect {
        data class LoadNextPage<T>(val key: T?) : SideEffect()
        data class LoadPrevPage<T>(val key: T?) : SideEffect()
        data object LoadFirstPage : SideEffect()
        data class LoadFirstPageWithKey(val key: Any) : SideEffect()
    }

    private fun <T> reducer(
        action: Action,
        state: State,
        comparator: EquallyFunction<T>,
        sideEffectListener: (SideEffect) -> Unit,
    ): State {
        return when (action) {
            Action.Refresh -> {
                executeRefreshAction<T>(sideEffectListener, state)
            }

            is Action.Restart -> {
                executeRestartAction(sideEffectListener)
            }

            is Action.RestartWithKey -> {
                executeRestartWithKeyAction(sideEffectListener, action.key)
            }

            is Action.Replace<*> -> {
                executeReplaceAction(action)
            }

            is Action.LoadPrev -> {
                executeLoadPrevAction<T>(state, sideEffectListener)
            }

            is Action.LoadNext -> {
                executeLoadNextAction<T>(state, sideEffectListener)
            }

            is Action.PrevPage<*> -> {
                executePrevPageAction(action, state, comparator)
            }

            is Action.NextPage<*> -> {
                executeNextPageAction(action, state, comparator)
            }

            is Action.ItemUpdated<*> -> {
                executeUpdateItem(action as Action.ItemUpdated<T>, state, comparator)
            }

            is Action.ItemDeleted<*> -> {
                executeDeleteItem(action as Action.ItemDeleted<T>, state, comparator)
            }

            is Action.ItemAdded<*> -> {
                executeAddedItem(action as Action.ItemAdded<T>, state, comparator)
            }

            is Action.PageError -> {
                executePageErrorAction<T>(state, action)
            }

            is Action.Error -> {
                executeErrorAction<T>(state, action)
            }
        }
    }


    private fun <T> executeRefreshAction(
        sideEffectListener: (SideEffect) -> Unit,
        state: State
    ): State {
        sideEffectListener(SideEffect.LoadFirstPage)
        return when (state) {
            is State.Data<*> -> {
                val data = state.data as List<T>
                if (data.isEmpty()) {
                    State.Loading
                } else State.Refreshing(data)
            }

            else -> State.Loading
        }
    }

    private fun executeRestartAction(sideEffectListener: (SideEffect) -> Unit): State {
        sideEffectListener(SideEffect.LoadFirstPage)
        return State.Loading
    }

    private fun executeRestartWithKeyAction(
        sideEffectListener: (SideEffect) -> Unit,
        key: Any
    ): State {
        sideEffectListener(SideEffect.LoadFirstPageWithKey(key))
        return State.Loading
    }

    private fun executeReplaceAction(action: Action.Replace<*>): State {
        if (action.items.isEmpty()) {
            return State.Empty
        }
        return State.Data(action.items, action.key)
    }

    private fun <T> executeLoadPrevAction(
        state: State,
        sideEffectListener: (SideEffect) -> Unit
    ): State {
        return when (state) {
            is State.Data<*> -> {
                val key = state.data.firstOrNull()
                sideEffectListener(SideEffect.LoadPrevPage(key))
                State.LoadingPrev(state.data as List<T>)
            }

            is State.Refreshing<*> -> {
                val key = state.data.firstOrNull()
                sideEffectListener(SideEffect.LoadPrevPage(key))
                State.LoadingPrev(state.data as List<T>)
            }

            is State.LoadingPrev<*> -> {
                State.LoadingPrev(state.data as List<T>)
            }

            is State.LoadingNext<*> -> {
                State.LoadingPrev(state.data as List<T>)
            }

            is State.PageErrorNext<*> -> state
            is State.PageErrorPrev<*> -> state

            else -> State.Loading
        }
    }

    private fun <T> executeLoadNextAction(
        state: State,
        sideEffectListener: (SideEffect) -> Unit
    ): State {
        return when (state) {
            is State.Data<*> -> {
                val key = state.data.lastOrNull()
                sideEffectListener(SideEffect.LoadNextPage(key))
                State.LoadingNext(state.data as List<T>)
            }

            is State.Refreshing<*> -> {
                val key = state.data.lastOrNull()
                sideEffectListener(SideEffect.LoadNextPage(key))
                State.LoadingNext(state.data as List<T>)
            }

            is State.LoadingPrev<*> -> {
                val key = state.data.lastOrNull()
                sideEffectListener(SideEffect.LoadNextPage(key))
                State.LoadingNext(state.data as List<T>)
            }

            is State.LoadingNext<*> -> {
                State.LoadingNext(state.data as List<T>)
            }

            is State.PageErrorNext<*> -> {
                val key = state.data.lastOrNull()
                sideEffectListener(SideEffect.LoadNextPage(key))
                State.LoadingNext(state.data as List<T>)
            }

            else -> State.Loading
        }
    }

    private fun <T> executePrevPageAction(
        action: Action.PrevPage<*>,
        state: State,
        comparator: EquallyFunction<T>,
    ): State {
        val items = action.items as List<T>
        return when (state) {
            is State.Empty -> {
                if (items.isEmpty()) {
                    State.Empty
                } else {
                    State.Data(items)
                }
            }

            is State.Data<*> -> {
                // sometimes loading prev page faster than socket and messages are duplicated
                var list = state.data as List<T>
                items.asReversed().forEach { item ->
                    list = addItemOrUpdate(list, item, comparator)
                }
                State.Data(list.toList())
            }

            is State.LoadingPrev<*> -> {
                // sometimes loading prev page faster than socket and messages are duplicated
                var list = state.data as List<T>
                items.asReversed().forEach { item ->
                    list = addItemOrUpdate(list, item, comparator)
                }
                State.Data(list.toList())
            }

            else -> State.Data(items)
        }
    }

    private fun <T> executeNextPageAction(
        action: Action.NextPage<*>,
        state: State,
        comparator: EquallyFunction<T>,
    ): State {
        val items = action.items as List<T>
        return when (state) {
            is State.Empty -> {
                if (items.isEmpty()) {
                    State.Empty
                } else {
                    State.Data(items)
                }
            }

            is State.Data<*> -> {
                val currentList = (state.data as List<T>).toMutableList()
                items.forEach { item ->
                    val index = currentList.indexOfFirst { comparator.isItemTheSame(item, it) }
                    if (index >= 0) {
                        currentList[index] = item
                    } else currentList.add(item)
                }
                State.Data(currentList)
            }

            is State.LoadingNext<*> -> {
                val currentList = (state.data as List<T>).toMutableList()
                items.forEach { item ->
                    val index = currentList.indexOfFirst { comparator.isItemTheSame(item, it) }
                    if (index >= 0) {
                        currentList[index] = item
                    } else currentList.add(item)
                }
                State.Data(currentList)
            }

            else -> State.Data(items)
        }
    }

    @Suppress("LongMethod")
    private fun <T> executeUpdateItem(
        action: Action.ItemUpdated<T>,
        state: State,
        comparator: EquallyFunction<T>,
    ): State {
        val item = action.item
        return when (state) {
            is State.Data<*> -> {
                val items = state.data as List<T>
                val index = items.indexOfFirst { comparator.isItemTheSame(it, item) }
                if (index >= 0) {
                    val newList = items.toMutableList()
                    newList[index] = item
                    State.Data(Collections.unmodifiableList(newList))
                } else {
                    state
                }
            }

            is State.LoadingNext<*> -> {
                val items = state.data as List<T>
                val index = items.indexOfFirst { comparator.isItemTheSame(it, item) }
                if (index >= 0) {
                    val newList = items.toMutableList()
                    newList[index] = item
                    State.LoadingNext(Collections.unmodifiableList(newList))
                } else {
                    state
                }
            }

            is State.LoadingPrev<*> -> {
                val items = state.data as List<T>
                val index = items.indexOfFirst { comparator.isItemTheSame(it, item) }
                if (index >= 0) {
                    val newList = items.toMutableList()
                    newList[index] = item
                    State.LoadingPrev(Collections.unmodifiableList(newList))
                } else {
                    state
                }
            }

            is State.Refreshing<*> -> {
                val items = state.data as List<T>
                val index = items.indexOfFirst { comparator.isItemTheSame(it, item) }
                if (index >= 0) {
                    val newList = items.toMutableList()
                    newList[index] = item
                    State.Refreshing(Collections.unmodifiableList(newList))
                } else {
                    state
                }
            }

            is State.PageErrorNext<*> -> {
                val items = state.data as List<T>
                val index = items.indexOfFirst { comparator.isItemTheSame(it, item) }
                if (index >= 0) {
                    val newList = items.toMutableList()
                    newList[index] = item
                    State.PageErrorNext(Collections.unmodifiableList(newList), state.error)
                } else {
                    state
                }
            }

            is State.PageErrorPrev<*> -> {
                val items = state.data as List<T>
                val index = items.indexOfFirst { comparator.isItemTheSame(it, item) }
                if (index >= 0) {
                    val newList = items.toMutableList()
                    newList[index] = item
                    State.PageErrorPrev(Collections.unmodifiableList(newList), state.error)
                } else {
                    state
                }
            }

            else -> state
        }
    }

    @Suppress("LongMethod")
    private fun <T> executeDeleteItem(
        action: Action.ItemDeleted<T>,
        state: State,
        comparator: EquallyFunction<T>,
    ): State {
        val item = action.item
        return when (state) {
            is State.Data<*> -> {
                val items = state.data as List<T>
                val newList = items.toMutableList()
                newList.removeAll { comparator.isItemTheSame(it, item) }
                if (newList.isNotEmpty()) {
                    State.Data(Collections.unmodifiableList(newList))
                } else State.Empty
            }

            is State.LoadingNext<*> -> {
                val items = state.data as List<T>
                val newList = items.toMutableList()
                newList.removeAll { comparator.isItemTheSame(it, item) }
                if (newList.isNotEmpty()) {
                    State.LoadingNext(Collections.unmodifiableList(newList))
                } else state
            }

            is State.LoadingPrev<*> -> {
                val items = state.data as List<T>
                val newList = items.toMutableList()
                newList.removeAll { comparator.isItemTheSame(it, item) }
                if (newList.isNotEmpty()) {
                    State.LoadingPrev(Collections.unmodifiableList(newList))
                } else state
            }

            is State.Refreshing<*> -> {
                val items = state.data as List<T>
                val newList = items.toMutableList()
                newList.removeAll { comparator.isItemTheSame(it, item) }
                if (newList.isNotEmpty()) {
                    State.Refreshing(Collections.unmodifiableList(newList))
                } else state
            }

            is State.PageErrorNext<*> -> {
                val items = state.data as List<T>
                val newList = items.toMutableList()
                newList.removeAll { comparator.isItemTheSame(it, item) }
                if (newList.isNotEmpty()) {
                    State.PageErrorNext(Collections.unmodifiableList(newList), state.error)
                } else state
            }

            is State.PageErrorPrev<*> -> {
                val items = state.data as List<T>
                val newList = items.toMutableList()
                newList.removeAll { comparator.isItemTheSame(it, item) }
                if (newList.isNotEmpty()) {
                    State.PageErrorPrev(Collections.unmodifiableList(newList), state.error)
                } else state
            }

            else -> state
        }
    }


    private fun <T> executeAddedItem(
        action: Action.ItemAdded<T>,
        state: State,
        comparator: EquallyFunction<T>,
    ): State {
        val item = action.item
        return when (state) {
            is State.Data<*> -> {
                val items = state.data as List<T>
                val newList = addItemOrUpdate(items, item, comparator)
                State.Data(newList)
            }

            is State.LoadingNext<*> -> {
                val items = state.data as List<T>
                val newList = addItemOrUpdate(items, item, comparator)
                State.LoadingNext(newList)
            }

            is State.LoadingPrev<*> -> {
                val items = state.data as List<T>
                val newList = addItemOrUpdate(items, item, comparator)
                State.LoadingPrev(newList)
            }

            is State.Refreshing<*> -> {
                val items = state.data as List<T>
                val newList = addItemOrUpdate(items, item, comparator)
                State.Refreshing(newList)
            }

            is State.PageErrorNext<*> -> {
                val items = state.data as List<T>
                val newList = addItemOrUpdate(items, item, comparator)
                State.PageErrorNext(newList, state.error)
            }

            is State.PageErrorPrev<*> -> {
                val items = state.data as List<T>
                val newList = addItemOrUpdate(items, item, comparator)
                State.PageErrorPrev(newList, state.error)
            }

            else -> State.Data(listOf(item))
        }
    }

    private fun <T> addItemOrUpdate(
        items: List<T>,
        item: T,
        comparator: EquallyFunction<T>
    ): List<T> {
        val newList = items.toMutableList()
        newList.replaceOrInsertAtStart(item, comparator)
        return Collections.unmodifiableList(newList)
    }

    private fun <T> executePageErrorAction(state: State, action: Action.PageError): State {
        return when (state) {
            is State.LoadingNext<*> -> {
                State.PageErrorNext(state.data as List<T>, action.error)
            }

            is State.LoadingPrev<*> -> {
                State.PageErrorPrev(state.data as List<T>, action.error)
            }

            is State.Refreshing<*> -> {
                val items = state.data as List<T>
                if (items.isEmpty()) {
                    State.ErrorEmpty(action.error)
                } else {
                    State.Error(items, action.error)
                }
            }

            is State.Data<*> -> {
                val items = state.data as List<T>
                if (items.isEmpty()) {
                    State.ErrorEmpty(action.error)
                } else {
                    State.Error(items, action.error)
                }
            }

            else -> State.ErrorEmpty(action.error)
        }
    }

    private fun <T> executeErrorAction(state: State, action: Action.Error): State {
        return when (state) {
            is State.Refreshing<*> -> {
                val items = state.data as List<T>
                if (items.isEmpty()) {
                    State.ErrorEmpty(action.error)
                } else {
                    State.Error(items, action.error)
                }
            }

            is State.Data<*> -> {
                val items = state.data as List<T>
                if (items.isEmpty()) {
                    State.ErrorEmpty(action.error)
                } else {
                    State.Error(items, action.error)
                }
            }

            else -> State.ErrorEmpty(action.error)
        }
    }


    class Store<T>(private val comparator: EquallyFunction<T>) :
        CoroutineScope by CoroutineScope(
            Dispatchers.Default + CoroutineName("Paginator.State"),
        ) {
        private var state: State = State.Empty
        var render: (State) -> Unit = {}
            set(value) {
                field = value
                value(state)
            }

        val sideEffects = MutableSharedFlow<SideEffect>()

        fun proceed(action: Action) {
            val newState = reducer(action, state, comparator) { sideEffect ->
                launch { sideEffects.emit(sideEffect) }
            }

            if (state != newState) {
                //log("action: $action oldState: $state newState: $newState")
                state = newState
                render(newState)
            }
        }
    }
}

fun <T> Paginator.Store<T>.itemAdded(item: T) = proceed(Paginator.Action.ItemAdded(item))

fun <T> Paginator.Store<T>.itemUpdated(item: T) = proceed(Paginator.Action.ItemUpdated(item))

fun <T> Paginator.Store<T>.itemDeleted(item: T) = proceed(Paginator.Action.ItemDeleted(item))

fun <T> Paginator.Store<T>.refresh() = proceed(Paginator.Action.Refresh)

fun <T> Paginator.Store<T>.restart() = proceed(Paginator.Action.Restart)

fun <T> Paginator.Store<T>.restartWithKey(key: Any) = proceed(Paginator.Action.RestartWithKey(key))

fun <T> Paginator.Store<T>.loadNext() = proceed(Paginator.Action.LoadNext)

fun <T> Paginator.Store<T>.loadPrev() = proceed(Paginator.Action.LoadPrev)

fun <T> Paginator.Store<T>.replace(list: List<T>, key: T? = null) =
    proceed(Paginator.Action.Replace(list, key))

fun <T> Paginator.Store<T>.nextPage(list: List<T>) = proceed(Paginator.Action.NextPage(list))

fun <T> Paginator.Store<T>.prevPage(list: List<T>) = proceed(Paginator.Action.PrevPage(list))

fun Paginator.Store<*>.error(error: ErrorEntity) = proceed(Paginator.Action.Error(error))

fun Paginator.Store<*>.pageError(error: ErrorEntity) = proceed(Paginator.Action.PageError(error))