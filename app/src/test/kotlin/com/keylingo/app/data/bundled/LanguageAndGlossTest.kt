package com.keylingo.app.data.bundled

import com.keylingo.app.domain.model.LearningMode
import com.keylingo.app.testing.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageAndGlossTest {
    private val data = TestData.bundled

    @Test fun detectsTheTypingLanguage() {
        val detector = data.languageDetector
        listOf("I like pizza", "how are you", "hola, how are you doing", "see you mañana").forEach { assertEquals(it, "en", detector.detect(it)) }
        listOf("yo quiero comer", "¿cómo estás?", "mañana vamos a la playa", "no sé dónde está").forEach { assertEquals(it, "es", detector.detect(it)) }
    }

    @Test fun glossesSpanishConjugationsBackToEnglish() {
        val g = data.glosser
        assertEquals("I want", g.gloss("quiero")?.english)
        assertEquals("we were / we went", g.gloss("fuimos")?.english) // ser and ir share the preterite
        assertEquals("you ate", g.gloss("comiste")?.english)
        assertEquals("también", g.gloss("tambien")?.canonicalSpanish)
        assertTrue(g.gloss("casas")?.english in setOf("houses", "homes")) // not "you marry": casarse needs its pronoun
        assertNull(g.gloss("xyzzy"))
    }

    @Test fun learningAssistantUsesTheSameAnalysisForSentenceAndHints() {
        val assistant = com.keylingo.app.data.hint.LearningAssistant(BundledDataSupplier { data }, com.keylingo.app.testing.inlineDispatchers)
        val en = assistant.analyze(data, "I am going to call you tomorrow", LearningMode.BEGINNER, null)
        assertEquals("Te voy a llamar mañana", en.sentence?.translatedText)
        assertTrue(en.hints.map { it.translatedWord }.containsAll(listOf("llamar", "mañana")))
        val es = assistant.analyze(data, "mañana vamos a la playa", LearningMode.BEGINNER, null)
        assertEquals("es", es.typingLanguage)
        assertEquals("Tomorrow we go to the beach", es.sentence?.translatedText)
        assertEquals("en", es.sentence?.targetLanguage)
        assertNotNull(es.hints.firstOrNull { it.originalWord == "playa" && it.translatedWord == "beach" })
    }

    @Test fun spanishSentenceChipExplainsTheGrammarAndHidesPartialTranslations() {
        val assistant = com.keylingo.app.data.hint.LearningAssistant(BundledDataSupplier { data }, com.keylingo.app.testing.inlineDispatchers)
        val progressive = assistant.analyze(data, "Estoy aprendiendo español.", LearningMode.BEGINNER, null, typingLanguage = "es")
        assertEquals("I am learning Spanish.", progressive.sentence?.translatedText)
        assertTrue(progressive.sentence!!.notes.first().startsWith("estar + gerund"))
        val gustar = assistant.analyze(data, "Me gustan los perros", LearningMode.BEGINNER, null, typingLanguage = "es")
        assertTrue(gustar.sentence!!.notes.any { it.startsWith("gustar") })
        // A word the translator does not know: no sentence chip, but the known words still get hints.
        val partial = assistant.analyze(data, "La casa xyzzyq grande", LearningMode.BEGINNER, null, typingLanguage = "es")
        assertNull(partial.sentence)
        assertTrue(partial.hints.isNotEmpty())
        // A single word gets a word hint, not a sentence chip.
        assertNull(assistant.analyze(data, "Mañana", LearningMode.BEGINNER, null, typingLanguage = "es").sentence)
    }

    @Test fun learningModeControlsHowManyHintsSurvive() {
        val assistant = com.keylingo.app.data.hint.LearningAssistant(BundledDataSupplier { data }, com.keylingo.app.testing.inlineDispatchers)
        val sentence = "My brother and my sister bought a big red car and a new house yesterday"
        val beginner = assistant.analyze(data, sentence, LearningMode.BEGINNER, null).hints.size
        val advanced = assistant.analyze(data, sentence, LearningMode.ADVANCED, null).hints.size
        assertTrue("beginner=$beginner advanced=$advanced", beginner > advanced && advanced <= 3)
    }

    @Test fun detectionStaysCorrectWithTheLargerSpanishVocabulary() {
        // English sentences full of words that are also Spanish ("video", "final", "real", "general", "no", "me").
        for (en in listOf("I saw the final video", "the general idea is real", "no way, call me later", "send me the total cost", "I have a meeting at noon"))
            assertEquals("\"$en\"", "en", data.languageDetector.detect(en))
        for (es in listOf("vamos a la playa", "no sé qué hacer", "el video final es muy bueno", "tengo una reunión mañana", "entonces nos vemos luego"))
            assertEquals("\"$es\"", "es", data.languageDetector.detect(es))
    }
}
