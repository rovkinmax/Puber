# Recipe: ViewModel -- Advanced Patterns
> Load this recipe additionally when the step involves: parallel loading, form/draft, flow listeners, label-value details, grouped lists.
> Core patterns are in [viewmodel.md](viewmodel.md).

## Parallel loading (coroutineScope + async)

```kotlin
private fun loadData() = launch {
    updateViewState(MyViewState.Loading)
    coroutineScope {
        val dataDeferred = async { interactor.loadData() }
        val configDeferred = async { interactor.getConfig() }

        val data = dataDeferred.await().getOrThrow()
        val config = configDeferred.await().getOrThrow()
        updateViewState(mapper.mapToContent(data, config))
    }
}
```

## Flow listeners (real-time updates)

```kotlin
override fun onStart() {
    loadData()
    interactor.observeChanges()
        .drop(1)  // skip initial value
        .debounce(600L)
        .onEach { reloadData() }
        .launchIn(viewModelScope)
}
```

## Debounced batch updates

```kotlin
private val updatesMap = ConcurrentHashMap<String, Job>()

private fun scheduleUpdate(key: String, action: suspend () -> Unit) {
    updatesMap[key]?.cancel()
    updatesMap[key] = launch {
        delay(800L)
        action()
    }
}
```

## Form VM (Draft model)

For create/edit screens, VM holds a mutable draft and updates ViewState on each change:

```kotlin
internal class EditMyVM(
    router: AppRouter,
    private val params: EditMyParams,
    private val interactor: MyInteractor,
    private val resources: ResourceProvider,
) : PuberVM<EditMyViewState>(router) {

    override val errorHandler: ErrorHandler? = null

    private var draft = params.entity?.let(::mapEntityToDraft) ?: MyDraft()

    override val initialViewState = mapDraftToContent(draft)

    private fun updateDraft(block: MyDraft.() -> MyDraft) {
        draft = draft.block()
        updateViewState(mapDraftToContent(draft))
    }

    private fun onSave() = launch {
        if (!draft.isValid) return@launch
        updateViewState<EditMyViewState.Content> { copy(isSaving = true) }
        val result = if (params.entity != null) {
            interactor.update(params.entity.id, mapDraftToParams(draft))
        } else {
            interactor.create(mapDraftToParams(draft))
        }
        result.onSuccess { router.back() }
              .onFailure { updateViewState<EditMyViewState.Content> { copy(isSaving = false) } }
    }
}
```

Draft model:
```kotlin
internal data class MyDraft(
    val name: String = "",
    val description: String = "",
    val type: MyType = MyType.DEFAULT,
) {
    val isValid: Boolean get() = name.isNotBlank()
}
```

**Debounced auto-save** (for inline draft saving):
```kotlin
private var saveJob: Job? = null

private fun saveDraftField(apiCall: suspend () -> Unit) {
    saveJob?.cancel()
    saveJob = launch {
        delay(800L)
        apiCall()
    }
}
```

## Grouped list mapper (sections by category)

```kotlin
fun mapToSectionedList(items: List<Item>): List<ItemUi> {
    return buildList {
        var previousType: String? = null
        items.forEach { item ->
            val type = item.type
            if (type != null && type != previousType) {
                previousType = type
                add(ItemUi.Section(title = type))
            }
            add(mapItem(item))
        }
    }
}
```

## Picker result handling

Result codes are generated via `IdGenerator.generateId()` and passed to the target screen via params.

```kotlin
private val pickerRequestId = IdGenerator.generateId()

private fun openPicker() {
    router.showOver(
        screen = Screens.picker(
            selected = currentSelection,
            resultCode = pickerRequestId,
        ),
        resultCode = pickerRequestId,
        listener = ::dispatchPickerResult,
    )
}

@Suppress("UNCHECKED_CAST")
private fun dispatchPickerResult(result: Any?) {
    val selected = result as? List<MyEnum> ?: return
    onSelectionChanged(selected)
}
```

## Label-value mapping (details screens)

For detail screens that display a list of label-value pairs -- build the pairs in the UIMapper, NOT in the composable.

**Wrong** -- mapping with `stringResource()` in composable:
```kotlin
// DON'T: composable builds data structure from string resources
@Composable
private fun buildDetailFields(state: Content): List<Pair<String, String>> {
    return buildList {
        add(stringResource(R.string.name_label) to state.name)
    }
}
```

**Right** -- mapping with `ResourceProvider` in UIMapper:
```kotlin
// DO: UIMapper builds fields via ResourceProvider
internal class MyUIMapper(private val resources: ResourceProvider) {

    fun mapToContent(item: Item): Content {
        val fields = buildList {
            add(resources.getString(R.string.name_label) to item.title.orEmpty())
            item.year?.let {
                add(resources.getString(R.string.year_label) to it.toString())
            }
        }
        return Content(fields = fields)
    }
}
```

ViewState stores ready-to-render `List<Pair<String, String>>`. Composable just iterates and renders.

## Search VM (separate paginator)

For screens that already have a paginated list, the search needs its own `Paginator.Store` to avoid DI conflict. The search VM creates the paginator in its constructor (not from DI):

```kotlin
internal class MySearchVM(
    router: AppRouter,
    errorHandler: ErrorHandler,
    private val interactor: MyInteractor,
) : PagingVM<Item, MySearchViewState>(
    paginator = Paginator.Store(),  // own instance, NOT from DI
    router = router,
    errorHandler = errorHandler,
) {
    override val initialViewState: MySearchViewState = MySearchViewState.QueryEmpty
    private var query: String = ""

    override fun onAction(action: UIAction) {
        when (action) {
            is CommonAction.TextChanged -> onSearchChanged(action.text)
            CommonAction.RetryClicked -> resetPaging()
            is CommonAction.LoadMore -> notifyLoadNextPage()
            else -> super.onAction(action)
        }
    }

    private fun onSearchChanged(newQuery: String) {
        query = newQuery
        if (newQuery.isBlank()) {
            updateViewState(MySearchViewState.QueryEmpty)
        } else {
            updateViewState(MySearchViewState.Loading)
            resetPaging()
        }
    }

    override fun onLoadFirstPage() {
        val requestedQuery = query
        if (requestedQuery.isBlank()) return
        pagingLaunch {
            val result = interactor.search(query = requestedQuery)
            if (requestedQuery != query) return@pagingLaunch  // Race prevention
            result.onSuccess { items -> replace(items) }
        }
    }
}
```

**ViewState for search:**
```kotlin
@Immutable
internal sealed class MySearchViewState {
    data object QueryEmpty : MySearchViewState()
    data object Loading : MySearchViewState()
    data class Content(val items: List<VideoItemUIState>) : MySearchViewState()
    data class Error(val message: String) : MySearchViewState()
}
```

**Key points:**
- No `@InjectConstructor` -- Koin DSL with `viewModelOf(::MySearchVM)`
- `Paginator.Store()` -- own instance, separate from parent VM's paginator
- Race prevention: `if (requestedQuery != query) return@pagingLaunch`
- API returns `Result<T>`, handled with `onSuccess`/`onFailure`
