# Recipe: ViewModel
> Related: [error-handling.md](error-handling.md), [compose-screen.md](compose-screen.md)

How to create a ViewModel with proper state management.

## File location
```
com/kino/puber/ui/feature/<featureName>/vm/<ScreenName>VM.kt
com/kino/puber/ui/feature/<featureName>/model/<ScreenName>ViewState.kt
com/kino/puber/ui/feature/<featureName>/model/<ScreenName>UIMapper.kt
```

## ViewState (sealed class)

```kotlin
@Immutable
internal sealed class MyViewState {
    data object Loading : MyViewState()
    data object Empty : MyViewState()
    data class Error(val message: String) : MyViewState()
    data class Content(
        val items: List<VideoItemUIState>,
        val title: String = "",
    ) : MyViewState()
}
```

## Action (sealed interface)

```kotlin
internal sealed interface MyAction : UIAction {
    data class ItemClicked(val id: Long) : MyAction
    data object RetryClicked : MyAction
}
```

## ViewModel (PuberVM)

```kotlin
internal class MyVM(
    router: AppRouter,
    override val errorHandler: ErrorHandler,
    private val interactor: MyInteractor,
    private val mapper: MyUIMapper,
) : PuberVM<MyViewState>(router) {

    override val initialViewState = MyViewState.Loading

    override fun onStart() {
        loadData()
    }

    override fun onAction(action: UIAction) {
        when (action) {
            CommonAction.RetryClicked -> loadData()
            CommonAction.Refresh -> refresh()
            is CommonAction.ItemSelected<*> -> onItemSelected(action)
            is MyAction -> onMyAction(action)
            else -> super.onAction(action)
        }
    }

    private fun loadData() = launch {
        updateViewState(MyViewState.Loading)
        val data = interactor.getData()
        updateViewState(mapper.mapToContent(data))
    }

    override fun dispatchError(error: ErrorEntity) {
        if (stateValue is MyViewState.Loading) {
            updateViewState(MyViewState.Error(error.message))
        } else {
            showMessage(error.message)
        }
    }
}
```

## Typed state update

```kotlin
private fun refresh() {
    updateViewState<MyViewState.Content> {
        copy(isRefreshing = true)
    }
    loadData()
}

private fun updateItem(item: VideoItemUIState) {
    updateViewState<MyViewState.Content> {
        val updated = items.map { if (it.id == item.id) item else it }
        copy(items = updated)
    }
}
```

## UI Mapper (API model -> UI state)

```kotlin
internal class MyUIMapper(
    private val resources: ResourceProvider,
    private val videoItemUIMapper: VideoItemUIMapper,
) {
    fun mapToContent(items: List<Item>): MyViewState.Content {
        return MyViewState.Content(
            items = items.map(videoItemUIMapper::map),
        )
    }
}
```

**Note:** No `@InjectConstructor` — Koin resolves via `scopedOf(::MyUIMapper)` in the screen's `buildModule`.

## Checklist
- [ ] No `@InjectConstructor` — pure Koin DSL (`viewModelOf(::MyVM)`)
- [ ] `PuberVM<MyViewState>(router)` base class with `router` and optional `errorHandler`
- [ ] `initialViewState` set to Loading
- [ ] `onStart()` for initial data load (called once, guarded by `AtomicBoolean`)
- [ ] All actions handled in `onAction()` with `when`
- [ ] `CommonAction.RetryClicked`, `Refresh` handled
- [ ] `dispatchError()` — show Error state if Loading, toast if Content
- [ ] `updateViewState<T> { copy(...) }` for typed conditional updates
- [ ] `ResourceProvider` for strings in VM/Mapper (not hardcoded)
- [ ] `launch { }` for coroutines (uses `viewModelScope` + `DefaultExceptionHandler`)
