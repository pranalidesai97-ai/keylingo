package com.keylingo.app.domain

import com.keylingo.app.domain.model.SentenceStarters
import com.keylingo.app.domain.usecase.GenerateAutocompleteSuggestionsUseCase
import com.keylingo.app.testing.realAutocompleteProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** The immediate starter words must be exactly what the loaded prediction data shows, so nothing swaps on screen. */
class SentenceStartersTest {
    @Test fun startersMatchTheBundledSentenceStartRow() = runBlocking {
        val provider = realAutocompleteProvider()
        val start = GenerateAutocompleteSuggestionsUseCase.SENTENCE_START
        assertEquals(provider.seedNextWords(start, "en").take(5), SentenceStarters.ENGLISH)
        assertEquals(provider.seedNextWords(start, "es").take(5), SentenceStarters.SPANISH)
    }
}
