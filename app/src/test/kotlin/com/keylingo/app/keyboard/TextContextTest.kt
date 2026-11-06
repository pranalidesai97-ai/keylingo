package com.keylingo.app.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextContextTest {
    @Test fun extractsTheWordAtTheCursorAndItsPredecessors() {
        val ctx = TextContext("Hello. I really lik", " it")
        assertEquals("lik", ctx.currentWord)
        assertEquals(listOf("I", "really"), ctx.previousWords)
        assertEquals("really", ctx.previousWord)
        assertEquals("I", ctx.secondPreviousWord)
        assertEquals("I really lik", ctx.currentSentence)
        assertFalse(ctx.cursorInsideWord)
    }

    @Test fun handlesAccentsApostrophesAndCursorInsideAWord() {
        assertEquals("tambié", TextContext("yo tambié").currentWord)
        assertEquals("don't", TextContext("I don't").currentWord)
        assertTrue(TextContext("hel", "lo").cursorInsideWord)
        assertEquals("", TextContext("word ").currentWord)
    }

    @Test fun sentenceStartRules() {
        assertTrue(TextContext("").atSentenceStart)
        assertTrue(TextContext("Done. ").atSentenceStart)
        assertTrue(TextContext("Line\n").atSentenceStart)
        assertTrue(TextContext("¿Qué? ").atSentenceStart)
        assertFalse(TextContext("Done.").atSentenceStart)
        assertFalse(TextContext("one two ").atSentenceStart)
        assertFalse(TextContext("e.g").atSentenceStart)
    }

    @Test fun graphemeLengths() {
        assertEquals(1, TextContext.lastGraphemeLength("abc"))
        assertEquals(2, TextContext.lastGraphemeLength("ok 😀"))
        assertEquals(4, TextContext.lastGraphemeLength("x 👍🏽"))
        assertEquals("👩‍💻".length, TextContext.lastGraphemeLength("x 👩‍💻"))
        assertEquals(0, TextContext.lastGraphemeLength(""))
    }
}
