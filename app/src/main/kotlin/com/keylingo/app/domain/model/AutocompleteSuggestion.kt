package com.keylingo.app.domain.model

/**
 * A single English autocomplete candidate, independent of how it is
 * stored (Room today). Deliberately minimal -- autocomplete has no
 * translation, part of speech, or CEFR concept, unlike [DictionaryEntry].
 *
 * [frequencyRank] follows the same convention used throughout the app
 * (lower = more common); callers needing ranked results should trust the
 * order [com.keylingo.app.domain.repository.AutocompleteProvider] returns
 * them in rather than re-sorting.
 */
data class AutocompleteSuggestion(
    val word: String,
    val frequencyRank: Int
)
