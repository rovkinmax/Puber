package com.kino.puber.ui.feature.bookmarkpicker.component

import android.app.Activity
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import cafe.adriel.voyager.core.screen.ScreenKey
import com.kino.puber.core.di.DIScope
import com.kino.puber.core.di.LocalPuberKoinScope
import com.kino.puber.core.ui.navigation.AppLauncher
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.core.ui.navigation.component.FlowComponent
import com.kino.puber.core.ui.navigation.component.onBackPressed
import com.kino.puber.playertestfixtures.server.QueryMatchMode
import com.kino.puber.playertestfixtures.server.ResponsePlan
import com.kino.puber.profile.PlayerTestControl
import com.kino.puber.ui.ScreensImpl
import com.kino.puber.ui.feature.bookmarkpicker.model.BookmarkPickerResult
import com.kino.puber.ui.feature.bookmarkpicker.openBookmarkPicker
import kotlinx.parcelize.Parcelize
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.core.qualifier.named
import org.koin.dsl.module

private const val CALLER_TAG = "picker-e2e-caller"
private const val CALLER_SCOPE = "BookmarkPickerFlowCallerScreen"
private const val ITEM_ID = 42
private const val ITEM_TITLE = "Item 42"
private const val WATCH_LATER_FOLDER_ID = 1
private const val KIDS_FOLDER_ID = 2
private const val WAIT_TIMEOUT_MS = 10_000L

/**
 * Walks the whole bookmark path against the hermetic backend: a caller screen opens the picker,
 * the picker loads its folders over the wire, a toggle is written back, and closing the picker
 * hands the selection to the caller's result listener. The caller screen must stay composed
 * throughout — the picker is an overlay, and tearing the caller down takes its DI scope with it.
 */
