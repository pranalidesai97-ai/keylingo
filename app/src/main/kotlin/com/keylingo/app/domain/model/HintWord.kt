package com.keylingo.app.domain.model

/**
 * A single hint shown above the keyboard. [originalWord] is the exact text
 * the user typed (a single word, or a short recognized phrase like
 * "I am") -- it is only ever read, never written back into the message.
 * [formLabel] is a short human-readable tag for display (e.g. "noun",
 * "infinitive", "gerund") so the hint bar can show grammatical context,
 * not just the bare translation.
 *
 * [dictionaryEntryId] is null for phrase-chunk hints (chunks aren't rows in
 * the word dictionary) and set for single-word hints. Nothing reads it
 * yet -- it's here so tap-to-expand, favorites, and spaced repetition can
 * key off a specific dictionary row later without another [HintWord]
 * shape change.
 *
 * [confidence] (0f..1f) reflects how sure the hint-generation logic is
 * that this is the *correct* translation for the grammatical role it
 * assigned -- e.g. a word resolved via an explicit phrase-chunk pattern
 * ("I am ___ing" -> gerund) is high confidence; a bare fallback lookup
 * with no surrounding grammar signal (like ambiguous noun/verb words
 * without a disambiguating pattern) is lower. Not surfaced in the UI yet;
 * exists so future hint-ranking/quality work has a real signal to sort on
 * instead of treating every hint as equally certain.
 */
data class HintWord(
    val originalWord: String,
    val translatedWord: String,
    val partOfSpeech: PartOfSpeech,
    val formLabel: String,
    val dictionaryEntryId: Long?,
    val confidence: Float
)
