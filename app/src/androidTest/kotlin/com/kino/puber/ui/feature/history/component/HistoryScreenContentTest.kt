package com.kino.puber.ui.feature.history.component

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsPropertiesAndroid
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import com.kino.puber.core.ui.uikit.component.modifier.LocalAutoFocusOnLaunchEnabled
import com.kino.puber.core.ui.uikit.component.modifier.LocalContentFocusActive
import com.kino.puber.core.ui.uikit.component.moviesList.WATCHED_INDICATOR_TEST_TAG
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.model.CommonAction
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.domain.interactor.history.HistoryRowKey
import com.kino.puber.domain.interactor.history.HistorySemanticKey
import com.kino.puber.ui.feature.history.model.HistoryAction
import com.kino.puber.ui.feature.history.model.HistoryItemUIState
import com.kino.puber.ui.feature.history.model.HistoryPlaybackTarget
import com.kino.puber.ui.feature.history.model.HistoryPresentation
import com.kino.puber.ui.feature.history.model.HistoryViewState
import com.kino.puber.ui.feature.player.model.PlayerStartMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val DISCLOSURE = "Удаление влияет на историю просмотров вашего аккаунта."
private const val CONTINUE = "Продолжить просмотр"
private const val START_OVER = "Смотреть сначала"
private const val DETAILS = "Подробнее"
private const val DELETE_EXACT_MEDIA = "Удалить эту запись"
private const val CLOSE = "Закрыть"
private const val EMPTY_TITLE = "История просмотров пуста"
private const val REDUNDANT_CONTENT_TITLE = "История"

internal class HistoryScreenContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun finalItemDeletionFocusesRefreshAction() {
        val historyItem = historyItem()
        val rowKey = historyItem.rowKey
        val state = mutableStateOf<HistoryViewState>(
            HistoryViewState.Content(
                items = listOf(historyItem),
                focusKey = rowKey,
            ),
        )

        composeRule.setContent {
            PuberTheme {
                CompositionLocalProvider(LocalAutoFocusOnLaunchEnabled provides false) {
                    HistoryScreenContent(
                        state = state.value,
                        presentation = HistoryPresentation.TopTabs,
                        onAction = {},
                    )
                }
            }
        }

        composeRule
            .historyCardNode()
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused()

        composeRule.runOnIdle {
            state.value = HistoryViewState.Empty
        }

