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

/**
 * Suggestion-strip quality on realistic input, against the real bundled lexicons
 * (English ranked by wordfreq, Spanish from wordfreq + Hunspell): sentences,
 * search queries, short and long words, misspellings in both languages.
 * Each case comes from a defect seen while auditing the strip.
 */
class SuggestionQualityTest {

    private val useCase = GenerateAutocompleteSuggestionsUseCase(realAutocompleteProvider(), FakeLearnedVocabulary(), FakeContextPrediction())

    private suspend fun strip(prefix: String, lang: String = "en", previous: String? = null) =
        useCase(SuggestionRequest(prefix, previous, null, lang, maxSuggestions = 5))

    private suspend fun words(prefix: String, lang: String = "en", previous: String? = null) = strip(prefix, lang, previous).suggestions.map { it.word }

    @Test fun commonWordsLeadOverRareOnesInSearchQueriesAndMessages() = runTest {
        // Previously ranked by dictionary tier: "sta" -> stain, "inf" -> inferior, "vid" -> visit before video.
        assertEquals("state", words("sta").first())
        assertTrue(words("inf").toString(), words("inf").indexOf("information") in 0..1)
        assertEquals("video", words("vid").first())
        assertEquals("system", words("sys").first())
        assertTrue(words("res").toString(), "research" in words("res"))
        assertEquals("tomorrow", words("tom", previous = "weather").first())
        assertTrue(words("hel").containsAll(listOf("hello", "help")))
        assertEquals("going", words("goi", previous = "I'm").first())
    }

    @Test fun oneSlipTwoLetterWordsDoNotPushOutWhatTheUserIsTyping() = runTest {
        // "pr" used to show "or" first, "se" -> "we", "me gu" -> "tu".
        assertEquals("probably", words("pr").first())
        assertEquals("see", words("se").first())
        assertEquals("gusta", words("gu", "es", previous = "me").first())
        assertFalse("ma -> la", "la" in words("ma", "es"))
        assertEquals("know", words("kn").first())
    }

    @Test fun distantLookalikesAreNotOffered() = runTest {
        assertEquals(listOf("because", "becuz"), words("becuz")) // not begun, nexus, nevus
        assertTrue(words("xq", "es").none { it.length == 2 })      // not da, ca, fa, za: both letters replaced
        assertTrue(words("hwo").take(2).containsAll(listOf("how", "who")))
    }

    @Test fun theTypedWordIsNotEchoedUnlessItUndoesAnAutocorrection() = runTest {
        for (w in listOf("go", "were", "its", "tu", "si", "porque")) {
            val lang = if (w in setOf("tu", "si", "porque")) "es" else "en"
            assertFalse("$w echoed: ${words(w, lang)}", w in words(w, lang))
        }
        // With an autocorrection pending, what was typed stays available as the second choice.
        val r = strip("teh")
        assertEquals("the", r.autoCorrection?.word)
        assertEquals(SuggestionKind.LITERAL, r.suggestions[1].kind)
        // No duplicates, whatever the case or accents.
        for (p in listOf("Hel", "HEL", "tambien", "esta", "si", "dont")) {
            val lang = if (p in setOf("tambien", "esta", "si")) "es" else "en"
            val list = words(p, lang)
            assertEquals("$p: $list", list.size, list.toSet().size)
        }
    }

    @Test fun commonEnglishTyposAreFixed() = runTest {
        val cases = mapOf("todya" to "today", "wjat" to "what", "thsi" to "this", "taht" to "that", "yuo" to "you",
            "knwo" to "know", "cna" to "can", "becuz" to "because", "plz" to "please", "im" to "I'm")
        val wrong = cases.mapNotNull { (typo, fix) -> strip(typo).autoCorrection?.word.takeIf { it != fix }?.let { "$typo -> $it (expected $fix)" } }
        assertTrue(wrong.joinToString(), wrong.isEmpty())
    }

    @Test fun spanishSpellingByEarIsUnderstood() = runTest {
        // s/c/z, b/v, silent h: the most common Spanish misspellings, not keyboard slips.
        assertEquals("necesito", strip("nesecito", "es").autoCorrection?.word)
        assertEquals("gracias", strip("grasias", "es").autoCorrection?.word)
        assertEquals("trabajo", strip("trabjo", "es").autoCorrection?.word)
        assertEquals("haber", strip("aber", "es").autoCorrection?.word ?: words("aber", "es").first())
        assertTrue(words("bien", "es").none { it == "vien" })
        assertTrue("hola offered for ola: ${words("ola", "es")}", "hola" in words("ola", "es"))
        // "ola" (wave) is a real word: offered an alternative, never silently replaced.
        assertNull(strip("ola", "es").autoCorrection)
        assertTrue("vivir for bibir: ${words("bibir", "es")}", "vivir" in words("bibir", "es"))
    }

    @Test fun spanishAccentsAreRestoredAndNeverInventedOnRealWords() = runTest {
        assertEquals("también", strip("tambien", "es").autoCorrection?.word)
        assertEquals("aquí", strip("aqui", "es").autoCorrection?.word)
        assertEquals("mañana", strip("manana", "es").autoCorrection?.word)
        assertEquals("días", strip("dias", "es").autoCorrection?.word)
        // esta/está, si/sí, tu/tú, estas/estás are all real words: offer the accented form, do not force it.
        for (w in listOf("esta", "si", "tu", "estas", "porque")) assertNull(w, strip(w, "es").autoCorrection)
        assertTrue(words("esta", "es").contains("está"))
    }

    @Test fun spanishNextWordPredictionCoversEverydayFunctionWords() = runTest {
        val expected = mapOf(
            "para" to "que", "con" to "mi", "en" to "la", "muy" to "bien", "mi" to "casa", "se" to "puede",
            "lo" to "que", "voy" to "a", "tengo" to "que", "por" to "favor", "buenos" to "días", "muchas" to "gracias",
        )
        val wrong = expected.mapNotNull { (prev, next) -> words("", "es", prev).takeIf { next !in it }?.let { "$prev -> $it" } }
        assertTrue(wrong.joinToString(), wrong.isEmpty())
        assertTrue(words("", "es", "estoy").containsAll(listOf("bien", "en")))
    }
}
