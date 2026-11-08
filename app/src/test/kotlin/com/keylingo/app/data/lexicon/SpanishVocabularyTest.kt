package com.keylingo.app.data.lexicon

import com.keylingo.app.domain.usecase.GenerateAutocompleteSuggestionsUseCase
import com.keylingo.app.domain.usecase.SuggestionRequest
import com.keylingo.app.testing.FakeContextPrediction
import com.keylingo.app.testing.FakeLearnedVocabulary
import com.keylingo.app.testing.TestData
import com.keylingo.app.testing.realAutocompleteProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Spanish typing vocabulary (assets/lexicon/es_words.tsv: wordfreq frequency order, Hunspell-validated
 * spellings) merged with the generated conjugations. Before this list, Spanish autocomplete knew 67% of the
 * 1,000 most frequent Spanish words and missed basics like "así", "entonces", "según".
 */
class SpanishVocabularyTest {
    private val es = TestData.bundled.spanishLexicon
    private val suggest = GenerateAutocompleteSuggestionsUseCase(realAutocompleteProvider(), FakeLearnedVocabulary(), FakeContextPrediction())
    private fun words(prefix: String, previous: String? = null) =
        runBlocking { suggest(SuggestionRequest(prefix, previous, languageCode = "es"), 0) }.suggestions.map { it.word }

    private val everyday = listOf(
        // connectors, adverbs, pronouns, prepositions
        "así", "tan", "cada", "sea", "solo", "toda", "todas", "luego", "pues", "mientras", "entonces", "según", "cualquier", "sino",
        "ahí", "dentro", "aún", "también", "tampoco", "ojalá", "quizás", "además", "aunque", "después", "antes", "siempre", "nunca",
        "nosotros", "ustedes", "ellos", "conmigo", "contigo", "hacia", "hasta", "desde", "sobre", "entre", "durante", "sin",
        // conversation
        "hola", "gracias", "perdón", "vale", "claro", "bueno", "genial", "perfecto", "adiós", "mañana", "noche", "semana",
        // work and everyday nouns
        "trabajo", "reunión", "empresa", "correo", "llamada", "proyecto", "cliente", "oficina", "dinero", "precio", "tienda",
        "casa", "familia", "amigo", "comida", "agua", "teléfono", "ciudad", "país", "año", "día", "hora", "momento",
        // verbs and conjugations (accents included)
        "estoy", "estás", "está", "estábamos", "puedo", "puedes", "quiero", "tengo", "tienes", "hice", "hizo", "fui", "fue",
        "vamos", "voy", "hablar", "hablé", "habló", "comí", "comió", "sabía", "podría", "tendré", "será", "dímelo",
        "niño", "niña", "español", "año", "señor", "mañana", "corazón", "canción", "inglés", "también", "qué", "cómo",
    )

    @Test fun everydaySpanishWordsAreKnownAndSuggestable() {
        val missing = everyday.filter { es.find(it)?.suggestable != true }
        assertEquals("missing or not suggestable: $missing", emptyList<String>(), missing)
        assertTrue("lexicon size ${es.size}", es.size >= 50_000)
    }

    @Test fun commonWordsOutrankRareOnes() {
        fun rank(w: String) = es.find(w)!!.rank
        assertTrue(rank("que") < rank("entonces"))
        assertTrue(rank("entonces") < rank("ojalá"))
        assertTrue(rank("trabajo") < rank("trabajador"))
        assertTrue(rank("casa") < rank("casamiento"))
        assertTrue(rank("gracias") < rank("agradecimiento"))
        assertTrue(rank("también") < rank("tambor"))
    }

    @Test fun spanishCompletionsAccentsAndTypos() {
        assertEquals("también", words("tambi").first())
        assertTrue(words("ent").toString(), words("ent").take(3).any { it in setOf("entonces", "entre") })
        assertTrue(words("aho").toString(), "ahora" in words("aho").take(3))
        assertTrue(words("graci").toString(), "gracias" in words("graci").take(2))
        assertTrue(words("segu").toString(), words("segu").take(5).any { it in setOf("según", "seguro", "seguir") })
        // Accent restoration and keyboard typos.
        assertTrue(words("tambien").toString(), "también" in words("tambien").take(2))
        assertTrue(words("gracais").toString(), "gracias" in words("gracais").take(3))
        assertTrue(words("entoces").toString(), "entonces" in words("entoces").take(3))
    }

    @Test fun profanityIsRecognisedButNeverSuggested() {
        assertTrue(es.find("mierda") != null)
        assertTrue(words("mier").none { it == "mierda" })
        assertTrue(words("pu").none { it == "puta" })
    }

    @Test fun properNounsKeepTheirCapital() {
        assertEquals("Madrid", es.find("madrid")?.display)
        assertEquals("México", es.find("méxico")?.display)
    }
}
