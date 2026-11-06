package com.keylingo.app.domain.repository

import com.keylingo.app.domain.model.AutocompleteSuggestion

/** A typo-correction candidate with its keyboard-aware edit distance (lower = closer to what was typed). */
data class CorrectionCandidate(val word: String, val frequencyRank: Int, val distance: Double, val isCompletion: Boolean)

/**
 * The bundled (read-only) vocabulary for typing in a given language: prefix
 * completion, typo correction, word validity and cold-start next-word
 * lists. Personal data is never stored here -- see
 * [LearnedVocabularyProvider] and [ContextPredictionProvider].
 */
interface AutocompleteProvider {

    /** Most common words starting with [prefix] (case-, apostrophe- and, for Spanish, accent-insensitive). */
    suspend fun suggest(prefix: String, languageCode: String = "en", maxSuggestions: Int = 5): List<AutocompleteSuggestion>

    /** Words close to [typed] on the keyboard, best first. [includeCompletions] also matches word beginnings ("recie" -> "receive"). */
    suspend fun corrections(typed: String, languageCode: String = "en", maxSuggestions: Int = 3, includeCompletions: Boolean = true): List<CorrectionCandidate>

    /** True if [word] is a valid word of [languageCode] exactly as spelled (ignoring case). */
    suspend fun isKnownWord(word: String, languageCode: String = "en"): Boolean

    /** Correctly spelled variants sharing the same letters: "dont" -> ["don't"], "esta" -> ["está", "esta"]. */
    suspend fun spellingVariants(word: String, languageCode: String = "en"): List<String>

    /** Bundled cold-start continuations of [previousWord], most likely first. */
    suspend fun seedNextWords(previousWord: String, languageCode: String = "en"): List<String>

    /** Frequency rank of [word] in the bundled list, or null if unknown. */
    suspend fun rankOf(word: String, languageCode: String = "en"): Int?

    suspend fun isReady(): Boolean
    suspend fun wordCount(): Int
}
