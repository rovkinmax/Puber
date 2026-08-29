package com.kino.puber.ui.feature.player.vm

import java.util.Locale

internal fun sameSubtitleLanguage(first: String, second: String): Boolean {
    if (first.isBlank() || second.isBlank()) return false
    return canonicalSubtitleLanguage(first) == canonicalSubtitleLanguage(second)
}

private fun canonicalSubtitleLanguage(language: String): String {
    val normalized = language
        .trim()
        .lowercase(Locale.ROOT)
        .substringBefore('-')
        .substringBefore('_')
    return runCatching { Locale.forLanguageTag(normalized).isO3Language }
        .getOrNull()
        ?.takeIf { it.isNotBlank() && it != "und" }
        ?: normalized
}

/**
 * Localized name of a subtitle language, or null when the code cannot be resolved.
 *
 * `Locale.forLanguageTag("rus")` does not resolve to a known locale, so its display name
 * comes back untranslated. Resolving the three-letter code against the available locales
 * first is what makes "rus" read as "Русский" rather than "Russian".
 */
internal fun subtitleLanguageDisplayName(language: String, displayLocale: Locale): String? {
    if (language.isBlank()) return null
    val canonical = canonicalSubtitleLanguage(language)
    if (canonical.isEmpty()) return null
    val locale = iso3Locales[canonical] ?: Locale.forLanguageTag(canonical)
    val name = runCatching { locale.getDisplayLanguage(displayLocale) }.getOrNull().orEmpty()
    return name
        .takeIf { it.isNotBlank() && !it.equals(canonical, ignoreCase = true) }
        ?.replaceFirstChar { it.titlecase(displayLocale) }
}

private val iso3Locales: Map<String, Locale> by lazy {
    Locale.getAvailableLocales()
        .filter { it.language.isNotEmpty() }
        .mapNotNull { locale ->
            runCatching { locale.isO3Language }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { iso3 -> iso3 to Locale.forLanguageTag(locale.language) }
        }
        .toMap()
}
