package com.keylingo.app.domain.repository

import com.keylingo.app.domain.model.HintWord
import com.keylingo.app.domain.model.LearningMode

/**
 * Turns the text currently being typed into a small set of learning hints.
 * The MVP implementation ([com.keylingo.app.data.hint.SentenceAnalyzer], via
 * the thin [com.keylingo.app.data.hint.DefaultHintProvider] adapter) is a
 * tokenizer + local-grammar-signal detector over [DictionaryProvider]; a
 * future implementation could swap in on-device NLP or full phrase-chunk
 * recognition without the keyboard code needing to change.
 */
interface HintProvider {

    /**
     * Given the full current text of the input field (or the active
     * sentence/segment of it), return an ordered list of hints for the
     * "meaningful" words only. Never mutates or returns the typed text.
     *
     * [learningMode] shapes *how many* hints come back and *which*
     * candidates survive when a sentence has more of them than fit
     * (see [com.keylingo.app.data.hint.LearningModePolicy]) -- it's a
     * required parameter, not a default, so no call site can silently
     * ignore the user's chosen mode.
     *
     * [maxHints], if provided, hard-overrides the mode's own default hint
     * count; leave it null to let [learningMode] decide.
     */
    suspend fun generateHints(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
        learningMode: LearningMode,
        maxHints: Int? = null
    ): List<HintWord>
}