internal class BookmarkPickerFlowE2ETest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var server: PlayerTestControl

    @Before
    fun startBackend() {
        CallerRouterHandle.reset()
        server = PlayerTestControl()
        server.start()
        // route() only builds a route; reset() is what installs them on the server.
        server.reset(
            listOf(
                server.route(
                    id = "bookmark-folders",
                    path = "/v1/bookmarks",
                    queryMode = QueryMatchMode.Contains,
                    response = json(
                        """{"items":[
                            {"id":$WATCH_LATER_FOLDER_ID,"title":"Буду смотреть"},
                            {"id":$KIDS_FOLDER_ID,"title":"Для детей"}
                        ]}""",
                    ),
                ),
                server.route(
                    id = "item-folders",
                    path = "/v1/bookmarks/get-item-folders",
                    query = mapOf("item" to ITEM_ID.toString()),
                    queryMode = QueryMatchMode.Contains,
                    response = json(
                        """{"status":200,"folders":[
                            {"id":$WATCH_LATER_FOLDER_ID,"title":"Буду смотреть","count":1}
                        ]}""",
                    ),
                ),
                server.route(
                    id = "bookmark-add",
                    method = "POST",
                    path = "/v1/bookmarks/add",
                    queryMode = QueryMatchMode.Contains,
                    response = json("""{"status":200}"""),
                ),
                server.route(
                    id = "bookmark-remove",
                    method = "POST",
                    path = "/v1/bookmarks/remove-item",
                    queryMode = QueryMatchMode.Contains,
                    response = json("""{"status":200}"""),
                ),
            )
        )
    }

    @After
    fun stopBackend() {
        if (::server.isInitialized) {
            server.close()
        }
        CallerRouterHandle.reset()
    }

    @Test
    fun pickerOpensOverTheCaller_writesTheToggle_andReturnsTheSelection() {
        setContent()

        openPicker()

        composeRule.onNodeWithTag(CALLER_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(BOOKMARK_PICKER_DIALOG_TAG).assertIsDisplayed()
        awaitFolders()
        composeRule.onNodeWithText("Буду смотреть").assertIsDisplayed()
        composeRule.onNodeWithText("Для детей").assertIsDisplayed()

        composeRule.onNodeWithTag(bookmarkFolderRowTag(KIDS_FOLDER_ID)).activate()
        composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
            server.requestJournal.matchedRoutes["bookmark-add"] == 1
        }

        assertEquals(null, server.requestJournal.matchedRoutes["bookmark-remove"])

        closePicker()

        assertEquals(
            BookmarkPickerResult(
                itemId = ITEM_ID,
                selectedFolderIds = listOf(WATCH_LATER_FOLDER_ID, KIDS_FOLDER_ID),
            ),
            CallerRouterHandle.result,
        )
        assertEquals(emptyList<Any>(), server.requestJournal.unknownRequests)
    }

    @Test
    fun dismissingWithoutTouchingAnything_returnsTheFoldersTheItemAlreadyHad() {
        setContent()

        openPicker()
        composeRule.onNodeWithTag(BOOKMARK_PICKER_DIALOG_TAG).assertIsDisplayed()
        awaitFolders()

        closePicker()

        assertEquals(
            BookmarkPickerResult(
                itemId = ITEM_ID,
                selectedFolderIds = listOf(WATCH_LATER_FOLDER_ID),
            ),
            CallerRouterHandle.result,
        )
        assertEquals(null, server.requestJournal.matchedRoutes["bookmark-add"])
        assertEquals(null, server.requestJournal.matchedRoutes["bookmark-remove"])
    }

    private fun setContent() {
        composeRule.setContent {
            FlowComponent(
                scopeName = "BookmarkPickerFlowE2ETest",
                screen = CallerScreen,
                moduleFactory = { scopeId, _ ->
                    module {
                        scope(named(scopeId)) {
                            scoped<AppLauncher> { E2ENoOpAppLauncher }
                            scoped<Screens> { ScreensImpl }
                        }
                    }
                },
            )
        }
        composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) { CallerRouterHandle.router != null }
    }

    private fun openPicker() {
        composeRule.runOnUiThread {
            requireNotNull(CallerRouterHandle.router).openBookmarkPicker(
                itemId = ITEM_ID,
                listener = { result -> CallerRouterHandle.result = result },
            )
        }
        composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
            composeRule.onAllNodesWithTagCount(BOOKMARK_PICKER_DIALOG_TAG) > 0
        }
    }

    private fun closePicker() {
        // Physical Back is routed through the flow's dispatcher, which is what asks the picker
        // for its selection; a bare router.back() would pop it without any result.
        composeRule.runOnUiThread { onBackPressed(requireNotNull(CallerRouterHandle.router)) }
        composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) { CallerRouterHandle.result != null }
    }

    /** The picker starts in a loading state; its folders only appear once the backend answers. */
    private fun awaitFolders() {
        composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
            server.requestJournal.matchedRoutes["bookmark-folders"] == 1 &&
                server.requestJournal.matchedRoutes["item-folders"] == 1
        }
        composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
            composeRule.onAllNodesWithTagCount(bookmarkFolderRowTag(KIDS_FOLDER_ID)) > 0
        }
    }

    private fun json(body: String): ResponsePlan = ResponsePlan.Text(
        status = 200,
        body = body,
        contentType = "application/json; charset=utf-8",
    )
}

private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTagCount(tag: String): Int =
    onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size

private fun SemanticsNodeInteraction.activate(): SemanticsNodeInteraction = apply {
    performSemanticsAction(SemanticsActions.OnClick)
}

private object CallerRouterHandle {
    @Volatile
    var router: AppRouter? = null

    @Volatile
    var result: BookmarkPickerResult? = null

    fun reset() {
        router = null
        result = null
    }
}

@Parcelize
private data object CallerScreen : PuberScreen {

    override val key: ScreenKey
        get() = CALLER_SCOPE

    @Composable
    override fun Content() = DIScope(scopeName = key) {
        val scope = requireNotNull(LocalPuberKoinScope.current)
        SideEffect { CallerRouterHandle.router = scope.get<AppRouter>() }
        BasicText(
            text = "Caller",
            modifier = Modifier
                .fillMaxSize()
                .testTag(CALLER_TAG)
                .focusable(),
        )
    }
}

private object E2ENoOpAppLauncher : AppLauncher {
    override fun restart() = Unit

    override fun finish() = Unit

    override fun bind(activity: Activity) = Unit

    override fun unbind() = Unit
}
