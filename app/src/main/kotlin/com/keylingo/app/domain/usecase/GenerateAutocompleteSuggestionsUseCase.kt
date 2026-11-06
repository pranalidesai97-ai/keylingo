package com.keylingo.app.domain.usecase

import com.keylingo.app.domain.model.AutocompleteSuggestion
import com.keylingo.app.domain.model.SuggestionKind
import com.keylingo.app.domain.model.SuggestionResult
import com.keylingo.app.domain.repository.AutocompleteProvider
import com.keylingo.app.domain.repository.ContextPredictionProvider
import com.keylingo.app.domain.repository.LearnedVocabularyProvider
import javax.inject.Inject
import kotlin.math.ln

/** Everything one suggestion refresh needs to know about the cursor's context. */
data class SuggestionRequest(
    /** The partial word immediately before the cursor ("" right after a space). */
    val prefix: String,
    val previousWord: String? = null,
    val secondPreviousWord: String? = null,
    val languageCode: String = "en",
    val maxSuggestions: Int = 5,
    /** False in fields where corrections would be wrong (emails, URLs, passwords, code). */
    val allowAutoCorrect: Boolean = true,
    /** False in incognito/password fields: personal history must neither be read nor written. */
    val allowPersonalData: Boolean = true,
    /** Words the user un-corrected this session; never autocorrect them again. */
    val rejectedCorrections: Set<String> = emptySet()
)

/**
 * Merges the bundled lexicon, personal vocabulary and personal word
 * sequences into one ranked suggestion strip, and decides -- conservatively --
 * whether the typed word should be autocorrected on the next separator.
 *
 * Two scenarios:
 * 1. **Mid-word** (prefix non-empty): prefix completions, keyboard-aware
 *    typo corrections, accent/apostrophe/case variants; context (n-grams)
 *    only *re-ranks* candidates that already fit what was typed.
 * 2. **Between words** (prefix empty): next-word prediction from personal
 *    trigrams, then bigrams, then the bundled cold-start table.
 *
 * Autocorrect fires only when the typed word is not a known or learned
 * word, was not previously rejected, and one candidate is clearly best:
 * an exact accent/apostrophe/case variant ("dont" -> "don't",
 * "tambien" -> "también", "i" -> "I"), or a close keyboard typo with a
 * clear margin over the runner-up. Weights are named constants below.
 */
