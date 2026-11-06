package com.keylingo.app.domain.repository

import com.keylingo.app.domain.model.AutocompleteSuggestion

/**
 * Abstracts "where do English autocomplete suggestions come from". The MVP
 * implementation is backed by Room and a bundled JSON word list, but this
 * interface lets a future implementation swap in a bigger word list, a
 * personalized/learned dictionary, or an on-device prediction model
 * without touching the keyboard code.
 *
 * Zero relationship to [DictionaryProvider]/[HintProvider]/[PhraseChunkProvider],
 * by design -- autocomplete is a fully independent vertical slice from the
 * Spanish hint pipeline (see AutocompleteModule's KDoc).
 */
interface AutocompleteProvider {

    /**
     * Return English words starting with [prefix], most-common first.
     * Must be fast and safe to call on every keystroke pause (target: well
     * under 30ms). [prefix] need not be pre-normalized -- implementations
     * are responsible for their own trimming/lowercasing.
     *
     * [maxSuggestions] caps the result size; implementations should apply
     * it at the query level rather than truncating an unbounded list.
     */
    suspend fun suggest(prefix: String, maxSuggestions: Int = 5): List<AutocompleteSuggestion>

    /** Whether the provider currently has word-list data loaded. */
    suspend fun isReady(): Boolean

    /** Number of words available, for diagnostics/UI. */
    suspend fun wordCount(): Int
}
