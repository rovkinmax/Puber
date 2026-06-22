# Recipe: Compose Performance

Checklist and patterns for avoiding recomposition issues in Jetpack Compose screens.

## When to apply

Run this checklist after implementing any screen. Performance issues are easier to prevent than to fix later.

## Stability annotations

Mark ViewState and UI models as stable for Compose compiler:

```kotlin
// Sealed class — @Immutable on the sealed class
@Immutable
internal sealed class MyViewState {
    data object Loading : MyViewState()
    data object Empty : MyViewState()
    data class Error(val message: String) : MyViewState()
    data class Content(
        val items: List<VideoItemUIState>,
        val title: String,
    ) : MyViewState()
}

// UI model — always @Immutable
@Immutable
data class VideoItemUIState(
    val id: Long,
    val title: String,
    val posterUrl: String?,
)
```

**Rules:**
- `@Immutable` on sealed class ViewState
- `@Immutable` on all UI model data classes
- `@Stable` on interfaces that have observable state

**Note:** `kotlin.collections.*` is marked stable in `config/compose/compiler_config.conf`. `List`, `Map`, `Set` are stable in this project.

## Unstable types to watch

| Type | Status in project | Notes |
|------|-------------------|-------|
| `List<T>`, `Map<K,V>`, `Set<T>` | Stable (compiler config) | No extra fix needed |
| Lambda `() -> Unit` | Unstable | `remember(vm) { vm::onAction }` |
| External library classes | May be unstable | Wrap or add to compiler config |
| `MutableState` | Triggers recomposition | Expected — use correctly |

## Lambda stability

```kotlin
// WRONG — new lambda instance each recomposition
MyScreenContent(
    state = state,
    onAction = { vm.onAction(it) },  // unstable!
)

// RIGHT — stable reference via remember
val onAction: (UIAction) -> Unit = remember(vm) { vm::onAction }
MyScreenContent(state = state, onAction = onAction)
```

## Keys in lazy lists

Always provide stable keys for list items:

```kotlin
TvLazyRow {
    items(
        items = state.items,
        key = { it.id },  // REQUIRED — stable unique key
    ) { item ->
        VideoItem(state = item, onClick = { onAction(CommonAction.ItemSelected(it)) })
    }
}
```

## derivedStateOf for computed values

When UI depends on derived state (e.g., "show button only if list is scrolled"):

```kotlin
// WRONG — recomposes on every scroll pixel
val showButton = listState.firstVisibleItemIndex > 0

// RIGHT — recomposes only when value changes
val showButton by remember {
    derivedStateOf { listState.firstVisibleItemIndex > 0 }
}
```

## TV-specific: Focus handling

```kotlin
val focusRequester = remember { FocusRequester() }

Card(
    onClick = { onAction(MyAction.ItemClicked(item.id)) },
    modifier = Modifier
        .focusRequester(focusRequester)
        .onFocusChanged { focusState ->
            if (focusState.isFocused) {
                onAction(CommonAction.ItemFocused(item))
            }
        },
) {
    // content
}

// Auto-focus first item on launch
LaunchedEffect(Unit) {
    focusRequester.requestFocus()
}
```

Use `FocusOnLaunchRequester` utility for auto-focus patterns.

## State switching in when blocks

`when(state)` inside Scaffold is fine — it's the standard project pattern. For smoother transitions, wrap in `AnimatedContent`:

```kotlin
// Standard — instant state switch (sufficient for most screens)
Scaffold(...) { padding ->
    when (val state = viewState) {
        is Loading -> FullScreenProgressIndicator()
        is Content -> ContentBody(state, onAction, Modifier.padding(padding))
        is Error -> ErrorContent(...)
    }
}

// Optimized — animated state transitions (for polished UX)
AnimatedContent(
    targetState = state,
    contentKey = { it.javaClass },  // recompose only on type change
    transitionSpec = {
        fadeIn(tween(220, delayMillis = 90))
            .togetherWith(fadeOut(tween(90)))
    },
) { targetState ->
    when (targetState) {
        is Loading -> FullScreenProgressIndicator()
        is Content -> ContentBody(targetState, onAction)
        is Error -> ErrorContent(...)
    }
}
```

## Modifier best practices

```kotlin
// WRONG — reads animated value during composition
Box(modifier = Modifier.offset(
    x = animatedX.value.dp,
    y = 0.dp,
))

// RIGHT — lambda defers read to layout phase
Box(modifier = Modifier.offset {
    IntOffset(x = animatedX.value.roundToInt(), y = 0)
})

// Same principle for drawing:
Box(modifier = Modifier.graphicsLayer {
    alpha = animatedAlpha.value
})
```

**Rule:** Use lambda-based modifiers (`offset {}`, `graphicsLayer {}`, `drawBehind {}`) when reading animated or frequently-changing state.

## Scoping state reads to children

Pass only needed fields to child composables:

```kotlin
// BETTER — Header skips if title unchanged
Column {
    Header(title = state.title)
    VideoGrid(items = state.items)
}

// WORSE — Header recomposes on ANY state field change
Column {
    Header(state = state)
    VideoGrid(state = state)
}
```

## Image loading (Coil)

```kotlin
AsyncImage(
    model = ImageRequest.Builder(LocalContext.current)
        .data(url)
        .crossfade(true)
        .build(),
    contentDescription = description,
    contentScale = ContentScale.Crop,
    modifier = Modifier.size(120.dp, 180.dp),  // Fixed size!
)
```

**Always set:**
- `contentScale` — prevents layout jumps
- `crossfade(true)` — smooth loading transition
- Fixed `Modifier.size()` — prevents layout recalculation

## Compose compiler stability config

`config/compose/compiler_config.conf` marks these as stable:
- `java.time.LocalDateTime`
- `kotlinx.datetime.*`
- `kotlin.collections.*`
- `androidx.compose.ui.graphics.painter.Painter`

## Post-implementation checklist
- [ ] `@Immutable` on sealed class ViewState
- [ ] `@Immutable` on all UI model data classes
- [ ] `key = { it.id }` in all `items()` calls
- [ ] `remember(vm) { vm::onAction }` for action lambdas
- [ ] `derivedStateOf` for scroll-dependent UI
- [ ] Fixed image sizes (no wrap_content for network images)
- [ ] Lazy lists: items extracted to separate composables
- [ ] Focus handling with `FocusRequester` for TV D-pad navigation
- [ ] Lambda-based modifiers for animated values
