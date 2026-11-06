package com.keylingo.app.domain.repository

import com.keylingo.app.domain.model.LearnedWord

/**
 * Abstracts "where does the user's personal learned vocabulary live and
 * how is it ranked". Independent of [AutocompleteProvider] (the static
 * bundled word list) and [DictionaryProvider] (the Spanish teaching
 * dictionary) -- this is the only one of the three backed by genuine,
 * continuously-written user data rather than a bundled read-only asset.
 */
interface LearnedVocabularyProvider {

    /**
     * Records one natural completion of [word] (the user typed it and
     * then moved on -- a space, punctuation, or new word). Implementations
     * decide their own validation/threshold rules for when a word actually
     * becomes "learned" and starts appearing in [suggest]; this call
     * itself should be cheap enough to make on every word boundary.
     */
    suspend fun recordOrganicUsage(word: String, languageCode: String = "en")

    /**
     * Records an explicit acceptance: the user tapped [word] as a
     * suggestion. A stronger, more deliberate signal than [recordOrganicUsage].
     */
    suspend fun recordAcceptedSuggestion(word: String, languageCode: String = "en")

    /** Ranked personal-vocabulary completions for [prefix], most likely first. Empty until a word has crossed the "learned" threshold. */
    suspend fun suggest(prefix: String, languageCode: String = "en", maxSuggestions: Int = 3): List<LearnedWord>
}
