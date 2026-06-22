# Recipe: Compose Screen
> Related: [ui-components.md](ui-components.md), [viewmodel.md](viewmodel.md), [di-setup.md](di-setup.md)

How to create a new Compose screen in this project.

## File location
```
com/kino/puber/ui/feature/<featureName>/component/<ScreenName>Screen.kt
com/kino/puber/ui/feature/<featureName>/component/<ScreenName>ScreenContent.kt
```

## Screen class (DI + VM setup)

```kotlin
@Parcelize
internal class MyScreen : PuberScreen {

    private fun buildModule(scopeId: ScopeID, parentScope: Scope) = module {
        scope(named(scopeId)) {
            scopedOf(::MyUIMapper)
            scopedOf(::MyInteractor) // if screen-scoped
            viewModelOf(::MyVM)
        }
    }

    @Composable
    override fun Content() = DIScope(scopeName = key, moduleFactory = ::buildModule) {
        val vm = koinViewModel<MyVM>()
        val state by vm.collectViewState()
        val onAction: (UIAction) -> Unit = remember(vm) { vm::onAction }

        MyScreenContent(state = state, onAction = onAction)
    }
}
```

## Screen with params

```kotlin
@Parcelize
internal class MyScreen(private val params: MyScreenParams) : PuberScreen {

    private fun buildModule(scopeId: ScopeID, parentScope: Scope) = module {
        scope(named(scopeId)) {
            scoped { params }
            scopedOf(::MyUIMapper)
            viewModelOf(::MyVM)
        }
    }

    @Composable
    override fun Content() = DIScope(scopeName = key, moduleFactory = ::buildModule) {
        val vm = koinViewModel<MyVM>()
        val state by vm.collectViewState()
        val onAction: (UIAction) -> Unit = remember(vm) { vm::onAction }
        MyScreenContent(state = state, onAction = onAction)
    }
}

@Parcelize
internal data class MyScreenParams(
    val id: Long,
) : Parcelable
```

## Content composable (pure UI rendering)

```kotlin
@Composable
internal fun MyScreenContent(
    state: MyViewState,
    onAction: (UIAction) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
    ) {
        when (state) {
            is MyViewState.Loading -> FullScreenProgressIndicator()
            is MyViewState.Empty -> EmptyContent()
            is MyViewState.Content -> Content(state, onAction)
            is MyViewState.Error -> ErrorContent(state, onAction)
        }
    }
}
```

## Placeholder (loading skeleton)

Use the `placeholder` modifier from compose-placeholder-material3:

```kotlin
Text(
    modifier = Modifier.placeholder(visible = isLoading),
    text = item.title,
    style = MaterialTheme.typography.bodyMedium,
)
```

## Entity caching via ScreenParams

When an entity is already loaded (e.g., navigating from list to details), pass it through ScreenParams to skip the loading state:

```kotlin
@Parcelize
internal data class MyScreenParams(
    val id: Long,
    val item: Item? = null,  // pre-loaded from list
) : Parcelable
```

VM pattern — show content immediately if entity available, load from API otherwise:
```kotlin
override val initialViewState = params.item
    ?.let { mapper.mapToContent(it) }
    ?: MyViewState.Loading

override fun onStart() {
    if (params.item == null) loadData()
}
```

## Screen class + Content separation

Always split into two files:
- **Screen class** (`MyScreen.kt`) — `@Parcelize`, `buildModule()`, `Content()` with DI + VM wiring
- **Screen content** (`MyScreenContent.kt`) — pure UI composables, state rendering

```
ui/feature/<name>/component/MyScreen.kt           <- DI + VM
ui/feature/<name>/component/MyScreenContent.kt    <- all @Composable functions
```

## Anti-patterns
- **No data mapping in composables**: Label-value pairs, field lists, and any model-to-UI mapping belong in VM/Mapper via `ResourceProvider`. Composable = pure rendering only.
- Use `PuberScreen` with `@Parcelize`.
- Override `Content()` from Voyager's `Screen` interface.
- Use `buildModule(scopeId, parentScope)` returning a Koin `module {}`.
- Use `koinViewModel<T>()` to retrieve VMs.

## Checklist
- [ ] `@Parcelize` on screen class
- [ ] `PuberScreen` base with `@Parcelize`
- [ ] `buildModule()` with Koin `module { scope(named(scopeId)) { ... } }`
- [ ] `DIScope(scopeName = key, moduleFactory = ::buildModule)`
- [ ] `koinViewModel<MyVM>()` to retrieve VM
- [ ] `vm.collectViewState()` — method on VM, not extension
- [ ] `remember(vm) { vm::onAction }` for stable action lambda
- [ ] Two-layer: Screen (DI + VM) -> Content (pure UI)
- [ ] Handle all states (Loading, Content, Error, Empty)
- [ ] TV Surface as root layout
- [ ] Strings in `res/values/strings.xml`
