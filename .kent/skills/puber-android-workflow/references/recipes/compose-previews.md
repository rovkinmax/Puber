# Recipe: Compose Previews
> Related: [compose-screen.md](compose-screen.md)

How to add Compose previews for screens. No Paparazzi screenshot tests in this project.

## Preview Provider pattern

Each screen component should have a corresponding `PreviewParameterProvider`:

### File location
```
ui/feature/<screenName>/component/preview/<ScreenName>PreviewProvider.kt
```

### Provider class
```kotlin
internal class MyScreenPreviewProvider : PreviewParameterProvider<MyViewState> {
    override val values: Sequence<MyViewState> = sequenceOf(
        MyViewState.Loading,
        buildContent(),
        MyViewState.Empty,
        MyViewState.Error(message = "Something went wrong"),
    )
}

private fun buildContent(): MyViewState.Content {
    return MyViewState.Content(
        items = listOf(
            VideoItemUIState(
                id = 1,
                title = "Breaking Bad",
                posterUrl = "",
                year = "2008",
            ),
            VideoItemUIState(
                id = 2,
                title = "The Matrix",
                posterUrl = "",
                year = "1999",
            ),
        ),
    )
}
```

### Preview function (in the ScreenContent file)
```kotlin
@Preview(showBackground = true, device = Devices.TV_1080p)
@Composable
private fun MyScreenContentPreview(
    @PreviewParameter(MyScreenPreviewProvider::class) state: MyViewState,
) = PuberTheme {
    MyScreenContent(state = state, onAction = {})
}
```

## State coverage guidelines

For sealed class ViewState:
- **Always include:** Loading, Error, all Content variants
- **Content variants:** empty list, populated list, focused item with details
- **TV-specific:** show focus states if applicable

For data class ViewState (like forms):
- **New mode:** default state (empty fields)
- **Edit mode:** filled fields, `isEditing = true`
- **Saving state:** `isSaving = true`
- **With errors:** `fieldErrors` populated

For search ViewState:
- **QueryEmpty:** initial state
- **Loading:** searching in progress
- **Content with results:** populated grid
- **Error:** network/server error

## Sample data rules

- Use realistic data (movie/series names, years)
- Titles: "Breaking Bad", "The Matrix", "Interstellar"
- Years: 2008, 1999, 2014
- Types: "movie", "serial", "documentary"
- Keep at least 2 items in list content states for visual variety
- Use empty string `""` for image URLs in previews (Coil handles gracefully)

## TV-specific preview settings

Always use TV device in `@Preview`:

```kotlin
@Preview(
    showBackground = true,
    device = Devices.TV_1080p,
    widthDp = 1920,
    heightDp = 1080,
)
```

## Preview for details screen

```kotlin
internal class DetailsPreviewProvider : PreviewParameterProvider<DetailsScreenState> {
    override val values: Sequence<DetailsScreenState> = sequenceOf(
        DetailsScreenState.Loading,
        DetailsScreenState.Content(
            details = VideoDetailsUIState(
                title = "Breaking Bad",
                year = "2008",
                duration = "45 min",
                genres = "Drama, Crime, Thriller",
                description = "A chemistry teacher diagnosed with terminal lung cancer...",
                ratings = listOf(
                    RatingUIState.IMDB("9.5"),
                    RatingUIState.KP("8.9"),
                ),
            ),
        ),
        DetailsScreenState.Error("Failed to load details"),
    )
}
```

## Preview for form screen (data class ViewState)

```kotlin
internal class EditItemPreviewProvider : PreviewParameterProvider<EditItemViewState> {
    override val values = sequenceOf(
        EditItemViewState.Loading,
        EditItemViewState.Content(
            draft = EditItemDraft(title = "", description = ""),
        ),
        EditItemViewState.Content(
            draft = EditItemDraft(title = "My Playlist", description = "Best movies"),
            isEditing = true,
        ),
        EditItemViewState.Content(
            draft = EditItemDraft(title = "My Playlist"),
            isSaving = true,
        ),
    )
}
```

## Important notes

- **Always wrap in `PuberTheme`** -- never use bare `MaterialTheme` in previews
- **Use `Devices.TV_1080p`** -- previews should reflect TV layout, not phone
- **No Paparazzi** -- this project does not use screenshot testing
- **No `@TestParameter`** -- use standard `@PreviewParameter` only
- **`onAction = {}`** -- pass empty lambda for preview, no need for interaction

## Checklist
- [ ] `PreviewParameterProvider` in `component/preview/` subdirectory
- [ ] All ViewState variants covered (Loading, Content variants, Error, Empty)
- [ ] `@Preview` with `@PreviewParameter` in ScreenContent file
- [ ] Wrapped in `PuberTheme`
- [ ] `device = Devices.TV_1080p` in `@Preview`
- [ ] Realistic sample data (movie/series names)
- [ ] Empty string for image URLs (not null)
