package com.keylingo.app.data.lexicon

import com.keylingo.app.data.language.EnglishForm
import com.keylingo.app.data.language.EnglishMorphology
import com.keylingo.app.testing.TestData
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reports (stdout: VOCAB ...) and guards the real vocabulary composition, so
 * counts in the documentation come from the shipped data, not estimates.
 */
class VocabularyMetricsTest {
    @Test fun vocabularyComposition() {
        val rows = TestData.assetLines("lexicon/en_words.tsv").filter { !it.startsWith("#") }.map { it.split('\t') }
        val words = rows.map { it[0] }
        val lower = words.map { it.lowercase() }.toSet()
        val suggestable = rows.count { it.getOrElse(2) { "" } != "n" }
        val inflected = lower.count { w -> EnglishMorphology.analyze(w).any { it.form != EnglishForm.BASE && it.lemma != w && it.lemma in lower } }
        val d = TestData.bundled
        println("VOCAB englishUniqueWords=${words.size} suggestable=$suggestable knownOnly=${words.size - suggestable} " +
            "inflectedForms=$inflected baseForms=${words.size - inflected} contractions=${words.count { it.contains('\'') }} " +
            "capitalised=${words.count { it.first().isUpperCase() }} misspellings=${d.englishMisspellings.size}")
        println("VOCAB dictionaryEntries=${d.dictionary.size} englishLemmas=${d.dictionary.entries.map { it.sourceWord }.toSet().size} " +
            "spanishSurfaceForms=${d.spanishLexicon.size} idioms=${d.idioms.size}")
        repeat(2) {
            val t = System.nanoTime()
            com.keylingo.app.data.bundled.BundledLanguageData.load { p -> TestData.assetLines(p).asSequence() }
            println("VOCAB loadMs=${(System.nanoTime() - t) / 1_000_000}")
        }
        assertTrue(words.size > 100_000)
        assertTrue(suggestable > 70_000)
        assertTrue(inflected > 40_000)
    }
}
