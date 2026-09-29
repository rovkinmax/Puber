package com.kino.puber.data.repository

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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

    @Test
    fun subtitlePreferenceMetadata_persistsAndClearsSemanticIdentity() {
        val fixture = fixture()

        fixture.repository.saveTrackPreferences(
            itemId = 42,
            audioLang = null,
            audioLabel = null,
            subtitleLang = "en",
            subtitleUrl = "subtitle:English Forced",
            subtitleIsForced = true,
            subtitleDescriptiveLabel = "English Forced",
        )
        val restoredRepository = PlayerPreferencesRepository(fixture.context)
        assertEquals(true, restoredRepository.getPreferredSubtitlePreference(42).isForced)
        assertEquals("English Forced", restoredRepository.getPreferredSubtitlePreference(42).descriptiveLabel)

        restoredRepository.saveTrackPreferences(42, null, null, null, null)
        assertNull(fixture.repository.getPreferredSubtitlePreference(42).isForced)
        assertNull(fixture.repository.getPreferredSubtitlePreference(42).descriptiveLabel)
    }

    private fun fixture(): Fixture {
        val preferences = TestPreferences()
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

private class TestPreferences {
    private val values: MutableMap<String, Any> = mutableMapOf()
    val sharedPreferences: SharedPreferences = mockk()

    private val editor: SharedPreferences.Editor = mockk()

    init {
        every { sharedPreferences.getBoolean(any(), any()) } answers {
            values[firstArg()] as? Boolean ?: secondArg()
        }
        every { sharedPreferences.getString(any(), any()) } answers {
            values[firstArg()] as? String ?: secondArg()
        }
        every { sharedPreferences.contains(any()) } answers { values.containsKey(firstArg()) }
        every { sharedPreferences.edit() } returns editor
        every { editor.putBoolean(any(), any()) } answers {
            values[firstArg()] = secondArg()
            editor
        }
        every { editor.putString(any(), any()) } answers {
            val value = secondArg<String?>()
            if (value == null) values.remove(firstArg()) else values[firstArg()] = value
            editor
        }
        every { editor.remove(any()) } answers {
            values.remove(firstArg())
            editor
        }
        every { editor.apply() } returns Unit
    }
}
