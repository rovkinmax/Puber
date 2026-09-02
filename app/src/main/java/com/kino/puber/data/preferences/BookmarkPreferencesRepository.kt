package com.kino.puber.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.kino.puber.core.model.BookmarkMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BookmarkPreferencesRepository private constructor(
    private val preferences: SharedPreferences?,
    initialMode: BookmarkMode,
    initialQuickFolderId: Int?,
) {

    private constructor(preferences: SharedPreferences) : this(
        preferences = preferences,
        initialMode = preferences.getString(KEY_MODE, null)
            ?.let { stored -> BookmarkMode.entries.firstOrNull { it.name == stored } }
            ?: BookmarkMode.Simple,
        initialQuickFolderId = preferences.getInt(KEY_QUICK_FOLDER_ID, NO_FOLDER_ID)
            .takeUnless { it == NO_FOLDER_ID },
    )

    constructor(context: Context) : this(
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    )

    internal constructor(
        mode: BookmarkMode = BookmarkMode.Simple,
        quickFolderId: Int? = null,
    ) : this(
        preferences = null,
        initialMode = mode,
        initialQuickFolderId = quickFolderId,
    )

    private val mutableMode = MutableStateFlow(initialMode)
    val mode: StateFlow<BookmarkMode> = mutableMode.asStateFlow()

    private val mutableQuickFolderId = MutableStateFlow(initialQuickFolderId)
    val quickFolderId: StateFlow<Int?> = mutableQuickFolderId.asStateFlow()

    fun setMode(mode: BookmarkMode) {
        preferences?.edit()?.putString(KEY_MODE, mode.name)?.apply()
        mutableMode.value = mode
    }

    fun setQuickFolderId(folderId: Int?) {
        preferences?.edit()?.apply {
            if (folderId == null) {
                remove(KEY_QUICK_FOLDER_ID)
            } else {
                putInt(KEY_QUICK_FOLDER_ID, folderId)
            }
            apply()
        }
        mutableQuickFolderId.value = folderId
    }

    private companion object {
        const val PREFERENCES_NAME = "bookmark_preferences"
        const val KEY_MODE = "bookmark_mode"
        const val KEY_QUICK_FOLDER_ID = "quick_folder_id"
        const val NO_FOLDER_ID = -1
    }
}
