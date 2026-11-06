package com.keylingo.app.data.hint

import com.keylingo.app.domain.model.HintWord
import com.keylingo.app.domain.model.LearningMode
import com.keylingo.app.domain.repository.HintProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin [HintProvider] adapter over [SentenceAnalyzer]. All the actual
 * tokenizing/grammar/scoring logic lives in [SentenceAnalyzer], which is
 * deliberately kept independent of this interface so it stays trivially
 * unit-testable (construct it directly with a fake [com.keylingo.app.domain.repository.DictionaryProvider],
 * no Hilt/Android/HintProvider needed). This class exists purely so the
 * rest of the app can keep depending on the stable [HintProvider]
 * interface without knowing [SentenceAnalyzer] exists.
 */
@Singleton
class DefaultHintProvider @Inject constructor(
    private val sentenceAnalyzer: SentenceAnalyzer
) : HintProvider {

    override suspend fun generateHints(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
        learningMode: LearningMode,
        maxHints: Int?
    ): List<HintWord> {
        return sentenceAnalyzer.analyze(text, sourceLanguage, targetLanguage, learningMode, maxHints)
    }
}
