# Recipe: DI Setup (Koin)
> Related: [compose-screen.md](compose-screen.md), [navigation.md](navigation.md)

How to configure dependency injection with Koin 4.1.0.

## Global modules (PuberApp.kt)

```kotlin
startKoin {
    androidContext(this@PuberApp)
    modules(
        resourceModule,
        handlersModule,
        apiModule,
        repositoryModule,
        interactorModule,
    )
}
```

Global singletons use `singleOf` with optional interface binding:
```kotlin
val interactorModule = module {
    singleOf(::AuthInteractor) { bind<IAuthInteractor>() }
    singleOf(::DeviceInfoInteractor)
}
```

## Screen-scoped module (buildModule)

Each screen defines a `buildModule` function that creates a Koin module with a named scope:

```kotlin
private fun buildModule(scopeId: ScopeID, parentScope: Scope) = module {
    scope(named(scopeId)) {
        scopedOf(::MyUIMapper)
        scopedOf(::MyInteractor)  // screen-scoped interactor
        viewModelOf(::MyVM)
    }
}
```

## Scope lifecycle (DIScope composable)

```kotlin
@Composable
override fun Content() = DIScope(scopeName = key, moduleFactory = ::buildModule) {
    val vm = koinViewModel<MyVM>()
    // ...
}
```

`DIScope` manages scope lifecycle via `ScopeModuleManager : RememberObserver`. When the composable leaves composition, the scope is closed and dependencies are released.

## VM registration and retrieval

```kotlin
// In buildModule — register
viewModelOf(::MyVM)

// In Content() — retrieve
val vm = koinViewModel<MyVM>()
```

## Scoped dependencies

```kotlin
// Plain class scoped to screen lifecycle
scopedOf(::MyUIMapper)
scopedOf(::MyInteractor)

// With params instance
scope(named(scopeId)) {
    scoped { params }  // MyScreenParams instance
    viewModelOf(::MyVM)
}
```

## Singleton with interface binding

```kotlin
// Global module
singleOf(::KinoPubRepository) { bind<IKinoPubRepository>() }
singleOf(::CryptoPreferenceRepository) { bind<ICryptoPreferenceRepository>() }
singleOf(::DefaultErrorHandler) { bind<ErrorHandler>() }
```

## Scope hierarchy

```
PuberApp (global singletons: apiClient, repositories, global interactors)
  └── FlowScreen scope (shared interactors across sub-screens)
      └── ListScreen scope (VM, mapper)
      └── DetailsScreen scope (VM, mapper, params)
```

## Flow screen DI (shared dependencies)

```kotlin
@Parcelize
internal class MyFlowScreen : PuberScreen {

    private fun buildModule(scopeId: ScopeID, parentScope: Scope) = module {
        scope(named(scopeId)) {
            viewModelOf(::MyFlowVM)
            // Shared across all screens in this flow
            scopedOf(::SharedInteractor)
            scopedOf(::SharedUIMapper)
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

## Important rules
- **No annotations**: No `@InjectConstructor`, `@Inject`, no kapt/KSP for DI — pure Koin DSL only
- **No Toothpick**: No `bind<T>().singleton()`, no `scope.module {}` Toothpick syntax
- **Module boundary**: Feature/runtime DI belongs in `:app`; `:baselineprofile` does not own app DI
- `viewModelOf(::VM)` for ViewModels
- `scopedOf(::Class)` for screen-scoped dependencies
- `singleOf(::Class)` for global singletons
- `singleOf(::Impl) { bind<Interface>() }` for interface bindings
- `koinViewModel<VM>()` to retrieve in composables

## Checklist
- [ ] `buildModule(scopeId, parentScope)` returns Koin `module { scope(named(scopeId)) { ... } }`
- [ ] `DIScope(scopeName = key, moduleFactory = ::buildModule)` in `Content()`
- [ ] `viewModelOf(::VM)` for VM registration
- [ ] `scopedOf(::Class)` for mappers, interactors
- [ ] `koinViewModel<VM>()` for retrieval
- [ ] Global singletons in `PuberApp` modules
- [ ] No DI annotations anywhere
