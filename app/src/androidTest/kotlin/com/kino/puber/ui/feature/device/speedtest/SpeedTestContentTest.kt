package com.kino.puber.ui.feature.device.speedtest

import android.text.format.Formatter
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.test.platform.app.InstrumentationRegistry
import com.kino.puber.R
import com.kino.puber.core.system.ConnectionTransport
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.domain.interactor.speedtest.SpeedTestServer
import com.kino.puber.ui.feature.device.speedtest.model.SpeedTestAction
import com.kino.puber.ui.feature.device.speedtest.model.SpeedTestRowState
import com.kino.puber.ui.feature.device.speedtest.model.SpeedTestRowStatus
import com.kino.puber.ui.feature.device.speedtest.model.SpeedTestSessionStatus
import com.kino.puber.ui.feature.device.speedtest.model.SpeedTestViewState
import org.junit.Rule
import org.junit.Test

internal class SpeedTestContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun initialState_focusesStartAndShowsKnownServers() {
        val actions = mutableListOf<UIAction>()
        composeRule.setContent {
            PuberTheme {
                SpeedTestContent(
                    state = SpeedTestViewState(),
                    onAction = actions::add,
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onNodeWithTag(SPEED_TEST_START_TAG)
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsProperties.Focused) == true
        }

        composeRule.onNodeWithTag(SPEED_TEST_START_TAG)
            .assertIsFocused()
            .assertIsEnabled()
        composeRule.onNodeWithText("Амстердам").assertIsDisplayed()
        composeRule.onNodeWithText("Москва").assertIsDisplayed()
        composeRule.onNodeWithTag(SPEED_TEST_START_TAG).performClick()
        assert(actions.single() == SpeedTestAction.Start)
    }

    @Test
    fun runningState_showsProgressAndStop_dispatchesActions() {
        val actions = mutableListOf<UIAction>()
        composeRule.setContent {
            PuberTheme {
                SpeedTestContent(
                    state = SpeedTestViewState(
                        transport = ConnectionTransport.Wifi,
                        currentServer = SpeedTestServer.AMSTERDAM,
                        rows = listOf(
                            SpeedTestRowState(
                                server = SpeedTestServer.AMSTERDAM,
                                status = SpeedTestRowStatus.Running,
                                downloadedBytes = 50,
                                expectedBytes = 100,
                                megabitsPerSecond = 12.5,
                            ),
                            SpeedTestRowState(server = SpeedTestServer.MOSCOW),
                        ),
                        sessionStatus = SpeedTestSessionStatus.Running,
                        canStart = false,
                        canStop = true,
                    ),
                    onAction = actions::add,
                )
            }
        }

        composeRule.onNodeWithText("Wi-Fi").assertIsDisplayed()
        composeRule.onNodeWithTag(SPEED_TEST_AMSTERDAM_TAG).assertIsDisplayed()
        assertByteProgress(downloadedBytes = 50, expectedBytes = 100)
        composeRule.onNodeWithText("12.5 Мбит/с").assertIsDisplayed()
        composeRule.onNodeWithTag(SPEED_TEST_STOP_TAG)
            .assertIsDisplayed()
            .performClick()

        assert(actions.single() == SpeedTestAction.Stop)
    }

    @Test
    fun runningState_dpadRightMovesFocusFromStartToStop() {
        val state = mutableStateOf(SpeedTestViewState())
        composeRule.setContent {
            PuberTheme {
                SpeedTestContent(
                    state = state.value,
                    onAction = {},
                )
            }
        }
        waitForFocus(SPEED_TEST_START_TAG)

        composeRule.runOnIdle {
            state.value = runningState()
        }
        composeRule.onNodeWithTag(SPEED_TEST_START_TAG)
            .assertIsFocused()
            .performKeyInput {
                keyDown(Key.DirectionRight)
                keyUp(Key.DirectionRight)
            }

        composeRule.onNodeWithTag(SPEED_TEST_STOP_TAG).assertIsFocused()
    }

    @Test
    fun completedState_restoresStartFocusAfterRunning() {
        assertTerminalStateRestoresStartFocus(SpeedTestSessionStatus.Completed)
    }

    @Test
    fun failedState_restoresStartFocusAfterRunning() {
        assertTerminalStateRestoresStartFocus(SpeedTestSessionStatus.Failed)
    }

    @Test
    fun canceledState_restoresStartFocusAfterRunning() {
        assertTerminalStateRestoresStartFocus(SpeedTestSessionStatus.Canceled)
    }

    @Test
    fun knownMetadata_showsExactlyOneCurrentServerMarker() {
        composeRule.setContent {
            PuberTheme {
                SpeedTestContent(
                    state = SpeedTestViewState(
                        rows = listOf(
                            SpeedTestRowState(
                                server = SpeedTestServer.AMSTERDAM,
                                displayLabel = "Amsterdam API",
                                isCurrentServer = true,
                            ),
                            SpeedTestRowState(
                                server = SpeedTestServer.MOSCOW,
                                displayLabel = "Moscow API",
                            ),
                        ),
                    ),
                    onAction = {},
                )
            }
        }

        composeRule.onAllNodesWithText("Текущий сервер").assertCountEquals(1)
    }

