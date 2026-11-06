package com.keylingo.app.data.hint

import com.keylingo.app.domain.model.CefrLevel
import com.keylingo.app.domain.model.LearningMode
import com.keylingo.app.domain.model.PartOfSpeech

/**
 * Pure, stateless policy: given a [LearningMode] and a candidate's
 * metadata, how many hints should survive and which ones should win when
 * a sentence has more candidates than fit?
 *
 * Deliberately kept as plain functions on an object, not a class
 * [SentenceAnalyzer] is injected with -- there's no state, no I/O, and no
 * DI dependency here, so a plain pure-function policy is the simplest
 * thing that's still trivially unit-testable and trivially replaceable.
 * Future ranking work (confidence-weighted ranking, CEFR-gated filtering,
 * spaced-repetition "due for review" boosts) means editing [keepScore] or
 * adding a new input to it -- [SentenceAnalyzer] itself doesn't need to
 * change, since it already just asks this object "what survives?".
 */
object LearningModePolicy {

    /** How many hints to show when the caller hasn't hard-overridden it. */
    fun defaultMaxHints(mode: LearningMode): Int = when (mode) {
        LearningMode.BEGINNER -> 8
        LearningMode.INTERMEDIATE -> 5
        LearningMode.ADVANCED -> 3
    }

    /**
     * Relative teaching value of a part of speech, independent of mode:
     * verbs and nouns carry the most sentence meaning, adjectives/adverbs
     * next, pronouns least (per the "prioritize verbs and useful
     * vocabulary over pronouns" requirement). Function words rarely reach
     * this point at all since [SentenceAnalyzer] filters most of them as
     * stopwords first, but a weight is defined for completeness.
     *
     * Public (not private) because [SentenceAnalyzer] also needs a
     * comparable weight for phrase-chunk candidates, which don't carry a
     * [PartOfSpeech] of their own -- see [PHRASE_CHUNK_POS_WEIGHT].
     */
    fun posImportance(pos: PartOfSpeech): Int = when (pos) {
        PartOfSpeech.VERB, PartOfSpeech.NOUN -> 3
        PartOfSpeech.ADJECTIVE, PartOfSpeech.ADVERB -> 2
        PartOfSpeech.PRONOUN -> 1
        PartOfSpeech.ARTICLE, PartOfSpeech.PREPOSITION, PartOfSpeech.CONJUNCTION, PartOfSpeech.OTHER -> 0
    }

    /**
     * Weight used for phrase-chunk hints (e.g. "I am" -> "estoy"), which
     * don't have a dictionary [PartOfSpeech] of their own. Same tier as
     * verbs/nouns: a matched chunk is curated, high-value teaching
     * content, not a filler word.
     */
    const val PHRASE_CHUNK_POS_WEIGHT = 3

    /** Higher = "more advanced/difficult"; used to invert preference for Advanced mode. */
    private fun cefrWeight(level: CefrLevel): Int = when (level) {
        CefrLevel.A1 -> 0
        CefrLevel.A2 -> 1
        CefrLevel.B1 -> 2
        CefrLevel.B2 -> 3
        CefrLevel.C1 -> 4
    }

    /**
     * Higher score = more likely to survive when trimming to [defaultMaxHints].
     * Frequency rank is capped before use so an unranked entry
     * (Int.MAX_VALUE) can't blow out the arithmetic. Takes a plain
     * [posWeight] (rather than a [PartOfSpeech] directly) so both
     * dictionary-word candidates (via [posImportance]) and phrase-chunk
     * candidates (via [PHRASE_CHUNK_POS_WEIGHT]) can share this one
     * scoring path.
     *
     * - Beginner/Intermediate: rank verbs/nouns/chunks highest, then
     *   prefer *common* vocabulary (low frequencyRank) -- matches "focus
     *   on important vocabulary" without excluding easy words, which is
     *   what a learner still building a base needs.
     * - Advanced: same priority tier, but prefers *rare* vocabulary (high
     *   frequencyRank) and adds a CEFR bonus, actively favoring uncommon
     *   or difficult words over ones the learner has almost certainly
     *   already mastered. Phrase chunks are treated as common/A1 by
     *   convention (see [SentenceAnalyzer]), so they naturally rank below
     *   a genuinely rare word elsewhere in the same sentence in this mode.
     */
    fun keepScore(
        mode: LearningMode,
        posWeight: Int,
        frequencyRank: Int,
        cefrLevel: CefrLevel
    ): Double {
        val posScore = posWeight * 1000.0
        val cappedRank = frequencyRank.coerceIn(0, 100_000).toDouble()

        return when (mode) {
            LearningMode.BEGINNER, LearningMode.INTERMEDIATE ->
                posScore - (cappedRank * 0.01)

            LearningMode.ADVANCED ->
                posScore + (cappedRank * 0.01) + (cefrWeight(cefrLevel) * 500.0)
        }
    }
}
