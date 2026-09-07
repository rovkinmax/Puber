package com.kino.puber.ui.feature.bookmarkpicker

import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.OnResult
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.ui.feature.bookmarkpicker.model.BookmarkPickerResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BookmarkPickerNavigationTest {

    private lateinit var screens: Screens
    private lateinit var router: AppRouter

    @BeforeEach
    fun setup() {
        screens = mockk(relaxed = true)
        router = mockk(relaxed = true)
        every { router.screens } returns screens
    }

    @Test
    fun openBookmarkPicker_givesTheScreenTheSameCodeItRegistersTheListenerUnder() {
        val screen = mockk<PuberScreen>()
        val screenResultCode = slot<Int>()
        every {
            screens.bookmarkPicker(itemId = 42, resultCode = capture(screenResultCode))
        } returns screen

        router.openBookmarkPicker(itemId = 42, listener = {})

        val requestCode = slot<Int>()
        verify {
            router.navigateForResult<BookmarkPickerResult>(
                screen = screen,
                requestCode = capture(requestCode),
                listener = any(),
            )
        }
        assertEquals(
            screenResultCode.captured,
            requestCode.captured,
            "The picker reports its result under the code baked into the screen, " +
                "so the listener must be registered under that same code",
        )
    }

    @Test
    fun openBookmarkPicker_usesAFreshCodePerRequest() {
        every { screens.bookmarkPicker(any(), any()) } returns mockk()

        router.openBookmarkPicker(itemId = 1, listener = {})
        router.openBookmarkPicker(itemId = 2, listener = {})

        val requestCodes = mutableListOf<Int>()
        verify(exactly = 2) {
            router.navigateForResult<BookmarkPickerResult>(any(), capture(requestCodes), any())
        }
        assertNotEquals(requestCodes[0], requestCodes[1])
    }

    @Test
    fun openBookmarkPicker_forwardsTheListenerAndTheItemIdentity() {
        val screen = mockk<PuberScreen>()
        every { screens.bookmarkPicker(itemId = 7, resultCode = any()) } returns screen
        val listener: OnResult<BookmarkPickerResult> = {}

        router.openBookmarkPicker(item = videoItem(id = 7), listener = listener)

        val forwarded = slot<OnResult<BookmarkPickerResult>>()
        verify { router.navigateForResult(screen, any(), capture(forwarded)) }
        assertSame(listener, forwarded.captured)
    }

    @Test
    fun withBookmarkResult_marksAMovieSavedWhenItGainedTheQuickFolder() {
        val item = videoItem(id = 7, isSeriesLike = false, isSaved = false, isBookmarked = false)

        val updated = item.withBookmarkResult(
            result(itemId = 7, folderIds = listOf(3), isInQuickFolder = true)
        )

        assertTrue(updated.isBookmarked)
        assertTrue(updated.isSaved, "The quick folder is what the saved state tracks for a movie")
    }

    @Test
    fun withBookmarkResult_leavesAMovieUnsavedWhenItOnlyGainedAnotherFolder() {
        val item = videoItem(id = 7, isSeriesLike = false, isSaved = false, isBookmarked = false)

        val updated = item.withBookmarkResult(
            result(itemId = 7, folderIds = listOf(3), isInQuickFolder = false)
        )

        assertTrue(updated.isBookmarked)
        assertFalse(
            updated.isSaved,
            "Un-saving only writes to the quick folder, so filing elsewhere must not read as saved",
        )
    }

    @Test
    fun withBookmarkResult_clearsAMovieSavedStateWhenItLostEveryFolder() {
        val item = videoItem(id = 7, isSeriesLike = false, isSaved = true, isBookmarked = true)

        val updated = item.withBookmarkResult(result(itemId = 7, folderIds = emptyList()))

        assertFalse(updated.isBookmarked)
        assertFalse(updated.isSaved)
    }

    @Test
    fun withBookmarkResult_leavesASeriesSavedStateAlone() {
        val item = videoItem(id = 7, isSeriesLike = true, isSaved = true, isBookmarked = false)

        val updated = item.withBookmarkResult(result(itemId = 7, folderIds = emptyList()))

        assertFalse(updated.isBookmarked)
        assertTrue(updated.isSaved, "A series tracks 'saved' through its own watchlist, independently of folders")
    }

    @Test
    fun withBookmarkResult_ignoresAResultForAnotherItem() {
        val item = videoItem(id = 7, isSeriesLike = false, isSaved = false, isBookmarked = false)

        val updated = item.withBookmarkResult(result(itemId = 8, folderIds = listOf(3)))

        assertSame(item, updated)
    }

    private fun result(
        itemId: Int,
        folderIds: List<Int>,
        isInQuickFolder: Boolean = folderIds.isNotEmpty(),
    ) = BookmarkPickerResult(
        itemId = itemId,
        selectedFolderIds = folderIds,
        isInQuickFolder = isInQuickFolder,
    )

    private fun videoItem(
        id: Int,
        isSeriesLike: Boolean = false,
        isSaved: Boolean = false,
        isBookmarked: Boolean = false,
    ) = VideoItemUIState(
        id = id,
        title = "Item $id",
        imageUrl = "",
        bigImageUrl = "",
        isSeriesLike = isSeriesLike,
        isSaved = isSaved,
        isBookmarked = isBookmarked,
    )
}
