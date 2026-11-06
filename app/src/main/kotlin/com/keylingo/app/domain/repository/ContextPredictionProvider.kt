package com.keylingo.app.domain.repository

/**
 * Personal word-sequence history (bigrams and trigrams) learned on device
 * from what the user actually types. [languageCode] keeps English and
 * Spanish sequences separate.
 */
interface ContextPredictionProvider {

    /**
     * Records that [nextWord] followed [previousWord] (and, when known,
     * [secondPreviousWord] before that) at a real word boundary.
     * Implementations apply their own validation before persisting.
     */
    suspend fun recordSequence(previousWord: String, nextWord: String, languageCode: String = "en", secondPreviousWord: String? = null)

    /** Known next-word counts after [previousWord]. Fetched once per refresh; callers look candidates up in memory. */
    suspend fun nextWordCounts(previousWord: String, languageCode: String = "en"): Map<String, Int>

    /** Known next-word counts after the pair ([secondPreviousWord], [previousWord]) -- sharper than bigrams when available. */
    suspend fun nextWordCountsAfterPair(secondPreviousWord: String, previousWord: String, languageCode: String = "en"): Map<String, Int>

    /** Deletes all personal sequence history (user-initiated, from Settings). */
    suspend fun clearAll()

    /** Deletes every stored sequence containing [word] (when the user forgets that word). */
    suspend fun forgetWord(word: String, languageCode: String) {}

    /** Applies [com.keylingo.app.domain.model.PersonalDataRetention] to sequence history. Returns rows removed. */
    suspend fun pruneStale(now: Long): Int = 0
}
