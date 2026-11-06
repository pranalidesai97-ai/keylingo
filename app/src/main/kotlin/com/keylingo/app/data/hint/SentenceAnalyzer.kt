package com.keylingo.app.data.hint

import com.keylingo.app.domain.model.CefrLevel
import com.keylingo.app.domain.model.DictionaryEntry
import com.keylingo.app.domain.model.GrammaticalForm
import com.keylingo.app.domain.model.HintWord
import com.keylingo.app.domain.model.LearningMode
import com.keylingo.app.domain.model.PartOfSpeech
import com.keylingo.app.domain.model.PhraseChunk
import com.keylingo.app.domain.repository.DictionaryProvider
import com.keylingo.app.domain.repository.SourceLanguageRules
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The core hint-generation engine. Deliberately standalone and
 * language-agnostic: it depends only on [DictionaryProvider] (dictionary
 * lookups), [SourceLanguageRules] (everything language-specific about the
 * text being *typed* -- tokenizing, normalizing, stopwords, inflection
 * fallback, local grammar signals), and [PhraseMatcher] (multi-word
 * phrase recognition). None of English's specific morphology rules live
 * here anymore -- they're in [com.keylingo.app.data.language.EnglishSourceLanguageRules].
 * Supporting a new source language is a new [SourceLanguageRules]
 * implementation; this class's pipeline doesn't change.
 *
 * It's also fully unit-testable without Android, Compose, or the IME
 * service: construct it with fakes for its three dependencies and call
 * [analyze] directly.
 *
 * [com.keylingo.app.data.hint.DefaultHintProvider] is a thin
 * [com.keylingo.app.domain.repository.HintProvider] adapter over this
 * class; the interface the rest of the app depends on hasn't changed
 * shape because of that split.
 *
 * ### Pipeline
 * 1. Tokenize via [SourceLanguageRules.tokenize] (punctuation/whitespace
 *    excluded by construction) and normalize each token via
 *    [SourceLanguageRules.normalize] -- this also makes phrase matching
 *    (step 2) case-insensitive and whitespace-width-insensitive for free,
 *    since it compares normalized tokens, never raw text.
 * 2. Walk the tokens left to right. At every position, try
 *    [PhraseMatcher.findLongestMatch] *first* -- chunk sets are ordered
 *    longest-first, so the first match found is the longest valid one.
 *    A match consumes all its tokens, becomes one phrase-level candidate,
 *    and can set a "pending form" (e.g. "going to" implies the very next
 *    content word is an infinitive).
 * 3. If no chunk matches, the single word is resolved against the
 *    dictionary (with [SourceLanguageRules.inflectionCandidates] as
 *    fallback). Its grammatical form is decided in priority order: an
 *    explicit pending form from a preceding chunk, then
 *    [SourceLanguageRules.surfaceFormSignal] (e.g. English "-ing"), then
 *    [SourceLanguageRules.formImpliedByPrecedingWord] (e.g. English bare
 *    "to"), then a bare/ambiguous fallback -- each tier has its own
 *    honest confidence score.
 * 4. Every accepted candidate (chunk or word) is scored via
 *    [LearningModePolicy] and trimmed to the active [LearningMode]'s cap,
 *    then put back in original sentence order for display.
 */
