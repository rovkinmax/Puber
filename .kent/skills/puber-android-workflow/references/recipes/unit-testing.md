# Recipe: Unit Testing

How to write unit tests for ViewModels, Interactors, and UIMappers in Puber.

Note: No tests exist yet in the project. This recipe guides how to add them.

## File location
```
app/src/test/kotlin/com/kino/puber/ui/feature/<screenName>/vm/<ScreenName>VMTest.kt
app/src/test/kotlin/com/kino/puber/domain/interactor/<InteractorName>Test.kt
app/src/test/kotlin/com/kino/puber/ui/feature/<screenName>/model/<MapperName>Test.kt
```

## Dependencies

Add to `app/build.gradle.kts` if not already present:
```kotlin
testImplementation(libs.junit)
testImplementation(libs.coroutines.test)
testImplementation(libs.mockk)
```

## Test naming convention

```kotlin
fun methodName_expectedResult_whenCondition()

// Examples:
fun loadData_emitsContent_whenApiReturnsItems()
fun loadData_emitsError_whenApiReturnsFailure()
fun onAction_navigatesToDetails_whenItemSelected()
```

## UIMapper tests (simplest, no coroutines)

Test UIMappers with real instances -- they are pure functions:

```kotlin
class MyUIMapperTest {

    private val resources = FakeResourceProvider()
    private val mapper = MyUIMapper(resources)

    @Test
    fun mapToContent_mapsFieldsCorrectly() {
        val item = Item(
            id = 1,
            title = "Test Movie",
            type = "movie",
            year = 2024,
        )

        val result = mapper.mapItem(item)

        assertEquals("Test Movie", result.title)
        assertEquals("2024", result.subtitle)
    }

    @Test
    fun mapToContent_handlesNullFields() {
        val item = Item(id = 1, title = null, type = null, year = null)

        val result = mapper.mapItem(item)

        assertEquals("", result.title)
    }
}
```

## Interactor tests (suspend functions with Result)

Use `runTest` + mock `KinoPubApiClient`:

```kotlin
class MyInteractorTest {

    private val apiClient = mockk<KinoPubApiClient>()
    private val interactor = MyInteractor(apiClient)

    @Test
    fun getItems_returnsMappedItems() = runTest {
        coEvery {
            apiClient.getItems(page = 1)
        } returns Result.success(ItemsResponse(items = listOf(testItem1, testItem2)))

        val result = interactor.getItems()

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()?.size)
    }

    @Test
    fun getItems_returnsFailure_whenApiThrows() = runTest {
        coEvery {
            apiClient.getItems(any())
        } returns Result.failure(IOException("Network error"))

        val result = interactor.getItems()

        assertTrue(result.isFailure)
    }

    @Test
    fun getItemDetails_callsApiWithCorrectId() = runTest {
        coEvery { apiClient.getItemDetails(42) } returns Result.success(testItemDetails)

        interactor.getItemDetails(42)

        coVerify { apiClient.getItemDetails(42) }
    }
}
```

## ViewModel tests (PuberVM state + actions)

Use `TestDispatcher` for state flow testing:

```kotlin
class MyVMTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val interactor = mockk<MyInteractor>()
    private val mapper = MyUIMapper(FakeResourceProvider())
    private val router = mockk<AppRouter>(relaxed = true)

    private fun createVM() = MyVM(
        router = router,
        interactor = interactor,
        mapper = mapper,
    )

    @Test
    fun onStart_loadsDataAndEmitsContent() = runTest {
        coEvery { interactor.getData() } returns Result.success(testData)

        val vm = createVM()
        vm.onStart()
        advanceUntilIdle()

        val state = vm.stateValue
        assertIs<MyViewState.Content>(state)
        assertEquals(2, state.items.size)
    }

    @Test
    fun onStart_emitsError_whenApiReturnsFailure() = runTest {
        coEvery { interactor.getData() } returns Result.failure(IOException())

        val vm = createVM()
        vm.onStart()
        advanceUntilIdle()

        assertIs<MyViewState.Error>(vm.stateValue)
    }

    @Test
    fun onRetryClicked_reloadsData() = runTest {
        coEvery { interactor.getData() } returns Result.failure(IOException())

        val vm = createVM()
        vm.onStart()
        advanceUntilIdle()

        coEvery { interactor.getData() } returns Result.success(testData)
        vm.onAction(CommonAction.RetryClicked)
        advanceUntilIdle()

        assertIs<MyViewState.Content>(vm.stateValue)
    }

    @Test
    fun onItemSelected_navigatesToDetails() = runTest {
        coEvery { interactor.getData() } returns Result.success(testData)

        val vm = createVM()
        vm.onStart()
        advanceUntilIdle()

        val item = (vm.stateValue as MyViewState.Content).items.first()
        vm.onAction(CommonAction.ItemSelected(item))

        verify {
            router.navigateTo(match<PuberScreen> {
                it is DetailsScreen && it.itemId == item.id
            })
        }
    }
}
```

