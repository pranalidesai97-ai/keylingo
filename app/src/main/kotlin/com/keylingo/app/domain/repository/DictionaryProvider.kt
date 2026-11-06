package com.keylingo.app.domain.repository

import com.keylingo.app.domain.model.DictionaryEntry
import com.keylingo.app.domain.model.GrammaticalForm

/**
 * Abstracts "where do word translations come from". The MVP implementation
 * is backed by Room and a bundled JSON asset, but this interface lets a
 * future implementation swap in a bigger dictionary, a remote service, or
 * an on-device ML model without touching the keyboard or hint code.
 */
interface DictionaryProvider {

    /**
     * Look up a single word. Must be fast and safe to call on every
     * keystroke pause (target: well under 30ms per lookup).
     *
     * [form] requests a specific grammatical surface (present/base,
     * infinitive, or gerund) for verbs; non-verb entries ignore it. The
     * returned [DictionaryEntry] still carries all forms it has -- callers
     * that want the resolved string call [DictionaryEntry.translationFor].
     */
    suspend fun lookup(
        word: String,
        sourceLanguage: String,
        targetLanguage: String,
        form: GrammaticalForm = GrammaticalForm.BASE
    ): DictionaryEntry?

    /** Whether the provider currently has data loaded for this language pair. */
    suspend fun isReady(sourceLanguage: String, targetLanguage: String): Boolean

    /** Number of entries available for this language pair, for diagnostics/UI. */
    suspend fun entryCount(sourceLanguage: String, targetLanguage: String): Int
}
