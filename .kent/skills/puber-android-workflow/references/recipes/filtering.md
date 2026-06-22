# Recipe: Filtering
> Related: [viewmodel.md](viewmodel.md), [paging-list.md](paging-list.md)

How to implement filters and search in list screens for Android TV.

## Filter data class

```kotlin
internal data class MyFilter(
    val searchQuery: String = "",
    val type: ItemType? = null,
    val genre: String? = null,
    val year: Int? = null,
    val sortBy: SortType = SortType.UPDATED,
) {
    val isEmpty: Boolean
        get() = searchQuery.isBlank() && type == null &&
                genre == null && year == null && sortBy == SortType.UPDATED
}
```

## Filter enum for tag dispatch

```kotlin
internal enum class MyFilterTag {
    Type, Genre, Year, Sort
}
```

## Building filter chips (in UIMapper)

Build filter chip state in the UIMapper using `ResourceProvider`, not in composables:

```kotlin
internal class MyUIMapper(private val resources: ResourceProvider) {

    fun buildFilters(filter: MyFilter): List<FilterChipState> {
        return buildList {
            add(buildTypeChip(filter.type))
            add(buildGenreChip(filter.genre))
            add(buildYearChip(filter.year))
            add(buildSortChip(filter.sortBy))
        }.sortedBy { it.isSelected.not() }  // Selected chips appear first
    }

    private fun buildTypeChip(type: ItemType?): FilterChipState {
        val isSelected = type != null
        return FilterChipState(
            label = if (isSelected) {
                resources.getString(R.string.filter_type_selected, type!!.displayName)
            } else {
                resources.getString(R.string.filter_type)
            },
            isSelected = isSelected,
            tag = MyFilterTag.Type,
        )
    }

    private fun buildSortChip(sortBy: SortType): FilterChipState {
        return FilterChipState(
            label = resources.getString(R.string.filter_sort, sortBy.displayName),
            isSelected = sortBy != SortType.UPDATED,
            tag = MyFilterTag.Sort,
        )
    }
}
```

## FilterChipState (simple data class)

```kotlin
internal data class FilterChipState(
    val label: String,
    val isSelected: Boolean = false,
    val tag: MyFilterTag,
)
```

## Filter chips UI for TV

Use TV Material3 `FilterChip` with focus management:

```kotlin
@Composable
internal fun FilterRow(
    filters: List<FilterChipState>,
    onAction: (UIAction) -> Unit,
) {
    TvLazyRow(
        contentPadding = PaddingValues(horizontal = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(filters, key = { it.tag }) { chip ->
            FilterChip(
                selected = chip.isSelected,
                onClick = { onAction(MyAction.FilterChipClicked(chip)) },
            ) {
                Text(chip.label)
            }
        }
    }
}
```

## Handling chip clicks in VM

```kotlin
private fun onFilterChipClicked(chip: FilterChipState) {
    if (chip.isSelected) {
        clearFilter(chip.tag)
        return
    }

    when (chip.tag) {
        MyFilterTag.Type -> cycleTypeFilter()
        MyFilterTag.Genre -> openGenrePicker()
        MyFilterTag.Year -> cycleYearFilter()
        MyFilterTag.Sort -> cycleSortFilter()
    }
}

private fun clearFilter(tag: MyFilterTag) {
    filter = when (tag) {
        MyFilterTag.Type -> filter.copy(type = null)
        MyFilterTag.Genre -> filter.copy(genre = null)
        MyFilterTag.Year -> filter.copy(year = null)
        MyFilterTag.Sort -> filter.copy(sortBy = SortType.UPDATED)
    }
    resetPaging()
}
```

## Cycle-based filter (TV-friendly, no bottom sheets needed)

For simple enum filters, cycle through values on D-pad enter instead of opening a picker:

```kotlin
private val typeOptions = listOf(null) + ItemType.entries
private var typeIndex = 0

private fun cycleTypeFilter() {
    typeIndex = (typeIndex + 1) % typeOptions.size
    filter = filter.copy(type = typeOptions[typeIndex])
    resetPaging()
}
```

## Picker via bottom sheet (for complex selection)

```kotlin
private val genrePickerRequestId = IdGenerator.generateId()

private fun openGenrePicker() {
    router.showOver(
        screen = Screens.genrePicker(
            selectedGenre = filter.genre,
            resultCode = genrePickerRequestId,
        ),
        resultCode = genrePickerRequestId,
        listener = ::dispatchGenreResult,
    )
}

@Suppress("UNCHECKED_CAST")
private fun dispatchGenreResult(result: Any?) {
    val genre = result as? String ?: return
    filter = filter.copy(genre = genre)
    resetPaging()
}
```

## Search pattern

### In VM

```kotlin
private fun onSearchQueryChanged(query: String) {
    filter = filter.copy(searchQuery = query)
    resetPaging()
}
```

### In Composable (debounced)

```kotlin
val queryState = remember { mutableStateOf("") }

LaunchedEffect(queryState) {
    snapshotFlow { queryState.value }
        .debounce { if (it.isBlank()) 0L else 300L }
        .distinctUntilChanged()
        .collect { onAction(CommonAction.TextChanged(it, "search")) }
}
```

## Full filter integration in PagingVM

```kotlin
override fun onLoadFirstPage() {
    pagingLaunch {
        val result = interactor.getItems(
            type = filter.type?.name,
            genre = filter.genre,
            year = filter.year,
            sort = filter.sortBy.apiValue,
            query = filter.searchQuery.takeIf { it.isNotBlank() },
        )
        result.onSuccess { items ->
            replace(items)
            cachedItems.clearAndAddAll(items)
        }
    }
}
```

## Checklist
- [ ] Filter data class with `isEmpty` property
- [ ] Chips sorted: selected first (`.sortedBy { it.isSelected.not() }`)
- [ ] Tag field on chips for dispatch (enum, not string)
- [ ] Filter chip state built in UIMapper with `ResourceProvider`
- [ ] TV Material3 `FilterChip` with focus support
- [ ] Cycle-based selection for simple enum filters (TV-friendly)
- [ ] Picker via `router.showOver()` for complex multi-select
- [ ] `resetPaging()` after any filter change
- [ ] Search debounced (300ms) with `snapshotFlow`
- [ ] API calls return `Result<T>`, handled with `onSuccess`/`onFailure`
