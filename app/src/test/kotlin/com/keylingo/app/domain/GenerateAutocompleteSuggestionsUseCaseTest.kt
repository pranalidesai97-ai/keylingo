package com.keylingo.app.domain

import com.keylingo.app.domain.model.SuggestionKind
import com.keylingo.app.domain.usecase.GenerateAutocompleteSuggestionsUseCase
import com.keylingo.app.domain.usecase.SuggestionRequest
import com.keylingo.app.testing.FakeContextPrediction
import com.keylingo.app.testing.FakeLearnedVocabulary
import com.keylingo.app.testing.realAutocompleteProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Suggestion ranking, autocorrect and prediction, against the real bundled lexicons. */
class GenerateAutocompleteSuggestionsUseCaseTest {

    private val learned = FakeLearnedVocabulary()
    private val context = FakeContextPrediction()
    private val useCase = GenerateAutocompleteSuggestionsUseCase(realAutocompleteProvider(), learned, context)

    private suspend fun suggest(prefix: String, lang: String = "en", previous: String? = null, second: String? = null, rejected: Set<String> = emptySet()) =
        useCase(SuggestionRequest(prefix, previous, second, lang, maxSuggestions = 5, rejectedCorrections = rejected))

    private suspend fun autocorrectOf(word: String, lang: String = "en") = suggest(word, lang).autoCorrection?.word

    @Test fun completesCommonWordsMostFrequentFirst() = runTest {
        val words = suggest("hel").suggestions.map { it.word }
        assertTrue(words.toString(), "hello" in words && "help" in words)
        assertEquals("the", suggest("th").suggestions.first().word)
        assertTrue(suggest("beca").suggestions.map { it.word }.contains("because"))
    }

    @Test fun preservesTheCaseTheUserTyped() = runTest {
        assertTrue(suggest("Hel").suggestions.map { it.word }.contains("Hello"))
        assertTrue(suggest("HEL").suggestions.map { it.word }.contains("HELLO"))
    }

    @Test fun autocorrectsCommonKeyboardTypos() = runTest {
        val cases = mapOf(
            "teh" to "the", "becuase" to "because", "recieve" to "receive", "wierd" to "weird",
            "definately" to "definitely", "tommorow" to "tomorrow", "freind" to "friend", "thnaks" to "thanks",
        )
        val wrong = cases.mapNotNull { (typo, fix) -> autocorrectOf(typo).takeIf { it != fix }?.let { "$typo -> $it (expected $fix)" } }
        assertTrue(wrong.joinToString(), wrong.isEmpty())
    }

    @Test fun fixesApostrophesAndObligatoryCapitals() = runTest {
        assertEquals("don't", autocorrectOf("dont"))
        assertEquals("I'm", autocorrectOf("im"))
        assertEquals("I", autocorrectOf("i"))
        assertEquals("can't", autocorrectOf("cant"))
        assertEquals("didn't", autocorrectOf("didnt"))
    }

    @Test fun neverAutocorrectsRealWords() = runTest {
        for (word in listOf("form", "were", "its", "ill", "well", "hell", "may", "march", "they", "then", "than", "lol")) {
            assertNull("must not autocorrect real word '$word'", autocorrectOf(word))
        }
    }

    @Test fun leavesNumbersCodesAndMixedCaseAlone() = runTest {
        assertNull(autocorrectOf("teh2"))
        assertNull(autocorrectOf("iPhoen"))
        assertNull(useCase(SuggestionRequest("teh", allowAutoCorrect = false)).autoCorrection)
    }

    @Test fun respectsARejectedCorrection() = runTest {
        assertNull(suggest("teh", rejected = setOf("teh")).autoCorrection)
    }

    @Test fun offersTheLiteralWordWhenAutocorrecting() = runTest {
        val result = suggest("teh")
        assertEquals("the", result.suggestions[0].word)
        assertEquals(SuggestionKind.LITERAL, result.suggestions[1].kind)
        assertEquals("teh", result.suggestions[1].word)
    }

    @Test fun learnedWordsAreSuggestedAndNeverCorrected() = runTest {
        repeat(3) { learned.recordOrganicUsage("zorbix") }
        assertNull(autocorrectOf("zorbix"))
        assertTrue(suggest("zor").suggestions.map { it.word }.contains("zorbix"))
    }

    @Test fun oneTypingIsNotEnoughToLearnAWord() = runTest {
        learned.recordOrganicUsage("qwzx")
        assertFalse(suggest("qw").suggestions.any { it.word == "qwzx" })
    }

    @Test fun spanishAccentsAreRestored() = runTest {
        assertEquals("también", autocorrectOf("tambien", "es"))
        assertEquals("mañana", autocorrectOf("manana", "es"))
        assertEquals("canción", autocorrectOf("cancion", "es"))
        assertTrue(suggest("trabaje", "es").suggestions.map { it.word }.contains("trabajé"))
        // "esta" (this) and "está" (is) are both words: suggest the accent, do not force it.
        val esta = suggest("esta", "es")
        assertNull(esta.autoCorrection)
        assertTrue(esta.suggestions.map { it.word }.contains("está"))
    }

    @Test fun languagesDoNotMix() = runTest {
        val en = suggest("tamb", "en").suggestions.map { it.word }
        assertFalse(en.contains("también"))
        val es = suggest("tamb", "es").suggestions.map { it.word }
        assertTrue(es.toString(), es.contains("también"))
    }

    @Test fun predictsNextWordWithNoHistory() = runTest {
        val after = suggest("", previous = "I").suggestions.map { it.word }
        assertTrue(after.toString(), "am" in after)
        val start = suggest("").suggestions
        assertTrue(start.isNotEmpty())
        assertTrue(start.all { it.kind == SuggestionKind.PREDICTION })
    }

    @Test fun personalHistoryOutranksColdStart() = runTest {
        repeat(3) { context.recordSequence("lets", "zorbix") }
        repeat(2) { learned.recordOrganicUsage("zorbix") }
        assertEquals("zorbix", suggest("", previous = "lets").suggestions.first().word)
    }

    @Test fun trigramsSharpenPrediction() = runTest {
        repeat(2) { context.recordSequence("going", "to", secondPreviousWord = "am") }
        repeat(5) { context.recordSequence("going", "home", secondPreviousWord = "is") }
        assertEquals("to", suggest("", previous = "going", second = "am").suggestions.first().word)
    }

    @Test fun contextReranksButNeverIntroducesNonMatchingWords() = runTest {
        repeat(10) { context.recordSequence("the", "zebra") }
        val words = suggest("ca", previous = "the").suggestions.map { it.word }
        assertFalse(words.contains("zebra"))
    }
}
