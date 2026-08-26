package com.kino.puber.ui.feature.device.settings

import android.app.Activity
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.kino.puber.core.di.LocalPuberKoinScope
import com.kino.puber.core.ui.navigation.AppLauncher
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.RootPuberScreen
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.core.ui.navigation.component.FlowComponent
import com.kino.puber.core.ui.uikit.component.modifier.rememberFocusRequesterOnLaunch
import com.kino.puber.core.ui.uikit.model.ApiDomainDialogState
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.ui.ScreensImpl
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsActions
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsListUi
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsState
import com.kino.puber.ui.feature.device.settings.model.DeviceUi
import kotlinx.parcelize.Parcelize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.koin.core.qualifier.named
import org.koin.dsl.module

private const val SPEED_TEST_DESTINATION_TEST_TAG = "speed_test_destination"

internal class DeviceSettingsPushBackFocusTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun firstEntry_preservesTopAnchorWithoutFocusingSpeedTestLauncher() {
        DeviceSettingsFocusProbeHost.clear()
        setProbeContent()

        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()

        val initialAnchor = composeRule.runOnIdle {
            DeviceSettingsFocusProbeHost.anchor()
        }
        assertEquals(0 to 0, initialAnchor)
        assertTrue(
            composeRule
                .onAllNodes(
                    hasTestTag(SPEED_TEST_LAUNCHER_TEST_TAG) and isFocused(),
                )
                .fetchSemanticsNodes()
                .isEmpty(),
        )
    }

    @Test
    fun speedTestBack_restoresLauncherFocusAndLazyListAnchor() {
        DeviceSettingsFocusProbeHost.clear()
        setProbeContent()

        val list = composeRule.onNodeWithTag(DEVICE_SETTINGS_LIST_TEST_TAG)
        list.performScrollToNode(hasTestTag(SPEED_TEST_LAUNCHER_TEST_TAG))
        val launcher = composeRule.onNodeWithTag(SPEED_TEST_LAUNCHER_TEST_TAG)
        launcher
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused()
        composeRule.waitForIdle()
        val anchorBefore = composeRule.runOnIdle {
            DeviceSettingsFocusProbeHost.anchor()
        }

        launcher.performSelect()
        composeRule.waitUntil(timeoutMillis = 1_500) {
            composeRule
                .onAllNodes(
                    hasTestTag(SPEED_TEST_DESTINATION_TEST_TAG) and isFocused(),
                )
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag(SPEED_TEST_DESTINATION_TEST_TAG).assertIsFocused()
        composeRule.runOnIdle {
            DeviceSettingsFocusProbeHost.pressBack()
        }

        composeRule.waitUntil(timeoutMillis = 1_500) {
            DeviceSettingsFocusProbeHost.anchor() == anchorBefore &&
                composeRule
                    .onAllNodes(
                        hasTestTag(SPEED_TEST_LAUNCHER_TEST_TAG) and isFocused(),
                    )
                    .fetchSemanticsNodes()
                    .isNotEmpty()
        }

        launcher.assertIsFocused()
        val anchorAfter = composeRule.runOnIdle {
            DeviceSettingsFocusProbeHost.anchor()
        }
        assertEquals(anchorBefore, anchorAfter)

        composeRule.mainClock.advanceTimeBy(600)
        composeRule.waitForIdle()

        launcher.assertIsFocused()
        val settledAnchor = composeRule.runOnIdle {
            DeviceSettingsFocusProbeHost.anchor()
        }
        assertEquals(anchorBefore, settledAnchor)
    }

    private fun setProbeContent() {
        composeRule.setContent {
            PuberTheme {
                val backDispatcher = requireNotNull(LocalOnBackPressedDispatcherOwner.current)
                    .onBackPressedDispatcher
                SideEffect {
                    DeviceSettingsFocusProbeHost.recordBackDispatcher(backDispatcher)
                }
                FlowComponent(
                    scopeName = "DeviceSettingsPushBackFocusTest",
                    screen = DeviceSettingsFocusProbeFlowScreen,
                    moduleFactory = { scopeId, _ ->
                        module {
                            scope(named(scopeId)) {
                                scoped<AppLauncher> { DeviceSettingsFocusNoOpAppLauncher }
                                scoped<Screens> { ScreensImpl }
                            }
                        }
                    },
                )
            }
        }
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.performSelect() {
        performKeyInput {
            keyDown(Key.DirectionCenter)
            keyUp(Key.DirectionCenter)
        }
    }
}

private object DeviceSettingsFocusProbeHost {
    private var backDispatcher: OnBackPressedDispatcher? = null
    private var listState: LazyListState? = null

    fun clear() {
        backDispatcher = null
        listState = null
    }

    fun recordBackDispatcher(backDispatcher: OnBackPressedDispatcher) {
        this.backDispatcher = backDispatcher
    }

    fun record(listState: LazyListState) {
        this.listState = listState
    }

    fun pressBack() {
        requireNotNull(backDispatcher).onBackPressed()
    }

    fun anchor(): Pair<Int, Int> {
        val state = requireNotNull(listState)
        return state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset
    }
}

@Parcelize
private data object DeviceSettingsFocusProbeFlowScreen : RootPuberScreen {
    @Composable
    override fun Content() {
        FlowComponent(
            scopeName = "DeviceSettingsFocusProbeInnerFlow",
            screen = DeviceSettingsFocusProbeScreen,
        )
    }
}

@Parcelize
private data object DeviceSettingsFocusProbeScreen : RootPuberScreen {
    @Composable
    override fun Content() {
        val router = requireNotNull(LocalPuberKoinScope.current).get<AppRouter>()
        val listState = rememberLazyListState()
        SideEffect {
            DeviceSettingsFocusProbeHost.record(listState)
        }
        DeviceSettingsContent(
            state = DeviceSettingsState.Success(
                settings = DeviceSettingsListUi(emptyList()),
                device = DeviceUi(
                    title = "Android TV",
                    hardware = "Test hardware",
                    software = "Test software",
                ),
            ),
            apiDomain = ApiDomainDialogState(
                currentDomain = "service-kp.com",
                customDomain = null,
            ),
            onAction = { action ->
                if (action == DeviceSettingsActions.OpenSpeedTest) {
                    router.navigateTo(DeviceSettingsFocusProbeDestination)
                }
            },
            listState = listState,
        )
    }
}

@Parcelize
private data object DeviceSettingsFocusProbeDestination : PuberScreen {
    @Composable
    override fun Content() {
        val focusRequester = rememberFocusRequesterOnLaunch()
        Column {
            Button(
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .testTag(SPEED_TEST_DESTINATION_TEST_TAG),
                onClick = {},
            ) {
                Text("Speed Test")
            }
        }
    }
}

private data object DeviceSettingsFocusNoOpAppLauncher : AppLauncher {
    override fun restart() = Unit

    override fun finish() = Unit

    override fun bind(activity: Activity) = Unit

    override fun unbind() = Unit
}
