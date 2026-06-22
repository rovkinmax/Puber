# Recipe: Navigation
> Related: [compose-screen.md](compose-screen.md), [di-setup.md](di-setup.md)

How to set up navigation for screens using Voyager + AppRouter.

## Screen definition

```kotlin
@Parcelize
internal class MyScreen : PuberScreen

@Parcelize
internal class MyDetailsScreen(private val params: MyScreenParams) : PuberScreen
```

All screens use `@Parcelize` and extend `PuberScreen` (which implements Voyager's `Screen` + `Parcelable`). Default key = `javaClass.simpleName`.

## Screen factory (Screens interface)

```kotlin
// Screens.kt — interface with factory methods
interface Screens {
    fun auth(): PuberScreen
    fun main(): PuberScreen
    fun favorites(): PuberScreen
    fun details(id: Long): PuberScreen
}

// ScreensImpl.kt — implementation
class ScreensImpl : Screens {
    override fun auth() = AuthScreen()
    override fun main() = MainScreen()
    override fun favorites() = FavoritesScreen()
    override fun details(id: Long) = DetailsScreen(DetailsScreenParams(id))
}
```

## Standard navigation (AppRouter)

```kotlin
// Navigate forward (push)
router.navigateTo(router.screens.details(id))

// Navigate back (pop)
router.back()

// Replace current screen
router.replaceScreen(router.screens.main())

// New root (replace entire stack)
router.newRootScreen(router.screens.main())
```

## AppRouter commands

| Method | Description |
|--------|-------------|
| `navigateTo(screen)` | Push screen onto stack |
| `replaceScreen(screen)` | Replace current screen |
| `back()` | Pop current screen |
| `newRootScreen(screen)` | Replace entire stack |
| `showOver(screen)` | Show as bottom sheet |
| `hideBottomSheet()` | Dismiss bottom sheet |
| `backTo(screen)` | Pop to specific screen |
| `closeRootFlow()` | Close current flow |

## Navigate for result

Result codes are generated via `IdGenerator.generateId()` — never hardcode.
The calling VM generates the code and passes it to the target screen via params.
The target screen uses `params.resultCode` when returning a result.

```kotlin
// In calling VM — generate unique result code
private val detailsRequestId = IdGenerator.generateId()

private fun openDetails(id: Long) {
    router.navigateForResult(
        screen = router.screens.details(id),
        requestCode = detailsRequestId,
        listener = ::handleDetailsResult,
    )
}

private fun handleDetailsResult(result: SomeResult?) {
    if (result == null) return
    // Update local state with result
}
```

## Bottom sheet with result

Bottom sheet screens must use `BottomSheetScreenContainer` for TV focus management.
Back button dismissal is automatic via `FlowComponent`'s `BackHandler`.

```kotlin
// In calling VM
private val pickerRequestId = IdGenerator.generateId()

router.showOver(
    screen = MyPickerScreen(MyPickerParams(
        selection = currentSelection,
        resultCode = pickerRequestId,
    )),
    resultCode = pickerRequestId,
    listener = { result: List<String>? ->
        if (result != null) onSelectionChanged(result)
    },
)

// Inside picker Screen.Content()
BottomSheetScreenContainer {
    // content auto-receives focus via rememberFocusRequesterOnLaunch + focusGroup
    MyPickerContent(state = state, onOptionSelected = { ... })
}

// Inside picker VM — return result using code from params
router.hideBottomSheet(resultCode = params.resultCode, result = selectedItems)
```

## FlowComponent setup

```kotlin
@Parcelize
internal class MyFlowScreen : PuberScreen {

    private fun buildModule(scopeId: ScopeID, parentScope: Scope) = module {
        scope(named(scopeId)) {
            viewModelOf(::MyFlowVM)
            scopedOf(::SharedInteractor) // shared across sub-screens
        }
    }

    @Composable
    override fun Content() = DIScope(scopeName = key, moduleFactory = ::buildModule) {
        FlowComponent(
            scopeName = key,
            flowViewModel = { koinViewModel<MyFlowVM>() },
        )
    }
}
```

## Tab navigation

```kotlin
// PuberTab — extends Voyager Tab
// TabRouter with TabCommand.Open(tab)
tabRouter.open(MyTab)
```

## Closing bottom sheets

```kotlin
// With result — for pickers, selections
router.back(resultCode = params.resultCode, result = selectedItems)

// Without result — simple dismissal
router.hideBottomSheet()
```

## Checklist
- [ ] `@Parcelize` on all screen classes
- [ ] `PuberScreen` base with `@Parcelize`
- [ ] Params as `@Parcelize data class` implementing `Parcelable`
- [ ] Factory methods in `Screens` interface / `ScreensImpl`
- [ ] Navigation via `router` (constructor param of `PuberVM`)
- [ ] `navigateForResult` with typed listener for result-returning flows
- [ ] `FlowComponent` for multi-screen flows with shared scope
