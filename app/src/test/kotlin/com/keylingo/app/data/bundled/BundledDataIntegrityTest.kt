package com.keylingo.app.data.bundled

import com.keylingo.app.data.language.spanish.SpanishConjugator
import com.keylingo.app.domain.model.PartOfSpeech
import com.keylingo.app.testing.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the shipped data against the classes of defect found in the audit:
 * stripped accents, inflected rows posing as lemmas, missing gender,
 * duplicate senses, and a seeder/format mismatch silently dropping rows.
 */
class BundledDataIntegrityTest {
    private val rows = TestData.assetLines(BundledLanguageData.DICTIONARY).filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }
    private val dictionary = TestData.bundled.dictionary

    @Test fun everyRowParsesAndNothingIsSilentlyDropped() {
        assertTrue(rows.all { it.size >= 3 && it[0].isNotBlank() && it[1].isNotBlank() })
        assertEquals(rows.size, dictionary.size)
        assertTrue("dictionary unexpectedly small: ${dictionary.size}", dictionary.size > 1000)
    }

    @Test fun eachEnglishLemmaHasAtMostOneSensePerPartOfSpeech() {
        val dupes = rows.groupBy { it[0] to it[2] }.filter { it.value.size > 1 }.keys
        assertTrue("duplicate senses: $dupes", dupes.isEmpty())
    }

    @Test fun verbsAreInfinitivesAndConjugate() {
        val verbs = dictionary.entries.filter { it.partOfSpeech == PartOfSpeech.VERB }
        val bad = verbs.filter { e ->
            val head = SpanishConjugator.parse(e.baseTranslation).infinitive
            !(head.endsWith("ar") || head.endsWith("er") || head.endsWith("ir") || head.endsWith("ír"))
        }
        assertTrue("non-infinitive verb rows: ${bad.map { it.sourceWord to it.baseTranslation }}", bad.isEmpty())
        // Every verb must produce a full paradigm without throwing.
        verbs.forEach { SpanishConjugator.allForms(it.baseTranslation) }
    }

    @Test fun nounsCarryGrammaticalGender() {
        val nouns = dictionary.entries.filter { it.partOfSpeech == PartOfSpeech.NOUN }
        assertTrue(nouns.all { it.gender in setOf("m", "f", "mf") })
    }

    @Test fun spanishSpellingKeepsAccentsAndEnye() {
        val words = rows.flatMap { it[1].split(' ') }.map { it.lowercase() }.toSet()
        // Endings that are always written with an accent.
        val missingAccent = words.filter { it.endsWith("cion") || it.endsWith("sion") }
        assertTrue("unaccented -ción/-sión: $missingAccent", missingAccent.isEmpty())
        // Accent-stripped forms that the legacy data contained; several are different (or offensive) words.
        val stripped = setOf("ano", "nino", "manana", "tambien", "despues", "aqui", "facil", "dificil", "rapido", "musica",
            "telefono", "pelicula", "arbol", "lapiz", "cafe", "bebe", "pais", "cancion", "corazon", "medico", "numero", "sabado", "miercoles")
        assertTrue("accent-stripped words present: ${words intersect stripped}", (words intersect stripped).isEmpty())
        listOf("año", "niño", "mañana", "también", "después", "aquí", "fácil", "teléfono", "corazón", "sábado").forEach {
            assertTrue("missing $it", it in words)
        }
    }

    @Test fun englishLexiconIsCleanAndRanked() {
        val lines = TestData.assetLines(BundledLanguageData.ENGLISH_LEXICON).filter { !it.startsWith("#") }
        val keys = lines.map { it.substringBefore('\t').lowercase() }
        assertEquals("duplicate words", keys.size, keys.toSet().size)
        assertTrue(lines.all { it.split('\t')[1].toIntOrNull()?.let { r -> r > 0 } == true })
        assertEquals(listOf("I", "you", "the"), lines.take(3).map { it.substringBefore('\t') })
        // Profanity is recognised but never proactively suggested.
        val flags = lines.associate { it.substringBefore('\t').lowercase() to it.split('\t').getOrElse(2) { "" } }
        assertEquals("n", flags["fuck"])
    }

    @Test fun coldStartTablesOnlyPredictKnownWords() {
        val data = TestData.bundled
        val unknown = TestData.assetLines(BundledLanguageData.EN_NEXT_WORDS).filter { !it.startsWith("#") && it.isNotBlank() }
            .flatMap { it.split('\t').drop(1) }.filter { !data.englishLexicon.contains(it) }.toSet()
        assertTrue("predictions not in lexicon: $unknown", unknown.isEmpty())
    }
}
