package com.keylingo.app.domain.repository

import com.keylingo.app.domain.model.PhraseChunk

/**
 * Abstracts "where do recognized phrase chunks come from" (e.g. "I am" ->
 * "estoy", "going to" -> "voy a"). Kept separate from [DictionaryProvider]
 * because phrase-level teaching is a different lookup shape (phrase-to-
 * phrase, with an attached grammar hint for what follows) than single-word
 * translation -- same "swap the implementation later" seam, single
 * responsibility per interface.
 */
interface PhraseChunkProvider {

    /**
     * All known chunks for a language pair, longest ([PhraseChunk.wordCount])
     * first, so callers can greedily match the longest phrase before
     * falling back to shorter ones. Small, static data -- safe to load
     * once and keep in memory.
     */
    suspend fun allChunks(sourceLanguage: String, targetLanguage: String): List<PhraseChunk>
}
