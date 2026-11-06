package com.keylingo.app.domain.repository

import com.keylingo.app.domain.model.HintWord
import com.keylingo.app.domain.model.LearningAssistance
import com.keylingo.app.domain.model.LearningMode

/**
 * Turns the sentence currently being typed into learning assistance: a
 * sentence translation plus per-word/phrase hints, in whichever direction
 * applies (typing English -> Spanish hints; typing Spanish -> English glosses).
 * Never mutates the typed text.
 */
interface HintProvider {

    /**
     * [text] is the current sentence (the caller strips earlier sentences).
     * [learningMode] decides how many word hints survive (see LearningModePolicy);
     * [maxHints] hard-overrides that when non-null.
     */
    suspend fun assist(
        text: String,
        targetLanguage: String,
        learningMode: LearningMode,
        maxHints: Int? = null,
        /** "en"/"es" when the user forced a typing language; null = detect. */
        typingLanguage: String? = null,
        /** Offer grammar corrections (and translate the corrected sentence when there is one). */
        checkGrammar: Boolean = true
    ): LearningAssistance

    /** Convenience for callers that only need word hints. */
    suspend fun generateHints(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
        learningMode: LearningMode,
        maxHints: Int? = null
    ): List<HintWord> = assist(text, targetLanguage, learningMode, maxHints).hints
}
