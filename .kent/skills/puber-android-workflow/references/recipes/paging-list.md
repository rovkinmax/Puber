# Recipe: Paginated List
> Related: [viewmodel.md](viewmodel.md), [api-endpoint.md](api-endpoint.md), [compose-screen.md](compose-screen.md)

How to create a paginated list screen with PagingVM.

## Architecture overview

```
PagingVM<Entity, ViewState>(paginator, router, errorHandler)
  extends PuberVM<ViewState>(router)
  ├── paginator: Paginator.Store<Entity>
  ├── onLoadFirstPage()       -> replace(items)
  ├── onLoadNextPage(key?)    -> setNextPage(items)
  ├── dispatchListState(state) -> maps Paginator.State -> ViewState
  ├── notifyLoadNextPage()    -> triggers next page load
  ├── resetPaging()           -> resets paginator and reloads
  └── cachedItems: synchronizedList
```

## File location
```
ui/feature/<screenName>/vm/<ScreenName>VM.kt
ui/feature/<screenName>/model/<ScreenName>ViewState.kt
ui/feature/<screenName>/model/<ScreenName>UIMapper.kt
ui/feature/<screenName>/component/<ScreenName>Screen.kt
ui/feature/<screenName>/component/<ScreenName>ScreenContent.kt
```

## ViewState with all paginator states

```kotlin
@Immutable
internal sealed class MyListViewState {
    data object Loading : MyListViewState()
    data object Empty : MyListViewState()
    data class Error(val message: String) : MyListViewState()
    data class Content(
        val items: List<VideoItemUIState>,
        val isLoadingNext: Boolean = false,
    ) : MyListViewState()
}
```

## PagingVM implementation

```kotlin
internal class MyListVM(
    router: AppRouter,
    errorHandler: ErrorHandler,
    paginator: Paginator.Store<Item>,
    private val interactor: MyInteractor,
    private val mapper: MyUIMapper,
) : PagingVM<Item, MyListViewState>(paginator, router, errorHandler) {

    override val initialViewState: MyListViewState = MyListViewState.Loading

    private val cachedItems = Collections.synchronizedList(mutableListOf<Item>())

    override fun onStart() {
        init()  // Start paginator
    }

    override fun onAction(action: UIAction) {
        when (action) {
            CommonAction.RetryClicked -> resetPaging()
            CommonAction.Refresh -> refresh()
            is CommonAction.LoadMore -> notifyLoadNextPage()
            is CommonAction.ItemSelected<*> -> onItemSelected(action)
            else -> super.onAction(action)
        }
    }

    // --- Paginator callbacks ---

    override fun onLoadFirstPage() {
        pagingLaunch {
            val result = interactor.getItems()
            result.onSuccess { items ->
                replace(items)
                cachedItems.clearAndAddAll(items)
            }.onFailure { dispatchError(ErrorEntity(it.message.orEmpty())) }
        }
    }

    override fun onLoadNextPage(key: Item?) {
        pagingLaunch {
            val result = interactor.getItems(offset = cachedItems.size)
            result.onSuccess { items ->
                setNextPage(items)
                cachedItems.addAll(items)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dispatchListState(state: Paginator.State) {
        when (state) {
            Paginator.State.Empty -> updateViewState(MyListViewState.Empty)
            Paginator.State.Loading -> updateViewState(MyListViewState.Loading)
            is Paginator.State.Refreshing<*> ->
                showDataState(state.data as List<Item>)
            is Paginator.State.Data<*> ->
                showDataState(state.data as List<Item>)
            is Paginator.State.LoadingNext<*> ->
                showDataState(state.data as List<Item>, isLoadingNext = true)
            is Paginator.State.Error<*> ->
                showErrorState(state)
            is Paginator.State.PageErrorNext<*> ->
                showDataState(state.data as List<Item>)
        }
    }

    private fun showDataState(
        items: List<Item>,
        isLoadingNext: Boolean = false,
    ) {
        updateViewState(
            MyListViewState.Content(
                items = mapper.mapToUiList(items),
                isLoadingNext = isLoadingNext,
            )
        )
    }

    private fun showErrorState(state: Paginator.State) {
        val error = (state as? Paginator.State.Error<*>)?.error
        updateViewState(MyListViewState.Error(error?.message.orEmpty()))
    }

    companion object {
        private const val PAGE_SIZE = 20
    }
}
```

## Parallel loading (first page + extra data)

```kotlin
override fun onLoadFirstPage() {
    pagingLaunch {
        coroutineScope {
            val itemsDeferred = async { interactor.getItems() }
            val configDeferred = async { interactor.getConfig() }

            val items = itemsDeferred.await().getOrThrow()
            configDeferred.await()  // Cached in interactor
            replace(items)
            cachedItems.clearAndAddAll(items)
        }
    }
}
```

