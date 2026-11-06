package com.keylingo.app.domain.repository

import com.keylingo.app.domain.model.LearnedWord

/**
 * The user's personal vocabulary, learned on device. Independent of the
 * bundled word lists: this is genuine user data, persisted across restarts
 * and never sent anywhere.
 */
interface LearnedVocabularyProvider {

    /** One natural completion of [word] (typed, then a space/punctuation). Cheap; called at every word boundary. */
    suspend fun recordOrganicUsage(word: String, languageCode: String = "en")

    /** The user explicitly picked [word] from the suggestion strip -- a stronger signal than organic typing. */
    suspend fun recordAcceptedSuggestion(word: String, languageCode: String = "en")

    /** The user undid an autocorrection back to [word]: they meant it, so it should not be corrected again. */
    suspend fun recordRejectedCorrection(word: String, languageCode: String = "en")

    /** Ranked personal completions for [prefix]. Empty until a word has crossed the "learned" threshold. */
    suspend fun suggest(prefix: String, languageCode: String = "en", maxSuggestions: Int = 3): List<LearnedWord>

    /** True once [word] is part of the user's vocabulary (so autocorrect must leave it alone). */
    suspend fun isLearned(word: String, languageCode: String = "en"): Boolean

    /** The learned vocabulary (past the threshold), most recently used first, for the Settings list. */
    suspend fun listLearned(limit: Int = 1000): List<LearnedWord> = emptyList()

    /** Forgets one word, so it is neither suggested nor protected from autocorrect any more. */
    suspend fun forget(word: String, languageCode: String) {}

    /** Applies [com.keylingo.app.domain.model.PersonalDataRetention] to incidental history. Returns rows removed. */
    suspend fun pruneStale(now: Long): Int = 0

    /** Deletes the whole personal vocabulary (user-initiated, from Settings). */
    suspend fun clearAll()
}
