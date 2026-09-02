package com.kino.puber.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.kino.puber.core.model.BookmarkMode
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class BookmarkPreferencesRepositoryTest {

    @Test
    fun defaultsToSimpleModeWithoutQuickFolder() {
        val repository = fixture().repository

        assertEquals(BookmarkMode.Simple, repository.mode.value)
        assertNull(repository.quickFolderId.value)
    }

    @Test
    fun modeAndQuickFolderPersistAcrossRepositoryInstances() {
        val fixture = fixture()

        fixture.repository.setMode(BookmarkMode.Extended)
        fixture.repository.setQuickFolderId(42)
        val restored = BookmarkPreferencesRepository(fixture.context)

        assertEquals(BookmarkMode.Extended, restored.mode.value)
        assertEquals(42, restored.quickFolderId.value)
    }

    @Test
    fun clearingQuickFolderRemovesPersistedIdentity() {
        val fixture = fixture()
        fixture.repository.setQuickFolderId(42)

        fixture.repository.setQuickFolderId(null)
        val restored = BookmarkPreferencesRepository(fixture.context)

        assertNull(restored.quickFolderId.value)
    }

    private fun fixture(): Fixture {
        val preferences = BookmarkTestPreferences()
        val context = mockk<Context>()
        every {
            context.getSharedPreferences(any(), Context.MODE_PRIVATE)
        } returns preferences.sharedPreferences
        return Fixture(
            context = context,
            repository = BookmarkPreferencesRepository(context),
        )
    }

    private data class Fixture(
        val context: Context,
        val repository: BookmarkPreferencesRepository,
    )
}

private class BookmarkTestPreferences {
    private val values: MutableMap<String, Any> = mutableMapOf()
    val sharedPreferences: SharedPreferences = mockk()

    private val editor: SharedPreferences.Editor = mockk()

    init {
        every { sharedPreferences.getString(any(), any()) } answers {
            values[firstArg()] as? String ?: secondArg<String?>()
        }
        every { sharedPreferences.getInt(any(), any()) } answers {
            values[firstArg()] as? Int ?: secondArg()
        }
        every { sharedPreferences.edit() } returns editor
        every { editor.putString(any(), any()) } answers {
            values[firstArg()] = secondArg<String>()
            editor
        }
        every { editor.putInt(any(), any()) } answers {
            values[firstArg()] = secondArg<Int>()
            editor
        }
        every { editor.remove(any()) } answers {
            values.remove(firstArg())
            editor
        }
        every { editor.apply() } returns Unit
    }
}