## MainDispatcherRule

Standard test rule for replacing Main dispatcher:

```kotlin
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
```

Place in `app/src/test/kotlin/com/kino/puber/util/MainDispatcherRule.kt`.

## FakeResourceProvider

Create a minimal fake for testing UIMappers:

```kotlin
class FakeResourceProvider : ResourceProvider {
    override fun getString(resId: Int): String = "string_$resId"
    override fun getString(resId: Int, vararg args: Any): String =
        "string_${resId}_${args.joinToString("_")}"
}
```

Place in `app/src/test/kotlin/com/kino/puber/util/FakeResourceProvider.kt`.

## Mocks vs Fakes

| Use | When |
|-----|------|
| `mockk<T>()` | `KinoPubApiClient`, `AppRouter`, `ErrorHandler` |
| | (behavior not important, verify calls) |
| Fake impl | `ResourceProvider` (need real behavior for mapper tests) |
| Real class | UIMappers, Validators, data classes |
| | (pure logic, no dependencies) |

**Rule:** Use real classes when possible. Mock only at boundaries
(API, navigation, system services).

## Common test data

Keep test fixtures near the test file or in a shared `TestData.kt`:

```kotlin
// In test file or nearby TestData.kt
private val testItem1 = Item(
    id = 1,
    title = "Breaking Bad",
    type = "serial",
    year = 2008,
)
private val testItem2 = Item(
    id = 2,
    title = "The Matrix",
    type = "movie",
    year = 1999,
)
private val testData = listOf(testItem1, testItem2)
```

## Testing TypedTtlCache interactions

```kotlin
@Test
fun getItemDetails_returnsCached_whenNotExpired() = runTest {
    val cache = TypedTtlCacheImpl<Int, Item>(ttlMs = 60_000L)
    val interactor = DetailsInteractor(apiClient, cache)

    coEvery { apiClient.getItemDetails(1) } returns Result.success(testItem1)

    // First call -- hits API
    interactor.getItemDetails(1)
    // Second call -- should use cache
    interactor.getItemDetails(1)

    coVerify(exactly = 1) { apiClient.getItemDetails(1) }
}
```

## Test quality rules

- **Regression tests must fail without the fix.** If your test passes on both old and new code, the test data does not reproduce the bug — redesign it.
- **Never assert on locale-dependent strings.** Formatted dates (`"21 Nov 2024"`), currencies (`"$1,234.56"`), and numbers depend on `Locale.getDefault()`. Assert on structural properties instead: count, ordering, uniqueness, field values.
- **Prefer explicit expected values over count-only.** `assertEquals(listOf("movie", "serial"), result.map { it.type })` beats `assertEquals(2, items.size)` — on failure, the developer sees exactly what went wrong.
- **Mapper edge cases (mandatory):** empty input, single item, null/missing fields, invalid format, verify item IDs/content not just count, structure/order for section-based lists.

## Checklist
- [ ] Test class name: `<ClassName>Test`
- [ ] `MainDispatcherRule` for VM tests
- [ ] `runTest` for coroutine tests
- [ ] Happy path + error path covered
- [ ] Null/empty edge cases for API responses
- [ ] Actions that trigger navigation verified with `verify { router.navigateTo(...) }`
- [ ] `coEvery`/`coVerify` for suspend mocks
- [ ] No hardcoded strings from resources (use `FakeResourceProvider`)
- [ ] API returns `Result<T>`, test both `Result.success()` and `Result.failure()`
- [ ] Test data uses realistic content (movie/series names, years)
- [ ] Regression tests fail without the fix
- [ ] No locale-dependent string assertions
- [ ] Explicit expected values, not count-only