## DI for paginated list (Koin scope)

```kotlin
@Parcelize
internal class MyListScreen : PuberScreen {

    private fun buildModule(scopeId: ScopeID, parentScope: Scope) = module {
        scope(named(scopeId)) {
            scoped { Paginator.Store<Item>() }
            scopedOf(::MyInteractor)
            scopedOf(::MyUIMapper)
            viewModelOf(::MyListVM)
        }
    }

    @Composable
    override fun Content() = DIScope(scopeName = key, moduleFactory = ::buildModule) {
        val vm = koinViewModel<MyListVM>()
        val state by vm.collectViewState()
        val onAction: (UIAction) -> Unit = remember(vm) { vm::onAction }
        MyListScreenContent(state = state, onAction = onAction)
    }
}
```

## UI Component for TV

```kotlin
@Composable
internal fun MyListScreenContent(
    state: MyListViewState,
    onAction: (UIAction) -> Unit,
) {
    when (state) {
        is MyListViewState.Loading -> FullScreenProgressIndicator()
        is MyListViewState.Empty -> EmptyState()
        is MyListViewState.Error -> ErrorScreen(state.message, onAction)
        is MyListViewState.Content -> ContentGrid(state, onAction)
    }
}

@Composable
private fun ContentGrid(
    state: MyListViewState.Content,
    onAction: (UIAction) -> Unit,
) {
    TvLazyVerticalGrid(
        columns = TvGridCells.Fixed(5),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(
            items = state.items,
            key = { it.id },
        ) { item ->
            VideoItem(
                state = item,
                onClick = { onAction(CommonAction.ItemSelected(item)) },
                onFocus = { onAction(CommonAction.ItemFocused(item)) },
            )
        }

        // Load more trigger
        if (state.isLoadingNext) {
            item { FullScreenProgressIndicator() }
        } else {
            item {
                LaunchedEffect(Unit) {
                    onAction(CommonAction.LoadMore)
                }
            }
        }
    }
}
```

## Search with cache restoration

```kotlin
private val cachedItemsWithoutSearch = Collections.synchronizedList(mutableListOf<Item>())

override fun onLoadFirstPage() {
    pagingLaunch {
        val result = interactor.getItems(query = filter.searchQuery)
        result.onSuccess { items ->
            replace(items)
            cachedItems.clearAndAddAll(items)
            if (filter.searchQuery.isBlank()) {
                cachedItemsWithoutSearch.clearAndAddAll(items)
            }
        }
    }
}

private fun onSearchClosed() {
    filter = filter.copy(searchQuery = "")
    cachedItems.clearAndAddAll(cachedItemsWithoutSearch)
    if (cachedItemsWithoutSearch.isNotEmpty()) {
        showDataState(cachedItemsWithoutSearch)
    }
    resetPaging()
}
```

## Search VM race prevention

```kotlin
private var query: String = ""

override fun onLoadFirstPage() {
    val requestedQuery = query
    if (requestedQuery.isBlank()) return

    pagingLaunch {
        val result = interactor.search(query = requestedQuery)
        if (requestedQuery != query) return@pagingLaunch  // Stale result
        result.onSuccess { items -> replace(items) }
    }
}
```

## Long-press actions (context menu on TV)

On TV, use focus-based selection with D-pad enter/long-press instead of touch:

```kotlin
VideoItem(
    state = item,
    modifier = Modifier.handleDPadKeyEvents(
        onEnter = { onAction(CommonAction.ItemSelected(item)) },
        onLongPress = { onAction(MyAction.ItemContextMenu(item)) },
    ),
)

// In VM -- show bottom sheet with actions
private val actionsRequestCode = IdGenerator.generateId()

private fun onItemContextMenu(item: VideoItemUIState) {
    router.showOver(
        screen = Screens.itemActions(item.id, resultCode = actionsRequestCode),
        resultCode = actionsRequestCode,
        listener = ::dispatchActionResult,
    )
}
```

## Checklist
- [ ] `PagingVM<Item, ViewState>` base class with paginator, router, errorHandler
- [ ] `Paginator.Store<Item>` registered via `scoped { }` in Koin buildModule
- [ ] `init()` called in `onStart()`
- [ ] `onLoadFirstPage()` -> `replace(items)` + update cache
- [ ] `onLoadNextPage()` -> `setNextPage(items)` + append cache
- [ ] `dispatchListState()` handles ALL `Paginator.State` variants
- [ ] `CommonAction.LoadMore` -> `notifyLoadNextPage()`
- [ ] `CommonAction.Refresh` -> `refresh()`
- [ ] Thread-safe: `Collections.synchronizedList` for cached data
- [ ] API calls return `Result<T>`, handled with `onSuccess`/`onFailure`
- [ ] TV grid layout with focus management
- [ ] No pull-to-refresh (TV doesn't support it)
