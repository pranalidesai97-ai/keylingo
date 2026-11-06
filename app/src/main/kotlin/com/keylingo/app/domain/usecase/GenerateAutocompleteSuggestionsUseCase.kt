package com.keylingo.app.domain.usecase

import com.keylingo.app.domain.model.AutocompleteSuggestion
import com.keylingo.app.domain.repository.AutocompleteProvider
import com.keylingo.app.domain.repository.ContextPredictionProvider
import com.keylingo.app.domain.repository.LearnedVocabularyProvider
import javax.inject.Inject

/**
 * Single entry point the presentation layer calls to turn "the word
 * currently being typed (if any) plus the word just before it (if any)"
 * into "what autocomplete should show". Merges three fully independent
 * sources -- the bundled static word list ([AutocompleteProvider]), the
 * user's personal learned vocabulary ([LearnedVocabularyProvider]), and
 * the user's personal word-sequence history ([ContextPredictionProvider])
 * -- into one ranked list. None of the three providers know about each
 * other; this use case is the only place their outputs meet.
 *
 * Two distinct scenarios, both real and both handled:
 *
 * 1. **Prefix typing** (`prefix` non-blank, e.g. "happ"): candidates come
 *    from [learnedVocabularyProvider] and [autocompleteProvider], both
 *    already prefix-filtered by their own queries. [contextPredictionProvider]
 *    only *re-ranks* among these already-prefix-matching candidates via
 *    [BIGRAM_WEIGHT] -- it can never introduce a word that doesn't match
 *    what's actually been typed. This is what keeps context from
 *    "completely overriding prefix matching".
 * 2. **Pure next-word prediction** (`prefix` blank but [previousWord]
 *    known, e.g. right after "I am "): there is no prefix to filter by,
 *    so the only sensible suggestions *are* [contextPredictionProvider]'s
 *    next-word counts for [previousWord], directly ranked by count. If
 *    there's no bigram history for this previous word either, this
 *    correctly returns nothing rather than guessing.
 *
 * Scoring in scenario 1 is a plain weighted sum, not ML: personal
 * frequency (organic + accepted, weighted differently), a recency bonus
 * for anything used in the last 24h, a bigram-count boost, and dictionary
 * frequency for words with no personal history at all. The exact weights
 * are named constants below specifically so they're inspectable/tunable
 * in one place rather than buried in the arithmetic.
 */
class GenerateAutocompleteSuggestionsUseCase @Inject constructor(
    private val autocompleteProvider: AutocompleteProvider,
    private val learnedVocabularyProvider: LearnedVocabularyProvider,
    private val contextPredictionProvider: ContextPredictionProvider
) {
    suspend operator fun invoke(
        prefix: String,
        previousWord: String? = null,
        maxSuggestions: Int = 5
    ): List<AutocompleteSuggestion> {
        val normalizedPrefix = prefix.trim()

        return if (normalizedPrefix.isEmpty()) {
            predictNextWord(previousWord, maxSuggestions)
        } else {
            rankPrefixMatches(normalizedPrefix, previousWord, maxSuggestions)
        }
    }

    /** Scenario 2: nothing typed yet for the next word -- pure bigram-driven prediction, or nothing. */
    private suspend fun predictNextWord(previousWord: String?, maxSuggestions: Int): List<AutocompleteSuggestion> {
        if (previousWord.isNullOrBlank()) return emptyList()

        return contextPredictionProvider.nextWordCounts(previousWord)
            .entries
            .sortedByDescending { it.value }
            .take(maxSuggestions)
            .map { AutocompleteSuggestion(word = it.key, frequencyRank = 0) }
    }

    /** Scenario 1: mid-word typing -- prefix-gated candidates, bigram history only re-ranks among them. */
    private suspend fun rankPrefixMatches(
        prefix: String,
        previousWord: String?,
        maxSuggestions: Int
    ): List<AutocompleteSuggestion> {
        // Pull a wider candidate pool than maxSuggestions so a strong
        // bigram/personal signal can actually promote a candidate that
        // wouldn't have made a naive top-N cut from either source alone.
        val candidatePoolSize = (maxSuggestions * 4).coerceAtLeast(20)

        val learned = learnedVocabularyProvider.suggest(prefix, maxSuggestions = candidatePoolSize)
        val static = autocompleteProvider.suggest(prefix, maxSuggestions = candidatePoolSize)
        val nextWordCounts = if (previousWord.isNullOrBlank()) {
            emptyMap()
        } else {
            contextPredictionProvider.nextWordCounts(previousWord)
        }

        val now = System.currentTimeMillis()
        val scored = LinkedHashMap<String, Double>()

        for (word in learned) {
            val recencyBonus = if (now - word.lastUsedAtEpochMillis < RECENT_WINDOW_MILLIS) RECENCY_BONUS else 0.0
            val personalScore = word.organicCount * ORGANIC_WEIGHT + word.acceptedCount * ACCEPTED_WEIGHT
            val bigramScore = (nextWordCounts[word.word] ?: 0) * BIGRAM_WEIGHT
            scored[word.word] = LEARNED_BASE_SCORE + personalScore + recencyBonus + bigramScore
        }
        for (word in static) {
            if (word.word in scored) continue // already scored via learned vocabulary; don't overwrite with a weaker static-only score
            val dictionaryScore = DICTIONARY_SCORE_SCALE / (word.frequencyRank + 1).toDouble()
            val bigramScore = (nextWordCounts[word.word] ?: 0) * BIGRAM_WEIGHT
            scored[word.word] = dictionaryScore + bigramScore
        }

        return scored.entries
            .sortedByDescending { it.value }
            .take(maxSuggestions)
            .map { AutocompleteSuggestion(word = it.key, frequencyRank = 0) }
    }

    private companion object {
        const val ORGANIC_WEIGHT = 10.0
        const val ACCEPTED_WEIGHT = 30.0
        const val RECENCY_BONUS = 15.0
        const val RECENT_WINDOW_MILLIS = 24L * 60 * 60 * 1000
        const val BIGRAM_WEIGHT = 40.0
        // Baseline that keeps any learned word (even with zero personal-
        // frequency/recency/bigram score yet) ranked above a purely-static
        // dictionary word by default -- a word the user has personally
        // used before is a stronger signal than raw dictionary frequency.
        const val LEARNED_BASE_SCORE = 500.0
        const val DICTIONARY_SCORE_SCALE = 1000.0
    }
}
