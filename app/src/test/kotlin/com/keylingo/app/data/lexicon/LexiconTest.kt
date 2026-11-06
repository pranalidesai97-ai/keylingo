package com.keylingo.app.data.lexicon

import com.keylingo.app.testing.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LexiconTest {
    private val en = TestData.bundled.englishLexicon
    private val es = TestData.bundled.spanishLexicon

    @Test fun englishLexiconIsProductionSized() {
        assertTrue("size ${en.size}", en.size > 50_000)
        listOf("hello", "because", "tomorrow", "beautiful", "restaurant", "email", "I'm", "don't", "Monday").forEach {
            assertTrue("missing $it", en.contains(it))
        }
    }

    @Test fun prefixSearchIsApostropheAndCaseInsensitive() {
        assertEquals(listOf("don't"), en.findByKey("dont").map { it.display })
        assertTrue(en.prefixMatches("im", 5).any { it.display == "I'm" })
        assertTrue(en.prefixMatches("MON", 5).any { it.display == "Monday" })
    }

    @Test fun spanishLexiconIsAccentInsensitiveButKeepsEnye() {
        assertTrue(es.findByKey("tambien").any { it.display == "también" })
        assertTrue(es.findByKey("ano").any { it.display == "año" })
        assertTrue(es.prefixMatches("trabaj", 10).map { it.display }.containsAll(listOf("trabajar", "trabajo")))
        // Generated conjugations are present with accents.
        listOf("trabajé", "comió", "estás", "fuimos", "tenía", "hablarán").forEach { assertTrue("missing $it", es.contains(it)) }
    }

    @Test fun neighbourKeysAreCheaperThanDistantKeys() {
        assertTrue(TypoDistance.distance("tge", "the") < TypoDistance.distance("tpe", "the"))
        assertTrue(TypoDistance.distance("teh", "the") < 1.0)
        assertTrue(TypoDistance.distance("helllo", "hello") < 0.5)
    }

    @Test fun correctionSearchIsFastEnoughForEveryKeystroke() {
        val words = listOf("teh", "becuase", "recieve", "definately", "tommorow", "freind", "wierd", "thnaks", "adress", "begining")
        en.corrections("warmup", 3, true)
        val start = System.nanoTime()
        repeat(5) { for (w in words) en.corrections(w, 3, true) }
        val perCallMs = (System.nanoTime() - start) / 1_000_000.0 / (5 * words.size)
        println("TYPO_SCAN_MS_PER_CALL=$perCallMs")
        // Desktop JVM budget; a mid-range phone is ~3-5x slower and still well inside a 50 ms keystroke budget.
        assertTrue("corrections took $perCallMs ms per call", perCallMs < 15.0)
    }
}
