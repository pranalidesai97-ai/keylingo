package com.keylingo.app.domain

import com.keylingo.app.domain.usecase.GenerateAutocompleteSuggestionsUseCase
import com.keylingo.app.domain.usecase.SuggestionRequest
import com.keylingo.app.testing.FakeContextPrediction
import com.keylingo.app.testing.FakeLearnedVocabulary
import com.keylingo.app.testing.realAutocompleteProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Per-keystroke suggestion latency with the full bundled lexicons (112k English,
 * 50k+ Spanish words): every prefix of realistic messages, search queries and
 * misspelled text is typed one letter at a time, as the keyboard does, and each
 * refresh is timed. Prints `LATENCY` lines for the report.
 *
 * Desktop-JVM numbers; a mid-range phone is roughly 3-5x slower. The keyboard
 * runs this off the main thread and drops stale results, so the budget is
 * a frame-scale p95 with a wide margin (the assertions catch regressions such
 * as a full-lexicon scan per keystroke, not small fluctuations).
 */
class SuggestionLatencyTest {

    private val useCase = GenerateAutocompleteSuggestionsUseCase(realAutocompleteProvider(), FakeLearnedVocabulary(), FakeContextPrediction())

    private val english = listOf(
        "Hey are you coming to the meeting tomorrow morning", "Thanks so much for your help today I really appreciate it",
        "best restaurants near me open now", "how to reset my password on android", "weather forecast this weekend",
        "I'll call you when I get home", "Can we reschedule our appointment to next Thursday afternoon",
    )
    private val englishTypos = listOf(
        "teh meetign is tommorow at the offcie", "I dont knwo waht you mean becuase its wierd",
        "definately recieve the pakage untill friday", "cna you sned me the adress plz",
    )
    private val spanish = listOf(
        "Hola cómo estás hoy", "Muchas gracias por tu ayuda", "Mañana voy a trabajar en la oficina",
        "restaurantes cerca de mí abiertos ahora", "Nos vemos el fin de semana con la familia",
        "Estoy aprendiendo a cocinar comida mexicana",
    )
    private val spanishTypos = listOf(
        "nesecito aser la tarea para mañana", "grasias por benir a la fiesta", "tambien quiero ir aora mismo",
        "ola que tal estas", "el trabjo esta muy dificil",
    )

    /** Times one refresh per keystroke over [texts]; returns sorted per-call milliseconds. */
    private fun type(texts: List<String>, lang: String): DoubleArray = runBlocking {
        val times = ArrayList<Double>()
        for (text in texts) {
            val words = text.split(' ')
            for ((i, word) in words.withIndex()) {
                val previous = words.getOrNull(i - 1)
                val second = words.getOrNull(i - 2)
                for (end in 0..word.length) {
                    val request = SuggestionRequest(word.substring(0, end), previous, second, lang, maxSuggestions = 5)
                    val t = System.nanoTime()
                    useCase(request)
                    times += (System.nanoTime() - t) / 1_000_000.0
                }
            }
        }
        times.toDoubleArray().also { it.sort() }
    }

    private fun DoubleArray.pct(p: Double) = this[((size - 1) * p).toInt()]

    @Test fun everyKeystrokeRefreshStaysWellUnderAFrame() {
        // Warm-up: class loading, JIT, and the one-time lexicon load are not per-keystroke costs.
        repeat(3) { type(english + englishTypos, "en"); type(spanish + spanishTypos, "es") }
        val runs = listOf(
            "en" to type(english, "en"), "en-typos" to type(englishTypos, "en"),
            "es" to type(spanish, "es"), "es-typos" to type(spanishTypos, "es"),
        )
        for ((name, t) in runs) {
            println("LATENCY $name keystrokes=${t.size} median=%.2fms p95=%.2fms max=%.2fms".format(t.pct(0.5), t.pct(0.95), t.last()))
        }
        for ((name, t) in runs) {
            assertTrue("$name p95 ${t.pct(0.95)} ms", t.pct(0.95) < 20.0)
            assertTrue("$name median ${t.pct(0.5)} ms", t.pct(0.5) < 5.0)
        }
    }
}
