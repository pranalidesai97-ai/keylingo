package com.keylingo.app.domain.repository

import com.keylingo.app.domain.model.GrammaticalForm

/**
 * Everything language-specific about analyzing the text the user is
 * *typing* (source language, English today) -- as opposed to
 * [LanguagePack], which describes the language being *taught* (target).
 *
 * [SentenceAnalyzer][com.keylingo.app.data.hint.SentenceAnalyzer] depends
 * only on this interface for anything language-shaped: tokenizing,
 * normalizing, stopwords, inflection fallback, and the two local grammar
 * signals it currently understands (gerund-by-suffix, infinitive-by-
 * preceding-word). Supporting a new source language later -- e.g. typing
 * in Portuguese -- means writing one new implementation of this
 * interface; the analyzer's pipeline logic doesn't change at all.
 */
interface SourceLanguageRules {

    /** ISO 639-1 code of the language being typed, e.g. "en". */
    val languageCode: String

    /**
     * Splits raw input text into ordered word tokens, in their original
     * casing, with punctuation/whitespace/digits excluded. What counts as
     * a "word boundary" is language (and script) specific, so this isn't
     * a shared regex on the analyzer -- e.g. a future CJK source language
     * would need real segmentation instead of whitespace/punctuation
     * splitting.
     */
    fun tokenize(text: String): List<String>

    /** Normalizes one raw token for lookup/comparison (lowercasing today; could grow to fold diacritics, etc. per language). */
    fun normalize(word: String): String

    /** Function words (articles, prepositions, auxiliary verbs, etc.) to skip when picking hint candidates. */
    val stopWords: Set<String>

    /**
     * Candidate lemma forms to try against the dictionary if an exact
     * lookup on [normalizedWord] misses (e.g. English strips "-ing",
     * "-ed", "-s", "-er"/"-est"). Ordered by likelihood; the caller tries
     * them in order and stops at the first dictionary hit.
     */
    fun inflectionCandidates(normalizedWord: String): List<String>

    /**
     * Does [normalizedWord]'s own spelling signal a specific grammatical
     * form on its own, independent of any surrounding words? (English:
     * trailing "-ing" unambiguously signals [GrammaticalForm.GERUND].)
     * Returns null if the word's spelling alone doesn't imply anything.
     */
    fun surfaceFormSignal(normalizedWord: String): GrammaticalForm?

    /**
     * Does the word at [normalizedPreviousWord] (or null, if this is the
     * first word) imply a specific grammatical form for the word that
     * follows it? (English: a bare "to" immediately before a verb marks
     * it as [GrammaticalForm.INFINITIVE].) Returns null if there's no
     * such signal.
     */
    fun formImpliedByPrecedingWord(normalizedPreviousWord: String?): GrammaticalForm?
}
