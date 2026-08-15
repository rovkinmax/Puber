package com.kino.puber.data.repository

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class PlayerPreferencesRepositoryTest {

    @Test
    fun media3PlaybackPreferences_useCurrentBehaviorDefaults() {
        val repository = fixture().repository

        assertTrue(repository.discardEmbeddedArtworkMetadata)
        assertFalse(repository.hagcPlaybackEnabled)
    }

    @Test
    fun media3PlaybackPreferences_persistIndependentValues() {
        val fixture = fixture()

        fixture.repository.discardEmbeddedArtworkMetadata = false
        fixture.repository.hagcPlaybackEnabled = true

        val restoredRepository = PlayerPreferencesRepository(fixture.context)
        assertFalse(restoredRepository.discardEmbeddedArtworkMetadata)
        assertTrue(restoredRepository.hagcPlaybackEnabled)
    }

    private fun fixture(): Fixture {
        val preferences = BooleanTestPreferences()
        val context = mockk<Context>()
        every {
            context.getSharedPreferences(any(), Context.MODE_PRIVATE)
        } returns preferences.sharedPreferences
        return Fixture(
            context = context,
            repository = PlayerPreferencesRepository(context),
        )
    }

    private data class Fixture(
        val context: Context,
        val repository: PlayerPreferencesRepository,
    )
}

private class BooleanTestPreferences {
    private val values: MutableMap<String, Boolean> = mutableMapOf()
    val sharedPreferences: SharedPreferences = mockk()

    private val editor: SharedPreferences.Editor = mockk()

    init {
        every { sharedPreferences.getBoolean(any(), any()) } answers {
            values[firstArg()] ?: secondArg()
        }
        every { sharedPreferences.edit() } returns editor
        every { editor.putBoolean(any(), any()) } answers {
            values[firstArg()] = secondArg()
            editor
        }
        every { editor.apply() } returns Unit
    }
}
