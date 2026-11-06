package com.keylingo.app.domain.model

/**
 * A short recognized phrase (e.g. "I am", "going to", "I want to") with its
 * own translation, plus the grammatical form any content word immediately
 * following it should be resolved in. This is how "I am going to work"
 * teaches "voy" + "trabajar" (infinitive) instead of four disconnected
 * word-for-word hints.
 */
data class PhraseChunk(
    val sourcePhrase: String,
    val targetPhrase: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    /** Number of whitespace-separated words in [sourcePhrase]; used for greedy longest-match. */
    val wordCount: Int,
    /** Grammatical form the next content word after this chunk should use, if any. */
    val requiredNextForm: GrammaticalForm?
)
