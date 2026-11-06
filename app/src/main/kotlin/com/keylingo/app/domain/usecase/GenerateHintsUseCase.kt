package com.keylingo.app.domain.usecase

import com.keylingo.app.domain.model.HintWord
import com.keylingo.app.domain.model.LearningMode
import com.keylingo.app.domain.repository.HintProvider
import javax.inject.Inject

/**
 * Single entry point the presentation layer calls to turn "what the user
 * just typed" into "what should the hint bar show". Kept as its own
 * use-case class so the keyboard ViewModel has zero knowledge of
 * tokenization, stopwords, grammar-form detection, or dictionary lookups.
 */
class GenerateHintsUseCase @Inject constructor(
    private val hintProvider: HintProvider
) {
    suspend operator fun invoke(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
        learningMode: LearningMode,
        maxHints: Int? = null
    ): List<HintWord> {
        if (text.isBlank()) return emptyList()
        return hintProvider.generateHints(text, sourceLanguage, targetLanguage, learningMode, maxHints)
    }
}
