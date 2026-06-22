# Recipe: Error Handling
> Related: [viewmodel.md](viewmodel.md)

How to handle errors in ViewModels, coroutines, and UI.

## ErrorHandler in PuberVM

Base class `PuberVM` declares errorHandler as optional:

```kotlin
// PuberVM base class
protected open val errorHandler: ErrorHandler? = null
```

To use error handling, VM must **explicitly override** it in the constructor:

```kotlin
internal class MyVM(
    router: AppRouter,
    override val errorHandler: ErrorHandler,  // required!
    private val interactor: MyInteractor,
) : PuberVM<MyViewState>(router) { ... }
```

Without the override, `errorHandler` is `null` and errors from `launch {}` are not handled.

## ErrorHandler interface

```kotlin
interface ErrorHandler {
    fun proceed(error: Throwable): ErrorEntity
    fun proceedInvoke(error: Throwable, dispatch: (ErrorEntity) -> Unit)
    fun map(error: Throwable): String
}
```

`DefaultErrorHandler` is the standard implementation, registered globally via Koin:
```kotlin
singleOf(::DefaultErrorHandler) { bind<ErrorHandler>() }
```

## How it works

1. `launch {}` catches exceptions via `DefaultExceptionHandler`
2. `DefaultExceptionHandler` calls `errorHandler?.proceedInvoke(throwable, ::dispatchError)`
3. `errorHandler` maps the exception to `ErrorEntity`
4. `dispatchError(ErrorEntity)` is called on the VM
5. VM decides how to display the error based on current state

## dispatchError pattern

Override `dispatchError` and decide what to do based on current ViewState:

```kotlin
override fun dispatchError(error: ErrorEntity) {
    when (stateValue) {
        is MyViewState.Loading -> {
            // First load failed — show Error state
            updateViewState(MyViewState.Error(error.message))
        }
        is MyViewState.Content -> {
            // Already have content — show toast, keep content
            showMessage(error.message)
        }
        else -> {
            showMessage(error.message)
        }
    }
}
```

**Key rule:** If user sees content, keep it and show toast. If screen is loading, switch to Error state.

## launch with DefaultExceptionHandler

```kotlin
// How launch {} works in PuberVM base class:
protected fun launch(
    context: CoroutineContext = DefaultExceptionHandler {
        errorHandler?.proceedInvoke(it, ::dispatchError)
    },
    block: suspend CoroutineScope.() -> Unit,
): Job
```

## CancellationException

**NEVER catch CancellationException** — it breaks structured concurrency. The errorHandler already handles this correctly.

If you must use try-catch manually:

```kotlin
// WRONG
try {
    interactor.doSomething()
} catch (e: Exception) {
    // catches CancellationException too!
    showError(e.message)
}

// RIGHT
try {
    interactor.doSomething()
} catch (e: CancellationException) {
    throw e  // always rethrow
} catch (e: Exception) {
    showError(e.message)
}
```

## Result handling from API

Since `KinoPubApiClient` returns `Result<T>`, you can also handle errors explicitly:

```kotlin
private fun loadData() = launch {
    updateViewState(MyViewState.Loading)
    apiClient.getItems()
        .onSuccess { response ->
            updateViewState(mapper.mapToContent(response.items))
        }
        .onFailure { error ->
            updateViewState(MyViewState.Error(error.message.orEmpty()))
        }
}
```

Or use `.getOrThrow()` and let `DefaultExceptionHandler` handle failures:
```kotlin
private fun loadData() = launch {
    updateViewState(MyViewState.Loading)
    val items = interactor.getItems() // calls .getOrThrow() internally
    updateViewState(mapper.mapToContent(items))
}
```

## Error state in UI

```kotlin
@Composable
internal fun MyScreenContent(state: MyViewState, onAction: (UIAction) -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        when (state) {
            is MyViewState.Loading -> FullScreenProgressIndicator()
            is MyViewState.Error -> ErrorContent(
                message = state.message,
                onRetry = { onAction(CommonAction.RetryClicked) },
            )
            is MyViewState.Content -> Content(state, onAction)
        }
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text(text = stringResource(R.string.retry))
        }
    }
}
```

## Retry pattern

```kotlin
// In VM
override fun onAction(action: UIAction) {
    when (action) {
        CommonAction.RetryClicked -> loadData()
        // ...
    }
}
```

## Typed state update for error recovery

```kotlin
// Reset loading flag on error
override fun dispatchError(error: ErrorEntity) {
    updateViewState<MyViewState.Content> {
        copy(isLoading = false)
    }
    showMessage(error.message)
}
```

## Checklist
- [ ] `override val errorHandler: ErrorHandler` in VM constructor
- [ ] `dispatchError()` handles Loading -> Error state, Content -> toast
- [ ] `CancellationException` always rethrown in manual try-catch
- [ ] `CommonAction.RetryClicked` handled in `onAction`
- [ ] `FullScreenProgressIndicator` for loading, Error state for failures
- [ ] `DefaultExceptionHandler` used via `launch { }` (automatic in PuberVM)
- [ ] No swallowed exceptions (every catch has visible feedback)
