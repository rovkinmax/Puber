# Puber — Android TV Client for KinoPub

## Project Overview
Puber is an Android TV application (client for the KinoPub video streaming service).
Package: `com.kino.puber`. Main application code lives in `:app`; baseline profile generation lives in
`:baselineprofile`.

## Build System
- **Modules**: `:app` for application code and `:baselineprofile` for benchmark/baseline profile generation. There are
  no feature modules or core Gradle modules.
- **Versions**: all versions in `gradle/libs.versions.toml` (AGP, Kotlin, KSP, libraries) — always read from there, do not hardcode
- **Product flavors**: `dev` (`.stage` suffix) and `prod`
- **Compile command**: `./gradlew :app:compileDevDebugKotlin`
- **Version catalog**: `gradle/libs.versions.toml`
- **Compose compiler stability**: `config/compose/compiler_config.conf`
- **Static analysis**: Detekt with compose rules (`ru.kode:detekt-rules-compose`)
- **No buildSrc convention plugins** — minimal buildSrc with `Versions.kt`

## Architecture

### DI: Koin 4.1.0
- **Global modules** in `PuberApp.kt`: `resourceModule`, `handlersModule`, `apiModule`, `repositoryModule`, `interactorModule`
- **Screen-scoped modules** defined in `buildModule(scopeId, parentScope)` inside each Screen class
- **Scope lifecycle** managed by `ScopeModuleManager : RememberObserver` via `DIScope()` composable
- **VM registration**: `viewModelOf(::SomeVM)` in scope block, retrieved via `koinViewModel<SomeVM>()`
- **Scoped deps**: `scopedOf(::SomeClass)` in scope block
- **Singletons with interface**: `singleOf(::Impl) { bind<IInterface>() }`
- **No annotations** — pure Koin DSL, no `@InjectConstructor`, no `@Inject`, no kapt/KSP for DI

### ViewModel: PuberVM<ViewState>
- **Base class**: `PuberVM<ViewState>(router: AppRouter)` extends AndroidX `ViewModel`
- **State**: `MutableStateFlow<ViewState>`, exposed via `collectViewState()` composable
- **Lifecycle**: `onStart()` called once (guarded by `AtomicBoolean`), back dispatcher auto-registered
- **Updates**: `updateViewState(newState)` or typed `updateViewState<T> { copy(...) }`
- **Actions**: `onAction(action: UIAction)` with `when` dispatch
- **Coroutines**: `launch { }` using `viewModelScope` + `DefaultExceptionHandler`
- **Error handling**: optional `errorHandler: ErrorHandler?`, `dispatchError(ErrorEntity)`
- **Navigation**: via `router: AppRouter` (constructor param)

### PagingVM<T, VS>
- Extends `PuberVM<VS>`, adds `Paginator.Store<T>`
- Abstract: `onLoadFirstPage()`, `onLoadNextPage(key)`, `dispatchListState(state)`
- Helpers: `replace(list)`, `setNextPage(list)`, `notifyLoadNextPage()`, `resetPaging()`

### Navigation: Voyager + AppRouter
- **Library**: Voyager 1.1.0-beta03 (navigator, tab-navigator, bottom-sheet-navigator, koin)
- **Screen interface**: `PuberScreen : Screen, Parcelable` (default key = `javaClass.simpleName`)
- **All screens**: `@Parcelize class/object XxxScreen : PuberScreen`
- **Parameterized screens must override `key`**: use stable navigation params in `ScreenKey` (for example,
  `DetailsScreen_${params.itemId}` or `PlayerScreen_${params.itemId}_s${season}_e${episode}`) so Voyager saveable
  state, TV focus state, and `DIScope(scopeName = key)` are isolated per item. Computed keys in `@Parcelize` screens
  must be annotated with `@IgnoredOnParcel`.
- **AppRouter**: command bus emitting `Command` sealed class via `MutableSharedFlow`
  - `navigateTo(screen)` — push
  - `replaceScreen(screen)` — replace current
  - `showOver(screen)` — bottom sheet
  - `back()` — pop
  - `newRootScreen(screen)` — replace stack
  - `navigateForResult<T>(screen, requestCode, listener)` — with result
  - `showOver(screen, resultCode, listener)` — bottom sheet with result
  - `hideBottomSheet()`, `backTo(screen)`, `closeRootFlow()`
- **FlowComponent**: sets up `BottomSheetNavigator` + `Navigator` + creates scoped `AppRouter`
- **Screen factory**: `Screens` interface with `auth()`, `main()`, `favorites()`, etc. Implemented by `ScreensImpl`
- **Tab navigation**: `PuberTab` + `TabRouter` with `TabCommand.Open(tab)`

