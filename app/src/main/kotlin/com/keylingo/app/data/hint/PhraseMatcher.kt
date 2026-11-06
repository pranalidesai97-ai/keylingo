package com.keylingo.app.data.hint

import com.keylingo.app.domain.model.PhraseChunk
import com.keylingo.app.domain.repository.PhraseChunkProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finds the longest recognized phrase chunk starting at a given position
 * in an already-tokenized-and-normalized sentence. Kept separate from
 * [SentenceAnalyzer] (same reasoning as [LearningModePolicy]): single
 * responsibility, independently testable, and a natural seam if phrase
 * matching ever needs to get smarter (fuzzy matching, multi-language
 * phrase sets) without touching the analyzer's own pipeline.
 *
 * Matching happens purely on already-normalized tokens (lowercased,
 * punctuation already excluded by tokenization) rejoined with single
 * spaces -- so "Good morning!", "GOOD MORNING", and "good    morning" all
 * produce the identical comparison key "good morning" regardless of the
 * original casing, punctuation, or whitespace width. Chunk data itself
 * (from [PhraseChunkProvider]) is stored the same way (see
 * [com.keylingo.app.data.dictionary.PhraseChunkSeeder]), so the comparison
 * is a plain, cheap string equality check.
 */
@Singleton
class PhraseMatcher @Inject constructor(
    private val phraseChunkProvider: PhraseChunkProvider
) {

    /**
     * Tries the longest known chunk first, then progressively shorter
     * ones, starting exactly at [startIndex] in [normalizedTokens].
     * [PhraseChunkProvider.allChunks] is already ordered by word count
     * descending, so the first match found here is, by construction, the
     * longest valid match -- satisfying "prefer the longest match before
     * falling back to individual words" without a separate sorting step.
     *
     * Returns null if nothing matches at this exact starting position
     * (the caller falls back to single-word resolution for this token).
     */
    suspend fun findLongestMatch(
        normalizedTokens: List<String>,
        startIndex: Int,
        sourceLanguage: String,
        targetLanguage: String
    ): PhraseChunk? {
        val chunks = phraseChunkProvider.allChunks(sourceLanguage, targetLanguage)

        for (chunk in chunks) {
            val endExclusive = startIndex + chunk.wordCount
            if (endExclusive > normalizedTokens.size) continue

            val candidateSlice = normalizedTokens.subList(startIndex, endExclusive).joinToString(" ")
            if (candidateSlice == chunk.sourcePhrase) {
                return chunk
            }
        }
        return null
    }
}
