# Recipe: UI Components

Available reusable components and how to use them.

## Video components (core/ui/uikit/component/)

### VideoItem — poster card

```kotlin
VideoItem(
    state = VideoItemUIState(
        id = item.id,
        title = item.title,
        posterUrl = item.posters?.medium,
        type = item.type,
    ),
    onClick = { onAction(CommonAction.ItemSelected(it)) },
    onFocus = { onAction(CommonAction.ItemFocused(it)) },
)
```

### VideoGrid — lazy grid of grouped videos

```kotlin
VideoGrid(
    state = VideoGridUIState(
        groups = listOf(
            VideoGridGroupUIState(
                title = "Movies",
                items = movieItems,
            ),
        ),
    ),
    onAction = onAction,
)
```

Renders as a lazy column of lazy rows, grouped by type/category.

### VideoItemGridDetails — details panel

```kotlin
VideoItemGridDetails(
    state = VideoDetailsUIState(
        title = item.title,
        year = "2024",
        genres = "Action, Drama",
        ratings = listOf(RatingUIState.IMDB(8.5), RatingUIState.KP(7.9)),
        posterUrl = item.posters?.big,
        description = item.plot,
        duration = "2h 15m",
        seasons = "4 seasons",
        country = "USA",
    ),
)
```

### FullScreenProgressIndicator — loading spinner

```kotlin
// Full-screen centered loading indicator
FullScreenProgressIndicator()
```

Use for initial loading states. For skeleton loading, use the `placeholder` modifier instead.

## Rating display

```kotlin
// Sealed class for different rating sources
sealed class RatingUIState {
    data class IMDB(val rating: Double) : RatingUIState()
    data class KP(val rating: Double) : RatingUIState()
    data class PUB(val rating: Double) : RatingUIState()
}
```

## Placeholder / skeleton loading

```kotlin
import com.google.accompanist.placeholder.material3.placeholder

Text(
    modifier = Modifier.placeholder(visible = isLoading),
    text = title,
    style = MaterialTheme.typography.bodyMedium,
)

Box(
    modifier = Modifier
        .size(120.dp, 180.dp)
        .placeholder(visible = isLoading),
)
```

## TV Material3 components

Primary UI toolkit is `androidx.tv.material3`:

```kotlin
import androidx.tv.material3.Surface
import androidx.tv.material3.Card
import androidx.tv.material3.Text
import androidx.tv.material3.MaterialTheme

// Surface as root
Surface(modifier = Modifier.fillMaxSize()) {
    // content
}

// Card
Card(
    onClick = { onAction(MyAction.CardClicked) },
    modifier = Modifier.size(150.dp, 225.dp),
) {
    AsyncImage(
        model = posterUrl,
        contentDescription = title,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
}

// Text with theme typography
Text(
    text = title,
    style = MaterialTheme.typography.titleMedium,
    color = MaterialTheme.colorScheme.onSurface,
)
```

## Theme

```kotlin
// PuberTheme wraps both tv.material3 and material3
PuberTheme {
    // content
}

// Access theme values
MaterialTheme.colorScheme.primary
MaterialTheme.typography.bodyMedium
```

## Image loading (Coil 3)

```kotlin
import coil3.compose.AsyncImage

AsyncImage(
    model = ImageRequest.Builder(LocalContext.current)
        .data(posterUrl)
        .crossfade(true)
        .build(),
    contentDescription = title,
    contentScale = ContentScale.Crop,
    modifier = Modifier.size(120.dp, 180.dp),
)
```

## Material Icons

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*

Icon(Icons.Default.Favorite, contentDescription = "Favorite")
Icon(Icons.Default.Star, contentDescription = "Rating")
Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Back")
```

## Modifier utilities

```kotlin
// Conditional modifier
Modifier.ifElse(
    condition = isSelected,
    ifTrue = { border(2.dp, Color.White) },
    ifFalse = { /* no border */ },
)

// Focus on launch
val focusRequester = remember { FocusRequester() }
Modifier.focusRequester(focusRequester)
// FocusOnLaunchRequester for auto-focus

// Placeholder for skeleton loading
Modifier.placeholder(visible = isLoading)
```

## Notes
- For buttons — use TV Material3 `Button` or `Card` with click handlers
- For cards — use `androidx.tv.material3.Card` or `Surface`
- For empty states — build with `Column` + `Text` + `Icon`
- For info cards — use `Surface` with custom styling
- TV apps typically don't use top app bars
- **No `PullRefreshIndicator`** — TV apps use D-pad navigation, not touch gestures
- **No `LinePlaceHolder`** — use `Modifier.placeholder()` from compose-placeholder-material3

## Checklist
- [ ] TV Material3 components (`androidx.tv.material3.*`)
- [ ] `PuberTheme` wrapping content
- [ ] Coil `AsyncImage` for images with `crossfade(true)` and fixed size
- [ ] `VideoItem` / `VideoGrid` / `VideoItemGridDetails` for video content
- [ ] `FullScreenProgressIndicator` for loading states
- [ ] `Modifier.placeholder()` for skeleton loading
- [ ] Focus handling with `FocusRequester` for TV navigation