    @Test
    fun unmappedMetadata_showsNoCurrentServerMarker() {
        composeRule.setContent {
            PuberTheme {
                SpeedTestContent(
                    state = SpeedTestViewState(
                        rows = SpeedTestServer.knownServers.map(::SpeedTestRowState),
                    ),
                    onAction = {},
                )
            }
        }

        composeRule.onAllNodesWithText("Текущий сервер").assertCountEquals(0)
    }

    @Test
    fun failedState_showsRetainedPositiveSpeedAndLocalizedError() {
        val expectedRetainedSpeed = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getString(R.string.speed_test_result, 0.8)

        composeRule.setContent {
            PuberTheme {
                SpeedTestContent(
                    state = SpeedTestViewState(
                        rows = listOf(
                            SpeedTestRowState(
                                server = SpeedTestServer.AMSTERDAM,
                                status = SpeedTestRowStatus.Completed,
                                downloadedBytes = 100,
                                expectedBytes = 100,
                                megabitsPerSecond = 20.0,
                            ),
                            SpeedTestRowState(
                                server = SpeedTestServer.MOSCOW,
                                status = SpeedTestRowStatus.Failed,
                                downloadedBytes = 25,
                                expectedBytes = 100,
                                elapsedMillis = 250,
                                megabitsPerSecond = 0.8,
                                errorMessage = "Сервер недоступен",
                            ),
                        ),
                        sessionStatus = SpeedTestSessionStatus.Failed,
                    ),
                    onAction = {},
                )
            }
        }

        assertByteProgress(downloadedBytes = 100, expectedBytes = 100)
        assertByteProgress(downloadedBytes = 25, expectedBytes = 100)
        composeRule.onNodeWithText("20.0 Мбит/с").assertIsDisplayed()
        composeRule.onNodeWithText(expectedRetainedSpeed).assertIsDisplayed()
        composeRule.onNodeWithText("Сервер недоступен").assertIsDisplayed()
        composeRule.onNodeWithText("Текущий сервер").assertDoesNotExist()
    }

    @Test
    fun canceledState_showsCanceledText() {
        composeRule.setContent {
            PuberTheme {
                SpeedTestContent(
                    state = SpeedTestViewState(
                        rows = listOf(
                            SpeedTestRowState(
                                server = SpeedTestServer.AMSTERDAM,
                                status = SpeedTestRowStatus.Canceled,
                                downloadedBytes = 40,
                                expectedBytes = 100,
                            ),
                            SpeedTestRowState(server = SpeedTestServer.MOSCOW),
                        ),
                        sessionStatus = SpeedTestSessionStatus.Canceled,
                    ),
                    onAction = {},
                )
            }
        }

        assertByteProgress(downloadedBytes = 40, expectedBytes = 100)
        composeRule.onNodeWithText("Тест остановлен").assertIsDisplayed()
    }

    private fun assertTerminalStateRestoresStartFocus(
        terminalStatus: SpeedTestSessionStatus,
    ) {
        val state = mutableStateOf(SpeedTestViewState())
        composeRule.setContent {
            PuberTheme {
                SpeedTestContent(
                    state = state.value,
                    onAction = {},
                )
            }
        }
        waitForFocus(SPEED_TEST_START_TAG)

        composeRule.runOnIdle {
            state.value = runningState()
        }
        composeRule.onNodeWithTag(SPEED_TEST_START_TAG)
            .performKeyInput {
                keyDown(Key.DirectionRight)
                keyUp(Key.DirectionRight)
            }
        composeRule.onNodeWithTag(SPEED_TEST_STOP_TAG).assertIsFocused()

        composeRule.runOnIdle {
            state.value = state.value.copy(
                sessionStatus = terminalStatus,
                canStart = true,
                canStop = false,
            )
        }

        waitForFocus(SPEED_TEST_START_TAG)
        composeRule.onNodeWithTag(SPEED_TEST_START_TAG).assertIsFocused()
    }

    private fun waitForFocus(tag: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onNodeWithTag(tag)
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsProperties.Focused) == true
        }
    }

    private fun assertByteProgress(
        downloadedBytes: Long,
        expectedBytes: Long,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val expectedText = context.getString(
            R.string.speed_test_byte_progress,
            Formatter.formatShortFileSize(context, downloadedBytes),
            Formatter.formatShortFileSize(context, expectedBytes),
        )

        composeRule.onNodeWithText(expectedText).assertIsDisplayed()
    }

    private fun runningState() = SpeedTestViewState(
        rows = listOf(
            SpeedTestRowState(
                server = SpeedTestServer.AMSTERDAM,
                status = SpeedTestRowStatus.Running,
                downloadedBytes = 50,
                expectedBytes = 100,
            ),
            SpeedTestRowState(server = SpeedTestServer.MOSCOW),
        ),
        sessionStatus = SpeedTestSessionStatus.Running,
        canStart = false,
        canStop = true,
    )
}
