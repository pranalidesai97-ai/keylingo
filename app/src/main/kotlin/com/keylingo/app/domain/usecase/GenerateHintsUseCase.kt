package com.keylingo.app.domain.usecase

import com.keylingo.app.domain.model.LearningAssistance
import com.keylingo.app.domain.model.LearningMode
import com.keylingo.app.domain.repository.HintProvider
import javax.inject.Inject

/**
 * Single entry point the keyboard calls to turn "the sentence being typed"
 * into learning assistance (sentence translation + word hints). The keyboard
 * has no knowledge of tokenizing, grammar or dictionaries.
 */
class GenerateHintsUseCase @Inject constructor(
    private val hintProvider: HintProvider
) {
    suspend operator fun invoke(
        sentence: String,
        targetLanguage: String,
        learningMode: LearningMode,
        maxHints: Int? = null,
        typingLanguage: String? = null
    ): LearningAssistance {
        if (sentence.isBlank()) return LearningAssistance.EMPTY
        return hintProvider.assist(sentence, targetLanguage, learningMode, maxHints, typingLanguage)
    }
}
