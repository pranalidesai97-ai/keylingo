package com.keylingo.app.domain.repository

/**
 * Abstracts "where does personal word-sequence (bigram) history live".
 * Independent of [LearnedVocabularyProvider] (personal vocabulary -- does
 * the user know this word) and [AutocompleteProvider]/[DictionaryProvider]
 * (static bundled data) -- this is genuinely dynamic, per-user sequence
 * data with its own storage and its own query shape.
 *
 * [languageCode] is threaded through every method so English and Spanish
 * sequences never contaminate each other, even though only English
 * actually has a real invocation path today -- see [recordSequence]'s
 * KDoc.
 */
interface ContextPredictionProvider {

    /**
     * Records that [nextWord] immediately followed [previousWord] at a
     * real word boundary the user typed. Both words are expected
     * pre-extracted and already lowercased by the caller (see
     * [com.keylingo.app.keyboard.KeyboardViewModel]'s context-extraction
     * helper); this method still applies its own eligibility rules before
     * persisting anything (see [com.keylingo.app.data.context.RoomContextPredictionProvider]).
     *
     * Today this is only ever called with `languageCode = "en"`: KeyLingo
     * shows Spanish only as translation hints, never as text the user
     * actually types/commits (Spanish hint chips aren't tappable), so
     * there is currently no real trigger for Spanish word-boundary
     * events. The `languageCode` parameter exists so that changes the
     * moment Spanish text *is* committed somewhere (e.g. a future
     * tap-to-insert hint), with zero changes needed here.
     */
    suspend fun recordSequence(previousWord: String, nextWord: String, languageCode: String = "en")

    /**
     * All known next-word counts for [previousWord], as a map from word
     * to count, for [languageCode]. Returns an empty map if nothing is
     * known yet. Deliberately returns the *whole* map in one call rather
     * than a per-candidate lookup method -- callers should fetch this
     * once per suggestion refresh and do in-memory lookups against it for
     * each candidate, not query per candidate.
     */
    suspend fun nextWordCounts(previousWord: String, languageCode: String = "en"): Map<String, Int>
}
