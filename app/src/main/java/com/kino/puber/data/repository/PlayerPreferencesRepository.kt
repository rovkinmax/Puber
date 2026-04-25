package com.kino.puber.data.repository

import android.content.Context
import com.kino.puber.domain.model.SubtitleSize
import com.kino.puber.ui.feature.player.model.BufferPreset

class PlayerPreferencesRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getPreferredAudioLang(itemId: Int): String? {
        return prefs.getString("${KEY_AUDIO_LANG_PREFIX}$itemId", null)
    }

    fun getPreferredSubtitleLang(itemId: Int): String? {
        return prefs.getString("${KEY_SUBTITLE_LANG_PREFIX}$itemId", null)
    }

    fun getPreferredAudioLabel(itemId: Int): String? {
        return prefs.getString("${KEY_AUDIO_LABEL_PREFIX}$itemId", null)
    }

    fun saveTrackPreferences(itemId: Int, audioLang: String?, audioLabel: String?, subtitleLang: String?) {
        prefs.edit().apply {
            if (audioLang != null) {
                putString("${KEY_AUDIO_LANG_PREFIX}$itemId", audioLang)
            } else {
                remove("${KEY_AUDIO_LANG_PREFIX}$itemId")
            }
            if (audioLabel != null) {
                putString("${KEY_AUDIO_LABEL_PREFIX}$itemId", audioLabel)
            } else {
                remove("${KEY_AUDIO_LABEL_PREFIX}$itemId")
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

    var skipIntroEnabled: Boolean
        get() = prefs.getBoolean(KEY_SKIP_INTRO, true)
        set(value) = prefs.edit().putBoolean(KEY_SKIP_INTRO, value).apply()

    var skipRecapEnabled: Boolean
        get() = prefs.getBoolean(KEY_SKIP_RECAP, true)
        set(value) = prefs.edit().putBoolean(KEY_SKIP_RECAP, value).apply()

    var skipCreditsEnabled: Boolean
        get() = prefs.getBoolean(KEY_SKIP_CREDITS, true)
        set(value) = prefs.edit().putBoolean(KEY_SKIP_CREDITS, value).apply()

    var debugOverlayEnabled: Boolean
        get() = prefs.getBoolean(KEY_DEBUG_OVERLAY, false)
        set(value) = prefs.edit().putBoolean(KEY_DEBUG_OVERLAY, value).apply()

    var preferSurroundAudio: Boolean
        get() = prefs.getBoolean(KEY_PREFER_SURROUND, false)
        set(value) = prefs.edit().putBoolean(KEY_PREFER_SURROUND, value).apply()

    var bufferPreset: BufferPreset
        get() {
            val ordinal = prefs.getInt(KEY_BUFFER_PRESET, BufferPreset.AUTO.ordinal)
            return BufferPreset.entries.getOrElse(ordinal) { BufferPreset.AUTO }
        }
        set(value) = prefs.edit().putInt(KEY_BUFFER_PRESET, value.ordinal).apply()

    var fastDnsEnabled: Boolean
        get() = prefs.getBoolean(KEY_FAST_DNS, true)
        set(value) = prefs.edit().putBoolean(KEY_FAST_DNS, value).apply()

    private companion object {
        const val PREFS_NAME = "player_preferences"
        const val KEY_AUDIO_LANG_PREFIX = "audio_lang_"
        const val KEY_AUDIO_LABEL_PREFIX = "audio_label_"
        const val KEY_SUBTITLE_LANG_PREFIX = "subtitle_lang_"
        const val KEY_SUBTITLE_SIZE = "subtitle_size"
        const val KEY_SKIP_INTRO = "skip_intro_enabled"
        const val KEY_SKIP_RECAP = "skip_recap_enabled"
        const val KEY_SKIP_CREDITS = "skip_credits_enabled"
        const val KEY_DEBUG_OVERLAY = "debug_overlay_enabled"
        const val KEY_PREFER_SURROUND = "prefer_surround_audio"
        const val KEY_BUFFER_PRESET = "buffer_preset"
        const val KEY_FAST_DNS = "fast_dns_enabled"
    }
}
