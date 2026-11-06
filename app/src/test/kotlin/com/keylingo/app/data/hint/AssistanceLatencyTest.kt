package com.keylingo.app.data.hint

import com.keylingo.app.data.bundled.BundledDataSupplier
import com.keylingo.app.domain.model.LearningMode
import com.keylingo.app.testing.TestData
import com.keylingo.app.testing.inlineDispatchers
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The learning row is recomputed (debounced) while the user types, so the
 * whole per-sentence pipeline -- grammar check, translation in either
 * direction, hint selection -- must stay cheap. Desktop-JVM budgets; a
 * mid-range phone is roughly 3-5x slower and the work runs off the main
 * thread, so these leave a wide margin under a ~100 ms debounce.
 */
class AssistanceLatencyTest {

    private val data = TestData.bundled
    private val assistant = LearningAssistant(BundledDataSupplier { data }, inlineDispatchers)

    private val english = listOf(
        "I am going to watch a movie with my sister.", "Yesterday I go to the market with my friends.",
        "She goes to the office every day.", "Where are you going?", "I don't understand what you mean.",
        "My brother and my sister bought a big red car and a new house yesterday.",
    )
    private val spanish = listOf(
        "Estoy aprendiendo español.", "Ayer fui al mercado con mis padres.", "¿Cuántos años tiene tu hermano?",
        "Me duele la cabeza.", "Mañana voy a llamar a mi mamá.", "Mi hermano vive en una casa grande en Madrid.",
    )

    private fun msPerCall(inputs: List<String>, block: (String) -> Unit): Double {
        inputs.forEach(block) // warm-up
        val start = System.nanoTime()
        repeat(10) { inputs.forEach(block) }
        return (System.nanoTime() - start) / 1_000_000.0 / (10 * inputs.size)
    }

    @Test fun perSentenceWorkFitsTheTypingBudget() {
        val esToEn = msPerCall(spanish) { data.spanishTranslator.translate(it) }
        val enToEs = msPerCall(english) { data.translator.translate(it) }
        val grammar = msPerCall(english) { data.grammarChecker.check(it, complete = true) }
        val full = msPerCall(english + spanish) { assistant.analyze(data, it, LearningMode.BEGINNER, null) }
        println("LATENCY_MS es->en=%.2f en->es=%.2f grammar=%.2f fullAnalysis=%.2f".format(esToEn, enToEs, grammar, full))
        assertTrue("es->en $esToEn ms", esToEn < 10.0)
        assertTrue("en->es $enToEs ms", enToEs < 10.0)
        assertTrue("grammar $grammar ms", grammar < 5.0)
        assertTrue("full analysis $full ms", full < 25.0)
    }
}