class GenerateAutocompleteSuggestionsUseCase @Inject constructor(
    private val autocompleteProvider: AutocompleteProvider,
    private val learnedVocabularyProvider: LearnedVocabularyProvider,
    private val contextPredictionProvider: ContextPredictionProvider
) {
    /** Backwards-compatible entry point (English, no autocorrect info). */
    suspend operator fun invoke(prefix: String, previousWord: String? = null, maxSuggestions: Int = 5): List<AutocompleteSuggestion> =
        invoke(SuggestionRequest(prefix = prefix, previousWord = previousWord, maxSuggestions = maxSuggestions)).suggestions

    suspend operator fun invoke(request: SuggestionRequest, now: Long = System.currentTimeMillis()): SuggestionResult {
        val prefix = request.prefix.trim()
        return if (prefix.isEmpty()) predictNextWord(request) else rankForPrefix(prefix, request, now)
    }

    // ------------------------------------------------------------ prediction

    private suspend fun predictNextWord(request: SuggestionRequest): SuggestionResult {
        val lang = request.languageCode
        val previous = request.previousWord?.lowercase()?.takeIf { it.isNotBlank() } ?: SENTENCE_START
        val scores = LinkedHashMap<String, Double>()

        if (request.allowPersonalData && previous != SENTENCE_START) {
            val second = request.secondPreviousWord?.lowercase()
            if (!second.isNullOrBlank()) {
                for ((word, count) in contextPredictionProvider.nextWordCountsAfterPair(second, previous, lang)) {
                    scores.merge(word, TRIGRAM_WEIGHT * ln(1.0 + count), Double::plus)
                }
            }
            for ((word, count) in contextPredictionProvider.nextWordCounts(previous, lang)) {
                scores.merge(word, BIGRAM_WEIGHT * ln(1.0 + count), Double::plus)
            }
        }
        val seeds = autocompleteProvider.seedNextWords(previous, lang)
        seeds.forEachIndexed { index, word ->
            scores.merge(word, SEED_WEIGHT / (index + 1.0), Double::plus)
        }
        val suggestions = scores.entries
            .sortedByDescending { it.value }
            .map { it.key }
            .distinctBy { it.lowercase() }
            .take(request.maxSuggestions)
            .map { word ->
                val display = autocompleteProvider.spellingVariants(word, lang).firstOrNull { it.equals(word, ignoreCase = true) } ?: word
                AutocompleteSuggestion(display, autocompleteProvider.rankOf(display, lang) ?: 0, SuggestionKind.PREDICTION, lang)
            }
        return SuggestionResult(suggestions)
    }

    // ---------------------------------------------------------------- prefix

    private class Scored(val word: String, var score: Double, val kind: SuggestionKind, val rank: Int)

    private suspend fun rankForPrefix(prefix: String, request: SuggestionRequest, now: Long): SuggestionResult {
        val lang = request.languageCode
        val pool = (request.maxSuggestions * 4).coerceAtLeast(16)
        val lowerPrefix = prefix.lowercase()
        val candidates = LinkedHashMap<String, Scored>()

        fun offer(word: String, score: Double, kind: SuggestionKind, rank: Int) {
            val key = word.lowercase()
            val existing = candidates[key]
            if (existing == null || score > existing.score) candidates[key] = Scored(word, score, kind, rank)
        }

        // Context: personal n-grams re-rank, never introduce, candidates.
        val previous = request.previousWord?.lowercase()?.takeIf { it.isNotBlank() }
        val contextCounts: Map<String, Int> = if (request.allowPersonalData && previous != null) {
            val pairs = request.secondPreviousWord?.lowercase()?.takeIf { it.isNotBlank() }
                ?.let { contextPredictionProvider.nextWordCountsAfterPair(it, previous, lang) }.orEmpty()
            val bigrams = contextPredictionProvider.nextWordCounts(previous, lang)
            (bigrams.keys + pairs.keys).associateWith { (bigrams[it] ?: 0) + 2 * (pairs[it] ?: 0) }
        } else emptyMap()
        val seedContext = autocompleteProvider.seedNextWords(previous ?: SENTENCE_START, lang).map { it.lowercase() }

        fun contextBonus(word: String): Double {
            val w = word.lowercase()
            val personal = contextCounts[w]?.let { CONTEXT_WEIGHT * ln(1.0 + it) } ?: 0.0
            val seedIndex = seedContext.indexOf(w)
            val seed = if (seedIndex >= 0) SEED_CONTEXT_WEIGHT / (seedIndex + 1.0) else 0.0
            return personal + seed
        }

        // 1. Bundled completions.
        for (s in autocompleteProvider.suggest(prefix, lang, pool)) {
            val exact = s.word.equals(prefix, ignoreCase = true)
            offer(s.word, frequencyScore(s.frequencyRank) + contextBonus(s.word) + if (exact) EXACT_MATCH_BONUS else 0.0, SuggestionKind.COMPLETION, s.frequencyRank)
        }
        // 2. Personal vocabulary.
        val learnedTyped: Boolean
        if (request.allowPersonalData) {
            for (w in learnedVocabularyProvider.suggest(prefix, lang, pool)) {
                val recency = if (now - w.lastUsedAtEpochMillis < RECENT_WINDOW_MILLIS) RECENCY_BONUS else 0.0
                val personal = PERSONAL_WEIGHT * ln(1.0 + w.organicCount + ACCEPTED_MULTIPLIER * w.acceptedCount)
                val exact = w.word.equals(prefix, ignoreCase = true)
                val bundledRank = autocompleteProvider.rankOf(w.word, lang)
                val display = autocompleteProvider.spellingVariants(w.word, lang).firstOrNull { it.equals(w.word, ignoreCase = true) } ?: w.word
                offer(display, (bundledRank?.let { frequencyScore(it) } ?: LEARNED_BASE) + personal + recency + contextBonus(w.word) +
                    if (exact) EXACT_MATCH_BONUS else 0.0, SuggestionKind.COMPLETION, bundledRank ?: 0)
            }
            learnedTyped = learnedVocabularyProvider.isLearned(prefix, lang)
        } else learnedTyped = false

        val knownTyped = autocompleteProvider.isKnownWord(prefix, lang) || learnedTyped
        val rejected = lowerPrefix in request.rejectedCorrections.map { it.lowercase() }

        // 3. Spelling variants with identical letters (apostrophes, accents, capitals).
        val variants = autocompleteProvider.spellingVariants(prefix, lang).filter { it != prefix }
        for (v in variants) {
            val rank = autocompleteProvider.rankOf(v, lang) ?: 50_000
            offer(v, frequencyScore(rank) + contextBonus(v) + VARIANT_BONUS, SuggestionKind.CORRECTION, rank)
        }

        // 4. Keyboard-aware typo corrections (only worth computing for 2+ letters).
        val corrections = if (prefix.length >= 2) autocompleteProvider.corrections(prefix, lang, 4, includeCompletions = true) else emptyList()
        for (c in corrections) {
            val score = frequencyScore(c.frequencyRank) + contextBonus(c.word) - DISTANCE_PENALTY * c.distance -
                if (knownTyped) KNOWN_WORD_CORRECTION_PENALTY else 0.0
            offer(c.word, score, if (c.isCompletion) SuggestionKind.COMPLETION else SuggestionKind.CORRECTION, c.frequencyRank)
        }

        // ---- autocorrect decision
        var autoCorrection: AutocompleteSuggestion? = null
        val eligible = request.allowAutoCorrect && !rejected && prefix.none { it.isDigit() } &&
            prefix.drop(1).none { it.isUpperCase() } // leave iPhone, McDonald, ALLCAPS alone
        if (eligible) {
            val caseFix = variants.firstOrNull { it.equals(prefix, ignoreCase = true) && it.any(Char::isUpperCase) && prefix.all { ch -> !ch.isUpperCase() } }
            if (knownTyped) {
                // Known word: only fix obligatory capitalisation ("i" -> "I", "monday" -> "Monday"), or a very rare
                // dictionary word shadowing a very common contraction ("cant" -> "can't", "wont" -> "won't").
                if (caseFix != null && !learnedTyped) autoCorrection = AutocompleteSuggestion(caseFix, 0, SuggestionKind.CORRECTION, lang)
                else if (!learnedTyped) {
                    val typedRank = autocompleteProvider.rankOf(prefix, lang) ?: Int.MAX_VALUE
                    val common = variants.firstOrNull { (autocompleteProvider.rankOf(it, lang) ?: Int.MAX_VALUE) < COMMON_VARIANT_RANK }
                    if (common != null && typedRank > RARE_WORD_RANK) {
                        autoCorrection = AutocompleteSuggestion(common, autocompleteProvider.rankOf(common, lang) ?: 0, SuggestionKind.CORRECTION, lang)
                    }
                }
            } else {
                val variant = variants.firstOrNull()
                if (variant != null) {
                    autoCorrection = AutocompleteSuggestion(variant, autocompleteProvider.rankOf(variant, lang) ?: 0, SuggestionKind.CORRECTION, lang)
                } else {
                    val whole = corrections.filter { !it.isCompletion }
                    val best = whole.firstOrNull()
                    val maxDistance = when {
                        prefix.length <= 2 -> 0.0
                        prefix.length <= 4 -> 0.6
                        prefix.length <= 7 -> 1.0
                        else -> 1.5
                    }
                    if (best != null && best.distance <= maxDistance && best.frequencyRank <= MAX_AUTOCORRECT_RANK) {
                        val runnerUp = whole.getOrNull(1)
                        val bestScore = frequencyScore(best.frequencyRank) - DISTANCE_PENALTY * best.distance
                        val runnerScore = runnerUp?.let { frequencyScore(it.frequencyRank) - DISTANCE_PENALTY * it.distance }
                        if (runnerScore == null || bestScore - runnerScore >= AUTOCORRECT_MARGIN || best.distance + 0.3 <= runnerUp.distance) {
                            autoCorrection = AutocompleteSuggestion(matchCase(best.word, prefix), best.frequencyRank, SuggestionKind.CORRECTION, lang)
                        }
                    }
                }
            }
        }

        val ranked = candidates.values.sortedByDescending { it.score }
            .map { AutocompleteSuggestion(matchCase(it.word, prefix), it.rank, it.kind, lang) }
            .distinctBy { it.word }
            .toMutableList()
        if (autoCorrection != null) {
            ranked.removeAll { it.word.equals(autoCorrection.word, ignoreCase = true) }
            ranked.add(0, autoCorrection)
            // Always let the user keep exactly what they typed.
            ranked.removeAll { it.word == prefix }
            ranked.add(1.coerceAtMost(ranked.size), AutocompleteSuggestion(prefix, 0, SuggestionKind.LITERAL, lang))
        }
        return SuggestionResult(ranked.take(request.maxSuggestions), autoCorrection)
    }

    companion object {
        /** Bundled cold-start lists use this key for "beginning of a sentence". */
        const val SENTENCE_START = "<s>"

        /**
         * Applies the casing the user typed to a candidate: "Hel" -> "Hello",
         * "HEL" -> "HELLO"; words whose canonical form is capitalised ("I'm",
         * "Monday", "México") keep their capitals.
         */
        fun matchCase(candidate: String, typed: String): String {
            if (typed.isEmpty()) return candidate
            val canonicalHasCaps = candidate.any(Char::isUpperCase)
            return when {
                typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } -> candidate.uppercase()
                typed.first().isUpperCase() -> candidate.replaceFirstChar { it.titlecase() }
                canonicalHasCaps -> candidate
                else -> candidate
            }
        }

        private fun frequencyScore(rank: Int): Double = FREQUENCY_SCALE - ln((rank.coerceAtLeast(1) + 10).toDouble()) * FREQUENCY_SLOPE

        // Tunable weights (deterministic, documented in ARCHITECTURE.md).
        private const val FREQUENCY_SCALE = 100.0
        private const val FREQUENCY_SLOPE = 6.0
        private const val EXACT_MATCH_BONUS = 25.0
        private const val VARIANT_BONUS = 30.0
        private const val PERSONAL_WEIGHT = 9.0
        private const val ACCEPTED_MULTIPLIER = 3.0
        private const val LEARNED_BASE = 55.0
        private const val RECENCY_BONUS = 6.0
        private const val RECENT_WINDOW_MILLIS = 24L * 60 * 60 * 1000
        private const val CONTEXT_WEIGHT = 10.0
        private const val SEED_CONTEXT_WEIGHT = 8.0
        private const val DISTANCE_PENALTY = 18.0
        private const val KNOWN_WORD_CORRECTION_PENALTY = 30.0
        private const val TRIGRAM_WEIGHT = 30.0
        private const val BIGRAM_WEIGHT = 20.0
        private const val SEED_WEIGHT = 10.0
        private const val AUTOCORRECT_MARGIN = 8.0
        private const val MAX_AUTOCORRECT_RANK = 25_000
        private const val RARE_WORD_RANK = 30_000
        private const val COMMON_VARIANT_RANK = 2_000
    }
}
