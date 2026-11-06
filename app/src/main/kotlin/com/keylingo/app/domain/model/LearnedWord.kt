package com.keylingo.app.domain.model

/**
 * A word from the user's personal learned vocabulary, already past the
 * "actually learned" threshold (see [com.keylingo.app.data.learning.RoomLearnedVocabularyProvider]).
 * Deliberately a separate type from [AutocompleteSuggestion] even though
 * both ultimately become suggestion chips -- one comes from a static
 * bundled word list, the other from genuine user history, and keeping
 * them distinct types keeps that provenance visible through the domain
 * layer rather than silently blurring "the app's dictionary" with "what
 * this specific user has typed".
 */
data class LearnedWord(
    val word: String,
    val organicCount: Int,
    val acceptedCount: Int,
    val lastUsedAtEpochMillis: Long,
    val languageCode: String = "en"
)
