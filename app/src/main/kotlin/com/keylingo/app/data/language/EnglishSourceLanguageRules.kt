package com.keylingo.app.data.language

import com.keylingo.app.domain.model.GrammaticalForm
import com.keylingo.app.domain.repository.SourceLanguageRules
import javax.inject.Inject
import javax.inject.Singleton

/**
 * English-as-typed analysis rules. All the English-morphology-specific
 * logic that used to live directly inside `SentenceAnalyzer` (suffix
 * stripping, the "to" infinitive marker) and the stopword list that used
 * to live on `SpanishLanguagePack` (a category error -- stopwords filter
 * the *typed* language, not the language being taught) both live here now.
 * Adding a second source language means writing another small class like
 * this one -- `SentenceAnalyzer` needs no changes.
 */
@Singleton
class EnglishSourceLanguageRules @Inject constructor() : SourceLanguageRules {

    override val languageCode: String = "en"

    private val wordRegex = Regex("[A-Za-z']+")

    override fun tokenize(text: String): List<String> =
        wordRegex.findAll(text).map { it.value }.toList()

    override fun normalize(word: String): String = word.lowercase()

    // Function words we skip when choosing which words are "meaningful"
    // enough to show as hints. Pronouns are intentionally NOT stopwords,
    // since seeing "I" -> "yo" is itself a useful learning hint.
    override val stopWords: Set<String> = setOf(
        "a", "an", "the",
        "of", "in", "on", "at", "by", "for", "with", "about", "against",
        "between", "into", "through", "during", "before", "after", "above",
        "below", "to", "from", "up", "down", "over", "under", "again",
        "further", "then", "once", "and", "but", "or", "nor", "so", "yet",
        "is", "am", "are", "was", "were", "be", "been", "being",
        "do", "does", "did", "have", "has", "had",
        "this", "that", "these", "those",
        "it", "its", "as", "if", "than", "too", "very", "just", "not", "no"
    )

    override fun inflectionCandidates(normalizedWord: String): List<String> {
        val word = normalizedWord
        val candidates = mutableListOf<String>()
        when {
            word.endsWith("ies") && word.length > 4 -> candidates.add(word.dropLast(3) + "y")
            word.endsWith("es") && word.length > 3 -> candidates.add(word.dropLast(2))
            word.endsWith("s") && word.length > 3 -> candidates.add(word.dropLast(1))
        }
        when {
            word.endsWith("ing") && word.length > 5 -> {
                val stem = word.dropLast(3)
                candidates.add(stem)
                candidates.add(stem + "e")
                if (stem.length > 1 && stem.last() == stem[stem.length - 2]) {
                    candidates.add(stem.dropLast(1))
                }
            }
            word.endsWith("ed") && word.length > 4 -> {
                val stem = word.dropLast(2)
                candidates.add(stem)
                candidates.add(stem + "e")
            }
        }
        if (word.endsWith("er") && word.length > 4) candidates.add(word.dropLast(2))
        if (word.endsWith("est") && word.length > 5) candidates.add(word.dropLast(3))
        return candidates
    }

    override fun surfaceFormSignal(normalizedWord: String): GrammaticalForm? {
        return if (normalizedWord.endsWith("ing") && normalizedWord.length > 5) {
            GrammaticalForm.GERUND
        } else {
            null
        }
    }

    override fun formImpliedByPrecedingWord(normalizedPreviousWord: String?): GrammaticalForm? {
        return if (normalizedPreviousWord == "to") GrammaticalForm.INFINITIVE else null
    }
}