        composeRule
            .onNodeWithTag(HISTORY_REFRESH_TEST_TAG)
            .assertIsFocused()
    }

    @Test
    fun contextMenuShowsDisclosureAndOmitsBroaderDeletionActions() {
        setHistoryContent(openMenuState())

        composeRule.onNodeWithText(DISCLOSURE).assertExists()
        composeRule.onNodeWithText(CONTINUE).assertExists()
        composeRule.onNodeWithText(DETAILS).assertExists()
        composeRule.onNodeWithText(DELETE_EXACT_MEDIA).assertExists()
        composeRule.onNodeWithText(CLOSE).assertExists()
        composeRule.onNodeWithText("Удалить фильм").assertDoesNotExist()
        composeRule.onNodeWithText("Удалить сезон").assertDoesNotExist()
        composeRule.onNodeWithText("Очистить историю").assertDoesNotExist()
    }

    @Test
    fun contextMenuMapsSupportedActionsAndClose() {
        val actions = mutableListOf<UIAction>()
        setHistoryContent(openMenuState()) { actions += it }

        composeRule.onNodeWithText(CONTINUE).performClick()
        composeRule.onNodeWithText(DETAILS).performClick()
        composeRule.onNodeWithText(DELETE_EXACT_MEDIA).performClick()
        composeRule.onNodeWithText(CLOSE).performClick()

        composeRule.runOnIdle {
            assertTrue(
                actions.filterIsInstance<HistoryAction.Play>().single().startMode ==
                    PlayerStartMode.ResumeIfAvailable,
            )
            assertTrue(actions.any { it is HistoryAction.OpenDetails })
            assertTrue(actions.any { it is HistoryAction.DeleteExactMedia })
            assertTrue(actions.any { it is HistoryAction.DismissContextMenu })
        }
    }

    @Test
    fun fallbackDetailsContextMenuOmitsPlaybackActions() {
        val fallback = historyItem(playbackTarget = HistoryPlaybackTarget.Details)
        setHistoryContent(openMenuState(item = fallback))

        composeRule.onNodeWithText(CONTINUE).assertDoesNotExist()
        composeRule.onNodeWithText(START_OVER).assertDoesNotExist()
        composeRule.onNodeWithText(DETAILS).assertExists()
        composeRule.onNodeWithText(DELETE_EXACT_MEDIA).assertExists()
    }

    @Test
    fun completedRowStartOverActionCarriesExplicitPlayerMode() {
        val completed = historyItem(isWatched = true)
        val actions = mutableListOf<UIAction>()
        setHistoryContent(openMenuState(item = completed)) { actions += it }

        composeRule.onNodeWithText(START_OVER).performClick()

        composeRule.runOnIdle {
            val play = actions.filterIsInstance<HistoryAction.Play>().single()
            assertTrue(
                play ==
                    HistoryAction.Play(completed, PlayerStartMode.StartFromBeginning),
            )
            assertTrue(actions.any { it is HistoryAction.DismissContextMenu })
        }
    }

    @Test
    fun contextMenuDisablesDeleteWhileMutationIsPending() {
        setHistoryContent(openMenuState(isDeletionPending = true))

        composeRule
            .onNodeWithText(DELETE_EXACT_MEDIA, useUnmergedTree = true)
            .onParent()
            .assertIsNotEnabled()
    }

    @Test
    fun deletingCardRejectsSelectAndContextMenuInput() {
        val item = historyItem()
        val actions = mutableListOf<UIAction>()
        setHistoryContent(
            state = HistoryViewState.Content(
                items = listOf(item),
                deletingKeys = setOf(item.rowKey),
                focusKey = item.rowKey,
                isDeleteExactMediaAvailable = false,
            ),
            onAction = { actions += it },
        )
        val card = composeRule.historyCardNode()

        card.performSemanticsAction(SemanticsActions.RequestFocus)
        card.performClick()
        card.performKeyInput {
            keyDown(Key.Menu)
            keyUp(Key.Menu)
        }

        composeRule.runOnIdle {
            assertTrue(actions.none { it is CommonAction.ItemSelected<*> })
            assertTrue(actions.none { it is HistoryAction.OpenContextMenu })
        }
    }

    @Test
    fun contextMenuKeepsDeleteVisiblyUnavailableDuringRefresh() {
        setHistoryContent(openMenuState(isRefreshPending = true))

        composeRule
            .onNodeWithText(DELETE_EXACT_MEDIA, useUnmergedTree = true)
            .onParent()
            .assertIsNotEnabled()
    }

    @Test
    fun contextMenuKeepsDeleteVisiblyUnavailableDuringPagination() {
        setHistoryContent(openMenuState(isNextPagePending = true))

        composeRule
            .onNodeWithText(DELETE_EXACT_MEDIA, useUnmergedTree = true)
            .onParent()
            .assertIsNotEnabled()
    }

    @Test
    fun completedHistoryItemShowsWatchedIndicatorWhenSharedCardPreferenceIsOff() {
        val item = historyItem(
            isWatched = true,
            showWatchedIndicator = false,
        )
        setHistoryContent(
            HistoryViewState.Content(
                items = listOf(item),
                focusKey = item.rowKey,
            ),
        )

        composeRule
            .onNodeWithTag(WATCHED_INDICATOR_TEST_TAG, useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun topTabsNonEmptyHistoryDoesNotRenderContentTitle() {
        setHistoryContent(
            state = HistoryViewState.Content(
                items = listOf(historyItem()),
                isRefreshing = true,
                hasMorePages = true,
            ),
            presentation = HistoryPresentation.TopTabs,
        )

        composeRule.onNodeWithText(REDUNDANT_CONTENT_TITLE).assertDoesNotExist()
    }

    @Test
    fun sideDrawerNonEmptyHistoryRetainsContentTitle() {
        setHistoryContent(
            state = HistoryViewState.Content(
                items = listOf(historyItem()),
                isRefreshing = true,
                hasMorePages = true,
            ),
            presentation = HistoryPresentation.SideDrawer,
        )

        composeRule.onNodeWithText(REDUNDANT_CONTENT_TITLE).assertExists()
    }

    @Test
    fun retainedTopTabsHistoryDoesNotReclaimFocusWhileTabRowOwnsFocus() {
        val item = historyItem()
        setHistoryContent(
            state = HistoryViewState.Content(
                items = listOf(item),
                focusKey = item.rowKey,
            ),
            contentFocusActive = false,
        )

        composeRule.historyCardNode().assertIsNotFocused()
    }

    @Test
    fun rightAtGridEdgeKeepsFocusOnHistoryCard() {
        val items = listOf(
            historyItem(itemId = 41),
            historyItem(itemId = 42),
            historyItem(itemId = 43),
        )
        assertRightKeepsFocusOnFinalCard(items)
    }

    @Test
    fun rightAtOneCardTrailingRowKeepsFocusOnFinalHistoryCard() {
        val items = (41..44).map { itemId -> historyItem(itemId = itemId) }

        assertRightKeepsFocusOnFinalCard(items)
    }

    @Test
    fun rightAtTwoCardTrailingRowKeepsFocusOnFinalHistoryCard() {
        val items = (41..45).map { itemId -> historyItem(itemId = itemId) }

        assertRightKeepsFocusOnFinalCard(items)
    }

    @Test
    fun refreshIndicatorKeepsGridPositionStable() {
        val item = historyItem()
        val state = mutableStateOf<HistoryViewState>(
            HistoryViewState.Content(
                items = listOf(item),
                isRefreshing = false,
            ),
        )
        setHistoryContent(state = state, onAction = {})
        val idleTop = composeRule.historyCardNode().fetchSemanticsNode().boundsInRoot.top

        composeRule.runOnIdle {
            state.value = (state.value as HistoryViewState.Content).copy(
                isRefreshing = true,
            )
        }

        composeRule.onNodeWithTag(HISTORY_REFRESH_INDICATOR_TEST_TAG).assertExists()
        val refreshingTop = composeRule.historyCardNode().fetchSemanticsNode().boundsInRoot.top
        assertEquals(idleTop, refreshingTop)

        composeRule.runOnIdle {
            state.value = (state.value as HistoryViewState.Content).copy(
                isRefreshing = false,
            )
        }

        composeRule.onNodeWithTag(HISTORY_REFRESH_INDICATOR_TEST_TAG).assertDoesNotExist()
        assertEquals(
            idleTop,
            composeRule.historyCardNode().fetchSemanticsNode().boundsInRoot.top,
        )
    }

    @Test
    fun nextPageLoadingUsesThreeCardSkeletonRow() {
        setHistoryContent(
            HistoryViewState.Content(
                items = listOf(historyItem()),
                isLoadingMore = true,
                hasMorePages = true,
            ),
        )

        composeRule.onNodeWithTag(HISTORY_NEXT_PAGE_SKELETON_TEST_TAG).assertExists()
        repeat(3) { index ->
            composeRule
                .onNodeWithTag(HISTORY_NEXT_PAGE_SKELETON_CARD_TEST_TAG_PREFIX + index)
                .assertExists()
        }
    }

    @Test
    fun exhaustedPaginationExposesFinalPageMarker() {
        setHistoryContent(
            HistoryViewState.Content(
                items = listOf(historyItem()),
                hasMorePages = false,
                isLoadingMore = false,
                nextPageErrorMessage = null,
            ),
        )

        composeRule.onNodeWithTag(HISTORY_FINAL_PAGE_TEST_TAG).assertExists()
    }

    @Test
    fun finalPageMarkerIsAbsentWhileMorePagesMayExist() {
        setHistoryContent(
            HistoryViewState.Content(
                items = listOf(historyItem()),
                hasMorePages = true,
            ),
        )

        composeRule.onNodeWithTag(HISTORY_FINAL_PAGE_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun finalPageMarkerIsAbsentWhilePaginationIsLoading() {
        setHistoryContent(
            HistoryViewState.Content(
                items = listOf(historyItem()),
                hasMorePages = false,
                isLoadingMore = true,
            ),
        )

        composeRule.onNodeWithTag(HISTORY_FINAL_PAGE_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun finalPageMarkerIsAbsentWhileHistoryIsRefreshing() {
        setHistoryContent(
            HistoryViewState.Content(
                items = listOf(historyItem()),
                hasMorePages = false,
                isRefreshing = true,
            ),
        )

        composeRule.onNodeWithTag(HISTORY_FINAL_PAGE_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun finalPageMarkerIsAbsentOnPaginationError() {
        setHistoryContent(
            HistoryViewState.Content(
                items = listOf(historyItem()),
                hasMorePages = false,
                nextPageErrorMessage = "Synthetic page failure",
            ),
        )

        composeRule.onNodeWithTag(HISTORY_FINAL_PAGE_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun finalPageMarkerIsAbsentAfterRetainedContentReconciliationFailure() {
        setHistoryContent(
            HistoryViewState.Content(
                items = listOf(historyItem()),
                hasMorePages = false,
                reloadErrorMessage = "Synthetic reconciliation failure",
            ),
        )

        composeRule.onNodeWithTag(HISTORY_FINAL_PAGE_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun historyCardTestTagIsExportedAsAnAccessibilityResourceId() {
        setHistoryContent(
            HistoryViewState.Content(
                items = listOf(historyItem()),
                hasMorePages = true,
            ),
        )

        val resourceIdExport = SemanticsMatcher.expectValue(
            SemanticsPropertiesAndroid.TestTagsAsResourceId,
            true,
        )
        composeRule
            .historyCardNode()
            .assert(hasAnyAncestor(resourceIdExport))
    }

    @Test
    fun exportedHistoryCardTagsDoNotContainRawMediaIdentifiers() {
        val movie = historyItem(
            itemId = 123456789,
            deletionMediaId = 234567891,
            playbackTarget = HistoryPlaybackTarget.Movie(videoNumber = 345678912),
        )
        val episode = historyItem(
            itemId = 456789123,
            deletionMediaId = 567891234,
            playbackTarget = HistoryPlaybackTarget.Episode(
                seasonNumber = 678912345,
                episodeNumber = 789123456,
            ),
        )
        val deletionOnly = historyItem(
            itemId = 891234567,
            deletionMediaId = 912345678,
            playbackTarget = HistoryPlaybackTarget.Details,
        )
        setHistoryContent(
            HistoryViewState.Content(
                items = listOf(movie, episode, deletionOnly),
                hasMorePages = true,
            ),
        )

        val tags = composeRule
            .onAllNodes(historyCardTagMatcher, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { node -> node.config[SemanticsProperties.TestTag] }
        val rawIdentifiers = listOf(
            movie.itemId,
            movie.deletionMediaId,
            movie.videoNumber,
            episode.itemId,
            episode.deletionMediaId,
            episode.seasonNumber,
            episode.episodeNumber,
            deletionOnly.itemId,
            deletionOnly.deletionMediaId,
        ).filterNotNull()

        assertEquals(3, tags.toSet().size)
        tags.forEach { tag ->
            assertTrue(tag.matches(Regex("""history_card_[a-p]{32}""")))
            assertFalse(rawIdentifiers.any { identifier -> identifier.toString() in tag })
        }
    }

    @Test
    fun exportedHistoryCardTagRemainsStableAcrossRetainedStateUpdates() {
        val item = historyItem()
        val state = mutableStateOf<HistoryViewState>(
            HistoryViewState.Content(
                items = listOf(item),
                hasMorePages = true,
            ),
        )
        setHistoryContent(state = state, onAction = {})
        val initialTag = composeRule.historyCardTestTag()

        composeRule.runOnIdle {
            state.value = (state.value as HistoryViewState.Content).copy(
                isLoadingMore = true,
            )
        }

        assertEquals(initialTag, composeRule.historyCardTestTag())
    }

    @Test
    fun emptyRetainedContentDuringReconciliationDoesNotPublishEmptyCopy() {
        setHistoryContent(
            HistoryViewState.Content(
                items = emptyList(),
                isRefreshing = true,
                isDeleteExactMediaAvailable = false,
            ),
        )

        composeRule.onNodeWithText(EMPTY_TITLE).assertDoesNotExist()
    }

    @Test
    fun emptyRetainedContentAfterReconciliationFailureShowsRetryableError() {
        val actions = mutableListOf<UIAction>()
        setHistoryContent(
            state = HistoryViewState.Content(
                items = emptyList(),
                reloadErrorMessage = "Synthetic reconciliation failure",
                isDeleteExactMediaAvailable = false,
            ),
            onAction = { actions += it },
        )

        composeRule.onNodeWithText(EMPTY_TITLE).assertDoesNotExist()
        composeRule.onNodeWithText("Synthetic reconciliation failure").assertExists()
        composeRule.onNodeWithText("Повторить").performClick()
        composeRule.runOnIdle {
            assertTrue(actions.single() == HistoryAction.RetryReconciliation)
        }
    }

    @Test
    fun duplicateOnlyPageCompletionRequestsContinuationWithoutLosingCardFocus() {
        val item = historyItem()
        val state = mutableStateOf<HistoryViewState>(
            HistoryViewState.Content(
                items = listOf(item),
                focusKey = item.rowKey,
                hasMorePages = true,
                isLoadingMore = true,
            ),
        )
        val actions = mutableListOf<UIAction>()
        setHistoryContent(state = state, onAction = { actions += it })
        composeRule
            .historyCardNode()
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused()
        composeRule.runOnIdle {
            state.value = (state.value as HistoryViewState.Content).copy(
                isLoadingMore = false,
                pageAttemptRevision = 1L,
            )
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            actions.count { it == CommonAction.LoadMore } == 1
        }

        composeRule.runOnIdle {
            state.value = (state.value as HistoryViewState.Content).copy(
                isLoadingMore = true,
            )
        }
        composeRule.runOnIdle {
            state.value = (state.value as HistoryViewState.Content).copy(
                isLoadingMore = false,
                pageAttemptRevision = 2L,
            )
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            actions.count { it == CommonAction.LoadMore } == 2
        }
        composeRule
            .historyCardNode()
            .assertIsFocused()
    }

    @Test
    fun nextPageFailureWaitsForExplicitRetryAndKeepsCardFocus() {
        val item = historyItem()
        val state = mutableStateOf<HistoryViewState>(
            HistoryViewState.Content(
                items = listOf(item),
                focusKey = item.rowKey,
                hasMorePages = true,
                isLoadingMore = true,
            ),
        )
        val actions = mutableListOf<UIAction>()
        setHistoryContent(state = state, onAction = { actions += it })
        composeRule
            .historyCardNode()
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused()

        composeRule.runOnIdle {
            state.value = (state.value as HistoryViewState.Content).copy(
                isLoadingMore = false,
                pageAttemptRevision = 1L,
                nextPageErrorMessage = "Synthetic page failure",
            )
        }

        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertTrue(actions.none { it == CommonAction.LoadMore })
        }
        composeRule
            .historyCardNode()
            .assertIsFocused()
        composeRule.onNodeWithText("Повторить").performClick()
        composeRule.runOnIdle {
            assertTrue(actions.count { it == CommonAction.ReloadNextPage } == 1)
            assertTrue(actions.none { it == CommonAction.LoadMore })
        }
    }

    private fun setHistoryContent(
        state: HistoryViewState,
        presentation: HistoryPresentation = HistoryPresentation.TopTabs,
        contentFocusActive: Boolean = true,
        onAction: (UIAction) -> Unit = {},
    ) {
        composeRule.setContent {
            PuberTheme {
                CompositionLocalProvider(
                    LocalAutoFocusOnLaunchEnabled provides false,
                    LocalContentFocusActive provides contentFocusActive,
                ) {
                    HistoryScreenContent(
                        state = state,
                        presentation = presentation,
                        onAction = onAction,
                    )
                }
            }
        }
    }

    private fun setHistoryContent(
        state: MutableState<HistoryViewState>,
        presentation: HistoryPresentation = HistoryPresentation.TopTabs,
        contentFocusActive: Boolean = true,
        onAction: (UIAction) -> Unit,
    ) {
        composeRule.setContent {
            PuberTheme {
                CompositionLocalProvider(
                    LocalAutoFocusOnLaunchEnabled provides false,
                    LocalContentFocusActive provides contentFocusActive,
                ) {
                    HistoryScreenContent(
                        state = state.value,
                        presentation = presentation,
                        onAction = onAction,
                    )
                }
            }
        }
    }

    private fun assertRightKeepsFocusOnFinalCard(items: List<HistoryItemUIState>) {
        setHistoryContent(HistoryViewState.Content(items = items))
        val finalCard = composeRule.historyCardNode(index = items.lastIndex)

        finalCard
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused()
            .performKeyInput {
                keyDown(Key.DirectionRight)
                keyUp(Key.DirectionRight)
            }
            .assertIsFocused()
    }

    private fun openMenuState(
        item: HistoryItemUIState = historyItem(),
        isDeletionPending: Boolean = false,
        isRefreshPending: Boolean = false,
        isNextPagePending: Boolean = false,
    ): HistoryViewState.Content {
        return HistoryViewState.Content(
            items = listOf(item),
            isRefreshing = isRefreshPending,
            isLoadingMore = isNextPagePending,
            isDeleteExactMediaAvailable =
                !isDeletionPending && !isRefreshPending && !isNextPagePending,
            openMenuKey = item.rowKey,
            deletingKeys = if (isDeletionPending) setOf(item.rowKey) else emptySet(),
            focusKey = item.rowKey,
        )
    }

    private fun historyItem(
        itemId: Int = 42,
        deletionMediaId: Int = 700,
        isWatched: Boolean = false,
        showWatchedIndicator: Boolean = true,
        playbackTarget: HistoryPlaybackTarget = HistoryPlaybackTarget.Movie(videoNumber = 1),
    ): HistoryItemUIState {
        val semanticKey = when (playbackTarget) {
            is HistoryPlaybackTarget.Movie -> HistorySemanticKey.Movie(
                itemId = itemId,
                videoNumber = playbackTarget.videoNumber,
            )
            is HistoryPlaybackTarget.Episode -> HistorySemanticKey.Episode(
                itemId = itemId,
                seasonNumber = playbackTarget.seasonNumber,
                episodeNumber = playbackTarget.episodeNumber,
            )
            HistoryPlaybackTarget.Details -> null
        }
        val rowKey = semanticKey?.let(HistoryRowKey::Media)
            ?: HistoryRowKey.DeletionMedia(deletionMediaId)
        return HistoryItemUIState(
            itemId = itemId,
            deletionMediaId = deletionMediaId,
            rowKey = rowKey,
            semanticKey = semanticKey,
            videoNumber = (semanticKey as? HistorySemanticKey.Movie)?.videoNumber,
            seasonNumber = (semanticKey as? HistorySemanticKey.Episode)?.seasonNumber,
            episodeNumber = (semanticKey as? HistorySemanticKey.Episode)?.episodeNumber,
            progressPercent = 0.5f,
            isWatched = isWatched,
            lastViewedAt = "2099-07-23T12:00:00Z",
            playbackTarget = playbackTarget,
            card = VideoItemUIState(
                id = itemId,
                title = "Synthetic history movie",
                imageUrl = "",
                bigImageUrl = "",
                wideImageUrl = "",
                showTitle = true,
                progressPercent = 0.5f,
                isWatched = isWatched,
                showWatchedIndicator = showWatchedIndicator,
            ),
        )
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.historyCardNode(
        index: Int = 0,
    ):
        SemanticsNodeInteraction {
        return onAllNodes(historyCardTagMatcher, useUnmergedTree = true)[index]
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.historyCardTestTag(): String {
        return historyCardNode()
            .fetchSemanticsNode()
            .config[SemanticsProperties.TestTag]
    }

    private companion object {
        val historyCardTagMatcher = SemanticsMatcher("has opaque History card test tag") { node ->
            node.config
                .getOrNull(SemanticsProperties.TestTag)
                ?.startsWith(HISTORY_CARD_TEST_TAG_PREFIX) == true
        }
    }
}