### API Layer: Ktor + OkHttp
- **Single client**: `KinoPubApiClient` with `HttpClient(OkHttp)` engine
- **Plugins**: `Auth` (Bearer with refresh), `KinoPubParametersPlugin`, `CurlLogger` (debug), `HttpTimeout`, `ContentNegotiation` (kotlinx.serialization JSON), `DefaultRequest`, `HttpRequestRetry`
- **All endpoints**: `suspend fun` returning `Result<T>` via `apiCall { httpClient.get/post(...) }`
- **Base URLs**: `KinoPubConfig.MAIN_API_BASE_URL`, `KinoPubConfig.OAUTH_BASE_URL`
- **Token storage**: `ICryptoPreferenceRepository` (encrypted SharedPrefs)
- **No Retrofit**, no separate API interfaces per feature — all in one client

### Domain Layer
- **No separate domain entity classes** — uses API models (`data.api.models.Item`) directly
- **Interactors**: plain classes, constructor-injected via Koin
  - Global singletons: `AuthInteractor`, `DeviceInfoInteractor`, `DeviceSettingInteractor`
  - Screen-scoped: `FavoritesInteractor` (declared in screen's `buildModule`)
- **Interface pattern**: `IAuthInteractor` / `AuthInteractor` for global singletons; no interface for scoped-only interactors
- **Repository**: `IKinoPubRepository` / `KinoPubRepository` (auth state), `ICryptoPreferenceRepository` / `CryptoPreferenceRepository`
- **Cache**: `TypedTtlCache<K, V>` / `TypedTtlCacheImpl` — TTL in-memory cache with per-key Mutex

### UI Layer: Compose + TV Material3
- **Theme**: `PuberTheme` wraps both `tv.material3.MaterialTheme` and `material3.MaterialTheme`
- **TV-first**: uses `androidx.tv.material3.Surface`, `Card`, `Text`, etc.
- **Image loading**: Coil 3 (`AsyncImage`, `coil-network-ktor3`)
- **Placeholders**: `compose-placeholder-material3` library

### Screen Structure Pattern
```
ui/feature/<name>/
  component/
    XxxScreen.kt       — @Parcelize screen class + buildModule() + Content()
    XxxScreenContent.kt — pure Composable(state, onAction)
  vm/
    XxxVM.kt           — extends PuberVM<XxxViewState>
  model/
    XxxViewState.kt    — @Immutable sealed class (Loading/Empty/Error/Content)
    XxxScreenParams.kt — navigation params (optional)
    XxxUIMapper.kt     — maps API models → UI state
```

### UIAction Pattern
- `interface UIAction` — base marker
- `sealed class CommonAction : UIAction` — generic actions:
  - `ItemSelected<T>`, `ItemFocused<T>`, `ItemRemoved<T>`
  - `RetryClicked`, `Refresh`, `LoadMore`, `ReloadNextPage`
  - `TextChanged(text, tag)`, `ContinueClicked`, `Share`, `OnResume`
- Feature-specific actions extend `UIAction` directly

### ViewState Pattern
```kotlin
@Immutable
internal sealed class XxxViewState {
    data object Loading : XxxViewState()
    data object Empty : XxxViewState()
    data class Error(val message: String) : XxxViewState()
    data class Content(...) : XxxViewState()
}
```

### Screen Class Pattern
```kotlin
@Parcelize
internal class XxxScreen : PuberScreen {
    private fun buildModule(scopeId: ScopeID, parentScope: Scope) = module {
        scope(named(scopeId)) {
            scopedOf(::XxxUIMapper)
            scopedOf(::XxxInteractor)  // if scoped
            viewModelOf(::XxxVM)
        }
    }

    @Composable
    override fun Content() = DIScope(scopeName = key, moduleFactory = ::buildModule) {
        val vm = koinViewModel<XxxVM>()
        val state by vm.collectViewState()
        val onAction: (UIAction) -> Unit = remember(vm) { vm::onAction }
        XxxScreenContent(state = state, onAction = onAction)
    }
}
```

## Shared UIKit Components
Located in `core/ui/uikit/component/`:
- `VideoItem` + `VideoItemUIState` — card with poster image
- `VideoGrid` + `VideoGridUIState` — lazy column of lazy rows grouped by type
- `VideoItemGridDetails` + `VideoDetailsUIState` — details panel with poster, ratings, metadata
- `FullScreenProgressIndicator` — loading spinner
- `RatingUIState` — sealed: `IMDB`, `KP`, `PUB`
- Modifier utilities: `ifElse`, `FocusOnLaunchRequester`, `Placeholder`

## Core Utilities
- `ResourceProvider` — string resource abstraction
- `ErrorHandler` / `DefaultErrorHandler` — error mapping
- `ErrorEntity` — error value type
- `TypedTtlCache<K, V>` — TTL in-memory cache
- `VideoItemUIMapper` — `Item` → `VideoItemUIState`
- `VideoItemTypeMapper` — `ItemType` → display string
- `DefaultExceptionHandler` — coroutine exception handler

## Key Dependencies
All exact versions are in `gradle/libs.versions.toml` — always read from there.

| Category | Library |
|---|---|
| DI | Koin Android + Compose |
| Navigation | Voyager |
| HTTP | Ktor + OkHttp |
| Serialization | kotlinx.serialization-json |
| Coroutines | kotlinx.coroutines |
| Images | Coil 3 |
| Compose | BOM (androidx.compose) |
| TV UI | tv-foundation + tv-material |
| Crypto | security-crypto |
| Date/Time | kotlinx-datetime |
| QR | chaintech/qr-kit |
| Logging | Timber |

## Strings and Resources

- All user-visible strings must be in `res/values/strings.xml`
- Use `stringResource(R.string.xxx)` in Composable functions
- Use `ResourceProvider` for strings in non-Composable context (ViewModels, Mappers)
- Create UIMapper classes for API model → UI state transformations with localized strings

## Questions & Clarifications

- When you need to ask the user **2 or more** clarifying questions, ALWAYS use the `ask_question` tool. It provides a structured UI with selectable options instead of a wall of inline text. A single simple question can be asked as plain text, but multiple questions — only via the tool.

## Build

- For build errors use: `./gradlew :app:compileDevDebugKotlin 2>&1 | grep -E "e: |error:|FAILURE|What went wrong" -A3` — catches Kotlin compiler errors, Java errors, and Gradle failures in one pass
- App module has flavors: use `:app:compileDevDebugKotlin` (NOT `:app:compileDebugKotlin`)
- In both project-local and Kent-managed task worktrees, use `./tools/agentw :app:compileDevDebugKotlin` instead of
  direct `./gradlew` so Gradle state is isolated under `~/.gradle-agents`.

## Kent Workflow

Active Kent infrastructure lives under `.kent/`:

- commands: `.kent/commands/`
- skill: `.kent/skills/puber-android-workflow/`
- subagents: `.kent/subagents/`
- MCP bridge: `.kent/adapters/mcp/`
- worktree setup: `.kent/worktrees/setup.sh`

Kent commands are invoked as `/prompt:<name>`, for example `/prompt:feature-start` or `/prompt:refactor-start`.

- Project-local/manual worktrees must be created under `.kent/worktrees/`; do not create sibling worktrees such as
  `../Puber-<task>`.
- Kent-managed workflow task worktrees are owned by Kent and may live under
  `~/.kent/worktrees/workspace-.../<TASK-ID>`. Do not move or recreate them manually.
- `.kent/worktrees/setup.sh` and `tools/agentw` use `tools/configure-worktree-sdk` to write an ignored
  `local.properties` containing only `sdk.dir`; they must not copy `PUBER_CLIENT_SECRET`, `TMDB_READ_ACCESS_TOKEN`, or
  other project secrets into task worktrees.

MCP access is through wrapper scripts, not native `mcp__...` tool names:

```bash
.kent/adapters/mcp/mcp-list.sh <server> --schema
.kent/adapters/mcp/mcp-call.sh <server.tool> [arguments] --raw-dir ".todo/<task>/mcp"
```

## Feature Workflow

Commands in `.kent/commands/`, recipes in `.kent/skills/puber-android-workflow/references/recipes/`, feature data cached in `.todo/` (gitignored).

### Pipeline: `/prompt:feature-start` → `/prompt:feature-implement` → `/prompt:feature-review`

1. **Prepare** — `/prompt:feature-start` (or manually: init → design → spec → plan)
2. **Implement** — `/prompt:feature-implement` per step (auto-loads recipes + design + spec for current step)
3. **Review** — `/prompt:feature-review` → `/prompt:feature-fix` (maker-checker, up to 3 iterations)

Standalone commands (plan AND execute in one go): `/prompt:refactor-start`, `/prompt:migration-start`

### Auto-detection (no /slash-command needed)

The agent recognizes intent from natural language, but feature target selection is explicit:

| User says / does | Agent action |
|------------------|--------------|
| Shares a Figma URL | `/prompt:feature-start` or `/prompt:feature-design` with an explicit `.todo/<feature>` target |
| "new feature", "start feature" | `/prompt:feature-start` |
| Shares a spec file/URL | `/prompt:feature-spec` with an explicit `.todo/<feature>` target |
| "next step", "let's code", "implement step 3" | `/prompt:feature-implement` for the explicitly named workspace |
| "status", "where are we", "progress" | Show plan progress for the explicitly named workspace; ask if ambiguous |
| "load context", "remind me" | `/prompt:feature-context` for the explicitly named workspace |
| "review", "check against design" | `/prompt:feature-review` for the explicitly named workspace |
| "update mockup", "refresh design" | `/prompt:feature-design --refresh` with an explicit `.todo/<feature>` target |

### Session rules
- Do not infer feature state from `.todo/.current`; resolve a `.todo/<feature>` workspace from the request or task
  context.
- Reference cached design/spec from `.todo/` instead of calling Figma MCP
- After completing a plan step, update `[ ]` → `[x]` in `plan.md`

## MCP Mobile Testing

### Shared Device Coordination

Before touching an emulator/device, acquire a shared lock so parallel Kent sessions do not install over each other or
fight for focus. Physical devices, including a real TV, are forbidden unless the task/user explicitly provides permission
and an explicit serial for that physical device. Never rely on adb's default target selection.

```bash
EMULATORS=($(.kent/adapters/mobile/emulator-resource-lock.sh adb-emulators))

if ((${#EMULATORS[@]} > 0)); then
  LOCK_OUTPUT="$(.kent/adapters/mobile/emulator-resource-lock.sh acquire-any "${EMULATORS[@]}" -- 900 7200)"
  LOCK_RESOURCE="$(printf '%s\n' "$LOCK_OUTPUT" | sed -n 's/^resource=//p')"
  LOCK_TOKEN="$(printf '%s\n' "$LOCK_OUTPUT" | sed -n 's/^token=//p')"
  DEVICE_SERIAL="$LOCK_RESOURCE"
else
  echo "No running adb emulator is available; block unless the task/user explicitly allows starting an emulator." >&2
  exit 75
fi

trap '.kent/adapters/mobile/emulator-resource-lock.sh release "$LOCK_RESOURCE" "$LOCK_TOKEN"' EXIT
```

Release it after the smoke report is complete. The `trap` releases it on normal exit or failure; explicit release is also
fine after the report:

```bash
.kent/adapters/mobile/emulator-resource-lock.sh release "$LOCK_RESOURCE" "$LOCK_TOKEN"
```

Before any `adb install`, `adb shell`, logs, or launch command, verify `DEVICE_SERIAL` is non-empty and pass
`adb -s "$DEVICE_SERIAL"`. If `DEVICE_SERIAL` is empty, block with `blocker_reason` instead of running adb. If all running
emulators are busy, check owners with `.kent/adapters/mobile/emulator-resource-lock.sh status <emulator-serial>`. Start a
second emulator only when the task/user explicitly allows parallel device usage and a suitable AVD/host capacity is
available; use a distinct lock name for that emulator. Use a physical device only with explicit user permission and an
explicit serial.

Bind Mobile MCP to the same serial with
`.kent/adapters/mcp/mcp-call.sh mobile.device action=set
deviceId="$DEVICE_SERIAL" platform=android --allow-mutate`. Pass the same
explicit `deviceId` to every target-specific Mobile MCP call. If the bridge
cannot list or select the locked serial, block instead of switching targets.

**Tool priority (cheap → expensive):**
1. `assert_visible` / `assert_not_exists` — check element presence
2. `analyze_screen` — screen structure without screenshot
3. `get_ui` — full UI tree for navigation
4. `screenshot` — visual bugs only. Always `maxWidth: 800, maxHeight: 1400`

**Key tips:** `tap(hints: true)` saves a separate get_ui call, `wait_for_element` instead of `wait(ms)`, `get_logs(package: "com.kino.puber.stage")` for filtering.

### Before ANY Device Testing (MANDATORY)

```bash
test -n "$DEVICE_SERIAL"
if pwd | grep -q '/.kent/worktrees/'; then
  ./tools/agentw :app:assembleDevDebug
else
  ./gradlew :app:assembleDevDebug
fi
adb -s "$DEVICE_SERIAL" install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk
adb -s "$DEVICE_SERIAL" shell am force-stop com.kino.puber.stage
adb -s "$DEVICE_SERIAL" shell am start -n com.kino.puber.stage/com.kino.puber.MainActivity
```

Always install the freshly built `devDebug` APK immediately before emulator smoke tests. Another local session can
overwrite the app on the same emulator, and testing a stale APK can hide or misattribute navigation/focus regressions.
Do not use Gradle `install*` tasks for smoke tests because they may invoke adb without the selected serial.

## Testing
No tests exist yet. JUnit dependency present but unused.

## Compose Stability Config
`config/compose/compiler_config.conf`:
- `java.time.LocalDateTime` — stable
- `kotlinx.datetime.*` — stable
- `kotlin.collections.*` — stable
- `androidx.compose.ui.graphics.painter.Painter` — stable
