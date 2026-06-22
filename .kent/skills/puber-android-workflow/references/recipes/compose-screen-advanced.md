# Recipe: Compose Screen -- Advanced Patterns (TV)
> Load this recipe additionally when the step involves: focus management, D-pad navigation, tab-like content, accordion sections, grid layouts.
> Core patterns are in [compose-screen.md](compose-screen.md).

## Focus management (TV essentials)

On Android TV, focus is the primary navigation mechanism. Use `FocusRequester` and `Modifier.focusRequester()`:

```kotlin
val focusRequester = remember { FocusRequester() }

TvLazyVerticalGrid(
    columns = TvGridCells.Fixed(5),
    modifier = Modifier.focusRequester(focusRequester),
) {
    items(state.items, key = { it.id }) { item ->
        VideoItem(
            state = item,
            onClick = { onAction(CommonAction.ItemSelected(item)) },
            onFocus = { onAction(CommonAction.ItemFocused(item)) },
        )
    }
}

// Request focus on first composition
LaunchedEffect(Unit) {
    focusRequester.requestFocus()
}
```

## rememberFocusRequesterOnLaunch (UIKit utility)

Use the project's `rememberFocusRequesterOnLaunch()` for automatic initial focus on screen appear.
Uses `rememberSaveable` keyed to `LocalScreenKey` — fires once per navigation entry, not per recomposition.

```kotlin
val focusRequester = rememberFocusRequesterOnLaunch()

Column(
    modifier = Modifier
        .focusRequester(focusRequester)
        .focusGroup()
) {
    // screen content — first focusable child gets focus
}
```

## D-pad navigation between sections

Use `focusRestorer()` and `createInitialFocusRestorerModifiers()` for focus groups:

```kotlin
val (parentModifier, firstChildModifier) = createInitialFocusRestorerModifiers()

TvLazyColumn(modifier = parentModifier) {
    item {
        Text("Featured", style = MaterialTheme.typography.headlineSmall)
    }
    item {
        TvLazyRow(
            modifier = firstChildModifier,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(state.featured) { item ->
                VideoItem(state = item, onClick = { /* ... */ })
            }
        }
    }
    item {
        Text("Recent", style = MaterialTheme.typography.headlineSmall)
    }
    item {
        TvLazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(state.recent) { item ->
                VideoItem(state = item, onClick = { /* ... */ })
            }
        }
    }
}
```

## Tab-like content with AnimatedContent

TV doesn't use bottom tabs in the traditional sense. Use `AnimatedContent` for switching between content sections:

```kotlin
@Composable
internal fun TabContentSection(
    selectedTab: TabType,
    state: MyViewState.Content,
    onAction: (UIAction) -> Unit,
) {
    // Tab selector row (D-pad navigable)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 48.dp),
    ) {
        TabType.entries.forEach { tab ->
            FilterChip(
                selected = tab == selectedTab,
                onClick = { onAction(MyAction.TabSelected(tab)) },
            ) {
                Text(tab.displayName)
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    AnimatedContent(
        targetState = selectedTab,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "tab_content_animation",
    ) { tab ->
        when (tab) {
            TabType.MOVIES -> MoviesGrid(state.movies, onAction)
            TabType.SERIES -> SeriesGrid(state.series, onAction)
            TabType.BOOKMARKS -> BookmarksGrid(state.bookmarks, onAction)
        }
    }
}
```

## Details panel with focused item

Common TV pattern: grid on one side, details panel on the other that updates based on focused item:

```kotlin
@Composable
internal fun BrowseScreenContent(
    state: BrowseViewState.Content,
    onAction: (UIAction) -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        // Details panel (left side)
        Box(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight()
                .padding(48.dp),
        ) {
            state.focusedItem?.let { item ->
                VideoItemGridDetails(
                    state = item.details,
                    onAction = onAction,
                )
            }
        }

        // Grid (right side)
        TvLazyVerticalGrid(
            columns = TvGridCells.Fixed(4),
            modifier = Modifier.weight(0.6f),
            contentPadding = PaddingValues(24.dp),
        ) {
            items(state.items, key = { it.id }) { item ->
                VideoItem(
                    state = item,
                    onFocus = { onAction(CommonAction.ItemFocused(item)) },
                    onClick = { onAction(CommonAction.ItemSelected(item)) },
                )
            }
        }
    }
}
```

## Accordion (collapsible section)

