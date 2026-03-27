package com.kino.puber.data.repository

import android.content.Context
import com.kino.puber.domain.model.SubtitleSize

class PlayerPreferencesRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getPreferredAudioTrackId(itemId: Int): Int? {
        val key = "${KEY_AUDIO_TRACK_PREFIX}$itemId"
        return if (prefs.contains(key)) prefs.getInt(key, -1) else null
    }

    fun getPreferredSubtitleLang(itemId: Int): String? {
        return prefs.getString("${KEY_SUBTITLE_LANG_PREFIX}$itemId", null)
    }

    fun saveTrackPreferences(itemId: Int, audioTrackId: Int?, subtitleLang: String?) {
        prefs.edit().apply {
            if (audioTrackId != null) {
                putInt("${KEY_AUDIO_TRACK_PREFIX}$itemId", audioTrackId)
            } else {
                remove("${KEY_AUDIO_TRACK_PREFIX}$itemId")
            }
            if (subtitleLang != null) {
                putString("${KEY_SUBTITLE_LANG_PREFIX}$itemId", subtitleLang)
            } else {
                remove("${KEY_SUBTITLE_LANG_PREFIX}$itemId")
            }
            apply()
        }
    }

    fun getSubtitleSize(): SubtitleSize {
        val ordinal = prefs.getInt(KEY_SUBTITLE_SIZE, SubtitleSize.MEDIUM.ordinal)
        return SubtitleSize.entries.getOrElse(ordinal) { SubtitleSize.MEDIUM }
    }

    fun saveSubtitleSize(size: SubtitleSize) {
        prefs.edit().putInt(KEY_SUBTITLE_SIZE, size.ordinal).apply()
    }

    private companion object {
        const val PREFS_NAME = "player_preferences"
        const val KEY_AUDIO_TRACK_PREFIX = "audio_track_"
        const val KEY_SUBTITLE_LANG_PREFIX = "subtitle_lang_"
        const val KEY_SUBTITLE_SIZE = "subtitle_size"
    }
}
