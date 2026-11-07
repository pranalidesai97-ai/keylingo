package com.keylingo.app.domain.model

/**
 * Words offered in an empty field (or after a finished sentence) before the
 * bundled prediction data has finished loading, which takes 1-3 s after the
 * keyboard process starts on a phone. Without them the suggestion row stayed
 * blank on a freshly focused field until the data arrived.
 *
 * They are the first five entries of the bundled sentence-start row
 * (`<s>` in assets/prediction/{en,es}_next_words.tsv), so once the data is
 * loaded the computed predictions show the same words and nothing visibly
 * swaps. SentenceStartersTest keeps the two in sync.
 */
object SentenceStarters {
    val ENGLISH: List<String> = listOf("I", "the", "thanks", "hi", "ok")
    val SPANISH: List<String> = listOf("hola", "yo", "no", "sí", "qué")

    fun forLanguage(languageCode: String?): List<String> = if (languageCode == "es") SPANISH else ENGLISH
}
