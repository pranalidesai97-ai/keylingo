package com.keylingo.app.data.hint

import com.keylingo.app.data.bundled.BundledDataSupplier
import com.keylingo.app.domain.model.LearningMode
import com.keylingo.app.testing.TestData
import com.keylingo.app.testing.inlineDispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behaviour of the production Spanish grammar-note patterns
 * ([SpanishGrammarNotes.RULES], the ones that crashed the keyboard service on
 * Android when they carried `(?U)`). Calls the real [SpanishGrammarNotes.notesFor]
 * and the real [LearningAssistant] path, not a copy of the patterns.
 */
class SpanishGrammarNotesTest {

    private val progressive = "estar + gerund: action in progress (estoy aprendiendo)"

    private fun notes(s: String) = SpanishGrammarNotes.notesFor(s, max = 10)

    @Test fun everyRuleCompilesOnThisRuntime() {
        assertEquals(emptyList<String>(), SpanishGrammarNotes.rejectedPatterns)
        assertEquals(10, SpanishGrammarNotes.RULES.size)
    }

    @Test fun recognisesSpanishProgressiveConstructions() {
        for (s in listOf("estoy trabajando", "estás comiendo", "estamos viviendo", "estaba estudiando")) {
            assertTrue("no progressive note for \"$s\": ${notes(s)}", progressive in notes(s))
        }
    }

    @Test fun accentedFormsCapitalsAndDecomposedAccents() {
        for (s in listOf(
            "Estábamos durmiendo cuando llamaste", // accented auxiliary
            "¿Estás leyendo?",                       // -yendo gerund, inverted question mark
            "ESTOY TRABAJANDO",                      // capitals
            "Están construyendo una casa",           // accent + -yendo
            "estás comiendo",                  // decomposed á (NFD) is normalised
            "Ella está bañando al perro",            // ñ inside the gerund
        )) assertTrue("no progressive note for \"$s\": ${notes(s)}", progressive in notes(s))
    }

    @Test fun punctuationAroundTheConstruction() {
        for (s in listOf("Estoy trabajando.", "¡Estoy trabajando!", "Estoy trabajando, pero luego salgo.", "(estamos viviendo)", "—estaba estudiando—")) {
            assertTrue("no progressive note for \"$s\": ${notes(s)}", progressive in notes(s))
        }
    }

    @Test fun wordBoundariesHoldForAccentedLetters() {
        // "esta" inside "detesta"/"manifestamos" must not count as the auxiliary.
        assertFalse(progressive in notes("detesta trabajando"))
        assertFalse(progressive in notes("manifestamos cantando"))
        // Not a gerund.
        assertFalse(progressive in notes("estoy cansado"))
        assertFalse(progressive in notes("estoy en casa"))
        // A word that merely ends in -ando but is glued to more letters.
        assertFalse(progressive in notes("estoy andandox"))
    }

    @Test fun englishEmptyAndPunctuationOnlyInputGiveNoNotes() {
        for (s in listOf("", "   ", "?!...", "¿¡", "I am working at the station today.", "This is interesting", "We are studying Spanish.", "12345")) {
            assertEquals("unexpected notes for \"$s\"", emptyList<String>(), notes(s))
        }
    }

    @Test fun theOtherRulesStillWork() {
        val cases = mapOf(
            "Voy a llamar a mi mamá" to "ir a + infinitive",
            "Íbamos a comer" to "ir a + infinitive",
            "Me gustan los perros" to "gustar",
            "Tengo que trabajar" to "tener que",
            "Tengo 20 años" to "Spanish uses tener",
            "Tienes razón" to "Spanish uses tener",
            "Hoy hace frío" to "Weather uses hacer",
            "Acabo de comer" to "acabar de",
            "Ayer fui al mercado" to "fui + a",
            "He comido" to "haber + participle",
            "No entiendo" to "Negation",
        )
        for ((s, prefix) in cases) assertTrue("\"$s\" -> ${notes(s)}", notes(s).any { it.startsWith(prefix) })
    }

    @Test fun learningAssistantConstructsAndUsesTheNotes() {
        // The crash happened while constructing LearningAssistant (Hilt). Construct it the same way and run the Spanish path.
        val data = TestData.bundled
        val assistant = LearningAssistant(BundledDataSupplier { data }, inlineDispatchers)
        val result = assistant.analyze(data, "Estoy aprendiendo español.", LearningMode.BEGINNER, null, typingLanguage = "es")
        assertEquals("I am learning Spanish.", result.sentence?.translatedText)
        assertEquals(progressive, result.sentence?.notes?.first())
        // English input never reaches the Spanish notes.
        val english = assistant.analyze(data, "I am working at home", LearningMode.BEGINNER, null, typingLanguage = "en")
        assertTrue(english.sentence?.notes.orEmpty().none { it == progressive })
    }
}