@Singleton
class SentenceAnalyzer @Inject constructor(
    private val dictionaryProvider: DictionaryProvider,
    private val sourceLanguageRules: SourceLanguageRules,
    private val phraseMatcher: PhraseMatcher
) {

    /** One tokenized word: its position, raw display text, and normalized form. */
    private data class Token(val index: Int, val raw: String, val normalized: String)

    /** An accepted hint candidate, before mode-based trimming. Shared shape for both single words and matched phrase chunks so they can be scored/trimmed uniformly. */
    private sealed class Candidate(
        val startIndex: Int,
        val posWeight: Int,
        val frequencyRank: Int,
        val cefrLevel: CefrLevel
    ) {
        abstract fun toHintWord(): HintWord

        class Word(
            val token: Token,
            val entry: DictionaryEntry,
            val form: GrammaticalForm,
            val confidence: Float
        ) : Candidate(
            startIndex = token.index,
            posWeight = LearningModePolicy.posImportance(entry.partOfSpeech),
            frequencyRank = entry.frequencyRank,
            cefrLevel = entry.cefrLevel
        ) {
            override fun toHintWord(): HintWord {
                val label = if (entry.partOfSpeech == PartOfSpeech.VERB) {
                    form.displayLabel()
                } else {
                    entry.partOfSpeech.name.lowercase()
                }
                return HintWord(
                    originalWord = token.raw,
                    translatedWord = entry.translationFor(form),
                    partOfSpeech = entry.partOfSpeech,
                    formLabel = label,
                    dictionaryEntryId = entry.id,
                    confidence = confidence
                )
            }
        }

        class Phrase(
            startIndex: Int,
            val originalText: String,
            val chunk: PhraseChunk
        ) : Candidate(
            startIndex = startIndex,
            posWeight = LearningModePolicy.PHRASE_CHUNK_POS_WEIGHT,
            // Phrase chunks are treated as common/foundational by
            // convention -- they're curated short patterns a learner
            // meets early, not rare vocabulary. This is what lets Advanced
            // mode naturally prefer a genuinely rare word elsewhere in the
            // same sentence over a chunk hint.
            frequencyRank = 0,
            cefrLevel = CefrLevel.A1
        ) {
            override fun toHintWord(): HintWord = HintWord(
                originalWord = originalText,
                translatedWord = chunk.targetPhrase,
                partOfSpeech = PartOfSpeech.OTHER,
                formLabel = "phrase",
                dictionaryEntryId = null,
                confidence = ResolutionConfidence.PHRASE_CHUNK_MATCH
            )
        }
    }

    suspend fun analyze(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
        learningMode: LearningMode,
        maxHints: Int?
    ): List<HintWord> {
        val tokens = sourceLanguageRules.tokenize(text).mapIndexed { index, raw ->
            Token(index = index, raw = raw, normalized = sourceLanguageRules.normalize(raw))
        }
        if (tokens.isEmpty()) return emptyList()

        val normalizedTokens = tokens.map { it.normalized }
        val candidates = mutableListOf<Candidate>()
        var pendingForm: GrammaticalForm? = null
        var i = 0

        while (i < tokens.size) {
            val chunkMatch = phraseMatcher.findLongestMatch(normalizedTokens, i, sourceLanguage, targetLanguage)
            if (chunkMatch != null) {
                val originalText = tokens.subList(i, i + chunkMatch.wordCount).joinToString(" ") { it.raw }
                candidates.add(Candidate.Phrase(startIndex = i, originalText = originalText, chunk = chunkMatch))
                pendingForm = chunkMatch.requiredNextForm
                i += chunkMatch.wordCount
                continue
            }

            val token = tokens[i]
            if (token.normalized.length < 2 || token.normalized in sourceLanguageRules.stopWords) {
                // Stopwords don't consume a pending form -- a chunk's
                // "next word should be infinitive" flag should still
                // reach the next real content word even if a filler word
                // sits in between.
                i++
                continue
            }

            val candidate = resolveWordCandidate(token, tokens, pendingForm, sourceLanguage, targetLanguage)
            pendingForm = null
            if (candidate != null) candidates.add(candidate)
            i++
        }

        val cap = maxHints ?: LearningModePolicy.defaultMaxHints(learningMode)
        val survivors = if (candidates.size <= cap) {
            candidates
        } else {
            candidates
                .sortedByDescending { LearningModePolicy.keepScore(learningMode, it.posWeight, it.frequencyRank, it.cefrLevel) }
                .take(cap)
                .sortedBy { it.startIndex }
        }

        return survivors.map { it.toHintWord() }
    }

    /**
     * Resolves one non-chunk word against the dictionary, trying an exact
     * match first and then [SourceLanguageRules.inflectionCandidates] so
     * common inflections still resolve without a full lemmatizer. Returns
     * null if nothing matches even after those fallbacks -- unknown words
     * are skipped, never guessed at.
     */
    private suspend fun resolveWordCandidate(
        token: Token,
        allTokens: List<Token>,
        pendingForm: GrammaticalForm?,
        sourceLanguage: String,
        targetLanguage: String
    ): Candidate.Word? {
        dictionaryProvider.lookup(token.normalized, sourceLanguage, targetLanguage)?.let { entry ->
            return resolveForm(token, allTokens, entry, matchedExactly = true, pendingForm = pendingForm)
        }

        for (candidateWord in sourceLanguageRules.inflectionCandidates(token.normalized)) {
            dictionaryProvider.lookup(candidateWord, sourceLanguage, targetLanguage)?.let { entry ->
                return resolveForm(token, allTokens, entry, matchedExactly = false, pendingForm = pendingForm)
            }
        }

        return null
    }

    /**
     * Decides [GrammaticalForm] and confidence for an already-resolved
     * dictionary entry. See the class KDoc's "Pipeline" step 3 for the
     * priority order; this is where it's implemented.
     */
    private fun resolveForm(
        token: Token,
        allTokens: List<Token>,
        entry: DictionaryEntry,
        matchedExactly: Boolean,
        pendingForm: GrammaticalForm?
    ): Candidate.Word {
        if (entry.partOfSpeech != PartOfSpeech.VERB) {
            val confidence = if (matchedExactly) ResolutionConfidence.EXACT_NON_VERB else ResolutionConfidence.STEMMED_NON_VERB
            return Candidate.Word(token, entry, GrammaticalForm.BASE, confidence)
        }

        // Verbs: pick the surface form the sentence actually implies, in
        // priority order -- an explicit chunk-derived form outranks a
        // local morphological guess, which outranks a bare fallback.
        if (pendingForm != null) {
            return Candidate.Word(token, entry, pendingForm, ResolutionConfidence.CHUNK_IMPLIED_FORM)
        }

        sourceLanguageRules.surfaceFormSignal(token.normalized)?.let { form ->
            return Candidate.Word(token, entry, form, ResolutionConfidence.SURFACE_SIGNAL)
        }

        val previousNormalized = allTokens.getOrNull(token.index - 1)?.normalized
        sourceLanguageRules.formImpliedByPrecedingWord(previousNormalized)?.let { form ->
            return Candidate.Word(token, entry, form, ResolutionConfidence.PRECEDING_WORD_SIGNAL)
        }

        return if (matchedExactly) {
            // Bare lemma, no chunk context, no local signal: could be a
            // present-tense verb use ("I work") or, for words that share
            // spelling across roles (e.g. "work"), a noun-like use ("I
            // have work"). Reflect that genuine ambiguity honestly with a
            // middling confidence rather than pretending certainty.
            Candidate.Word(token, entry, GrammaticalForm.BASE, ResolutionConfidence.AMBIGUOUS_BARE_VERB)
        } else {
            // Resolved via a suffix-stripped fallback (plural "-s",
            // past-tense "-ed", etc.) but this schema only models
            // base/infinitive/gerund -- no distinct past-tense or
            // third-person form -- so the shown translation is an
            // imperfect fit for the actual tense used. Lower confidence
            // reflects that mismatch.
            Candidate.Word(token, entry, GrammaticalForm.BASE, ResolutionConfidence.STEMMED_VERB_FALLBACK)
        }
    }
}

/**
 * Named confidence constants for each resolution path, instead of magic
 * numbers scattered through [SentenceAnalyzer]. Values are hand-tuned
 * heuristics reflecting how strong each signal actually is -- documented
 * here so future confidence-ranking work has a single place to revisit
 * them.
 */
private object ResolutionConfidence {
    const val EXACT_NON_VERB = 0.9f
    const val STEMMED_NON_VERB = 0.7f
    const val CHUNK_IMPLIED_FORM = 0.85f
    const val SURFACE_SIGNAL = 0.75f
    const val PRECEDING_WORD_SIGNAL = 0.8f
    const val AMBIGUOUS_BARE_VERB = 0.55f
    const val STEMMED_VERB_FALLBACK = 0.4f
    const val PHRASE_CHUNK_MATCH = 0.95f
}
