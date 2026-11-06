package com.keylingo.app.domain.model

/**
 * A whole-sentence translation of what the user is typing, produced
 * offline by deterministic grammar rules. [confidence] is the share of
 * content words the engine could actually translate (unknown words are
 * left as typed), so the UI can hide low-coverage results instead of
 * showing a misleading partial translation.
 */
data class SentenceTranslation(
    val sourceText: String,
    val translatedText: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val confidence: Float
)

/**
 * Everything the learning row shows for the current sentence: an optional
 * sentence translation, per-word/per-phrase hints, and which language the
 * user is typing in ("en" -> hints are Spanish; "es" -> hints are English glosses).
 */
data class LearningAssistance(
    val sentence: SentenceTranslation?,
    val hints: List<HintWord>,
    val typingLanguage: String
) {
    companion object {
        val EMPTY = LearningAssistance(sentence = null, hints = emptyList(), typingLanguage = "en")
    }
}