```kotlin
Column(modifier = Modifier.animateContentSize()) {
    val (expanded, setExpanded) = rememberSaveable("section", state.title) {
        mutableStateOf(state.isExpanded)
    }

    Surface(
        onClick = { if (state.isCollapsable) setExpanded(!expanded) },
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = state.title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            if (state.isCollapsable) {
                val rotation by animateFloatAsState(if (expanded) 180f else 0f)
                Icon(
                    modifier = Modifier.rotate(rotation),
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                )
            }
        }
    }

    if (expanded) {
        state.items.forEach { item -> ItemRow(item) }
    }
}
```

## Search pattern for TV

```kotlin
@Composable
internal fun SearchScreenContent(
    state: SearchViewState,
    onAction: (UIAction) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(48.dp)) {
        // Search input field (TV-friendly)
        var query by remember { mutableStateOf("") }
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search movies and series...") },
            singleLine = true,
        )

        // Debounced search dispatch
        LaunchedEffect(Unit) {
            snapshotFlow { query }
                .debounce { if (it.isBlank()) 0L else 300L }
                .distinctUntilChanged()
                .collect { onAction(CommonAction.TextChanged(it, "search")) }
        }

        Spacer(Modifier.height(24.dp))

        // Results
        when (state) {
            is SearchViewState.QueryEmpty -> { /* empty illustration */ }
            is SearchViewState.Loading -> FullScreenProgressIndicator()
            is SearchViewState.Content -> SearchResultsGrid(state.items, onAction)
            is SearchViewState.Error -> ErrorScreen(state.message, onAction)
        }
    }
}
```

## Placeholder loading state (skeleton)

Use the `compose-placeholder-material3` library for loading skeletons:

```kotlin
@Composable
private fun VideoItemPlaceholder() {
    Card(
        modifier = Modifier
            .size(width = 160.dp, height = 220.dp)
            .placeholder(
                visible = true,
                highlight = PlaceholderHighlight.shimmer(),
            ),
    ) { }
}

// In grid
if (state is MyViewState.Loading) {
    items(10) { VideoItemPlaceholder() }
}
```

## BottomSheetScreenContainer (TV-adapted)

For picker/chooser screens shown via `router.showOver()`, wrap content in `BottomSheetScreenContainer`.
It auto-requests focus via `rememberFocusRequesterOnLaunch()` + `focusGroup()`, and renders
content anchored to bottom with rounded top corners on surface background.

Back button dismissal is handled automatically by `FlowComponent`'s `BackHandler`.

```kotlin
@Composable
override fun Content() = DIScope(scopeName = key, moduleFactory = ::buildModule) {
    val viewModel = koinViewModel<MyChooserVM>()
    val state by viewModel.collectViewState()
    val message by viewModel.collectMessage()

    BottomSheetScreenContainer {
        Box {
            MyChooserContent(
                state = state,
                onOptionSelected = { viewModel.onAction(CommonAction.ItemSelected(it)) },
            )
            ScaffoldMessage(message = message, onAction = viewModel::onAction)
        }
    }
}
```

## Manual focus highlight (non-TV-Material components)

For plain Material3 components that need D-pad focus visual feedback, use
`MutableInteractionSource` + `collectIsFocusedAsState()`:

```kotlin
val interactionSource = remember { MutableInteractionSource() }
val isFocused by interactionSource.collectIsFocusedAsState()

Row(
    modifier = Modifier
        .focusable(interactionSource = interactionSource)
        .background(
            if (isFocused) Color(MaterialTheme.colorScheme.primary.value).copy(alpha = 0.2f)
            else Color.Transparent
        )
        .clickable(interactionSource = interactionSource, indication = null) { ... }
)
```

To exclude an element from D-pad traversal: `.focusable(false)`.

## Anti-patterns for TV
- **No pull-to-refresh** -- TV has no swipe gestures. Use explicit "Refresh" button or auto-reload.
- **No collapsing toolbar** -- TV uses fixed headers/sidebars.
- **No bottom sheets for primary navigation** -- Use full-screen overlays or side panels. For pickers use `BottomSheetScreenContainer`.
- **No touch ripple effects** -- Use focus highlight via `Surface` border/scale.
- **No small touch targets** -- All interactive elements must be large enough for D-pad selection.
- **No click-outside-to-dismiss** -- TV has no touch; use back button (handled by `BackHandler` in `FlowComponent`).
- **No swipe/drag gestures** -- D-pad only. Scrolling is automatic via focused item in `LazyColumn`/`LazyRow`.
