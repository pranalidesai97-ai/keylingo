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
        assertNull(es.sentence)
        assertNotNull(es.hints.firstOrNull { it.originalWord == "playa" && it.translatedWord == "beach" })
    }

    @Test fun learningModeControlsHowManyHintsSurvive() {
        val assistant = com.keylingo.app.data.hint.LearningAssistant(BundledDataSupplier { data }, com.keylingo.app.testing.inlineDispatchers)
        val sentence = "My brother and my sister bought a big red car and a new house yesterday"
        val beginner = assistant.analyze(data, sentence, LearningMode.BEGINNER, null).hints.size
        val advanced = assistant.analyze(data, sentence, LearningMode.ADVANCED, null).hints.size
        assertTrue("beginner=$beginner advanced=$advanced", beginner > advanced && advanced <= 3)
    }
}
