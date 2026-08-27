package com.kino.puber.core.ui.uikit.component.details

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import coil3.EventListener
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.NullRequestDataException
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(DelicateCoilApi::class)
internal class VideoItemGridDetailsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val requestedData = mutableListOf<Any?>()
    private val nullRequestDataErrors = mutableListOf<Throwable>()

    @After
    fun tearDownImageLoader() {
        SingletonImageLoader.reset()
    }

    @Test
    fun loadingWithoutArtwork_doesNotExecuteAnImageRequest() {
        installTrackingImageLoader()

        composeRule.setContent {
            PuberTheme {
                VideoItemGridDetails(
                    modifier = androidx.compose.ui.Modifier,
                    state = VideoDetailsUIState.Loading,
                )
            }
        }
        composeRule.waitForIdle()

        assertEquals(emptyList<Any?>(), requestedData)
        assertNoNullRequestDataErrors()
    }

    @Test
    fun emptyArtwork_doesNotExecuteAnImageRequest() {
        installTrackingImageLoader()

        composeRule.setContent {
            PuberTheme {
                VideoItemGridDetails(
                    modifier = androidx.compose.ui.Modifier,
                    state = VideoDetailsUIState(
                        id = 42,
                        title = "Details",
                        description = "",
                        imageUrl = "",
                        imageFallbackUrls = emptyList(),
                        trailerUrl = "",
                        ratings = emptyList(),
                        year = "",
                        genres = "",
                        duration = "",
                        country = "",
                    ),
                )
            }
        }
        composeRule.waitForIdle()

        assertEquals(emptyList<Any?>(), requestedData)
        assertNoNullRequestDataErrors()
    }

    @Test
    fun artworkFailure_advancesThroughFallbackUrlsInOrder() {
        installTrackingImageLoader()

        composeRule.setContent {
            PuberTheme {
                VideoItemGridDetails(
                    modifier = androidx.compose.ui.Modifier,
                    state = VideoDetailsUIState(
                        id = 42,
                        title = "Details",
                        description = "",
                        imageUrl = "https://first.example/poster.jpg",
                        imageFallbackUrls = listOf("https://second.example/poster.jpg"),
                        trailerUrl = "",
                        ratings = emptyList(),
                        year = "",
                        genres = "",
                        duration = "",
                        country = "",
                    ),
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            requestedData.size >= 2
        }

        assertEquals(
            listOf(
                "https://first.example/poster.jpg",
                "https://second.example/poster.jpg",
            ),
            requestedData,
        )
        assertNoNullRequestDataErrors()
    }

    private fun installTrackingImageLoader() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(context)
                .eventListener(
                    object : EventListener() {
                        override fun onError(
                            request: coil3.request.ImageRequest,
                            result: ErrorResult,
                        ) {
                            if (result.throwable is NullRequestDataException) {
                                nullRequestDataErrors += result.throwable
                            }
                        }
                    },
                )
                .components {
                    add(
                        Interceptor { chain ->
                            requestedData += chain.request.data
                            ErrorResult(
                                image = null,
                                request = chain.request,
                                throwable = IllegalStateException("test image failure"),
                            )
                        },
                    )
                }
                .build(),
        )
    }

    private fun assertNoNullRequestDataErrors() {
        assertTrue(
            "Coil reported NullRequestDataException: $nullRequestDataErrors",
            nullRequestDataErrors.none { it is NullRequestDataException },
        )
    }
}
