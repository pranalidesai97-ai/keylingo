package com.keylingo.app.domain.repository

/**
 * Seam for future full-sentence or phrase-level translation (e.g. an
 * on-device transformer model, or a cloud API the user opts into).
 * KeyLingo's MVP does NOT use this to alter what the user types; it only
 * powers the per-word [com.keylingo.app.domain.repository.HintProvider].
 * Kept as its own interface so a future "preview translation" feature can
 * be added without reshaping the dictionary/hint code.
 */
interface TranslationProvider {

    /** Translate an entire phrase. Implementations may be offline or online. */
    suspend fun translate(text: String, sourceLanguage: String, targetLanguage: String): String

    /** Whether this provider can operate without a network connection. */
    fun isOfflineCapable(): Boolean
}
