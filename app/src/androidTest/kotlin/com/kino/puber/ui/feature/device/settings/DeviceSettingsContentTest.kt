package com.kino.puber.ui.feature.device.settings

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.model.ApiDomainDialogState
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsActions
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsListUi
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsState
import com.kino.puber.ui.feature.device.settings.model.DeviceUi
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val TMDB_ATTRIBUTION_NOTICE =
    "This product uses the TMDB API but is not endorsed or certified by TMDB."
private const val TMDB_ATTRIBUTION_TITLE = "О TMDB"
private const val TMDB_LOGO_DESCRIPTION = "Логотип TMDB"

internal class DeviceSettingsContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsRenderTmdbAttributionBlock() {
        composeRule.setContent {
            PuberTheme {
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
                )
            }
        }

        composeRule
            .onNodeWithTag(DEVICE_SETTINGS_LIST_TEST_TAG)
            .performScrollToNode(hasText(TMDB_ATTRIBUTION_NOTICE))
        composeRule.onNodeWithText(TMDB_ATTRIBUTION_TITLE).assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription(TMDB_LOGO_DESCRIPTION)
            .assertIsDisplayed()
            .assertRendersTmdbGradient()
        composeRule.onNodeWithText(TMDB_ATTRIBUTION_NOTICE).assertIsDisplayed()
    }

    @Test
    fun settingsRenderAndDispatchSpeedTestLauncher() {
        val actions = mutableListOf<UIAction>()
        val launcherText = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getString(R.string.speed_test_launcher)
        composeRule.setContent {
            PuberTheme {
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
                    onAction = actions::add,
                )
            }
        }

        composeRule
            .onNodeWithTag(DEVICE_SETTINGS_LIST_TEST_TAG)
            .performScrollToNode(hasText(launcherText))
        composeRule
            .onNodeWithText(launcherText)
            .assertIsDisplayed()
            .performClick()

        assertTrue(actions.single() == DeviceSettingsActions.OpenSpeedTest)
    }
}

private fun SemanticsNodeInteraction.assertRendersTmdbGradient() {
    val pixels = captureToImage().toPixelMap()
    var greenPixelFound = false
    var cyanPixelFound = false

    for (x in 0 until pixels.width) {
        for (y in 0 until pixels.height) {
            val color = pixels[x, y]
            greenPixelFound = greenPixelFound || (
                color.red >= 0.35f &&
                    color.green >= 0.65f &&
                    color.blue >= 0.45f &&
                    color.green - color.red >= 0.12f
                )
            cyanPixelFound = cyanPixelFound || (
                color.red <= 0.35f &&
                    color.green >= 0.60f &&
                    color.blue >= 0.70f &&
                    color.blue - color.red >= 0.30f
                )
        }
    }

    assertTrue(
        "TMDB logo must render the approved green-to-cyan gradient, not monochrome pixels",
        greenPixelFound && cyanPixelFound,
    )
}
