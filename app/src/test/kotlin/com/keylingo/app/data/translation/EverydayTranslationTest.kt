package com.keylingo.app.data.translation

import com.keylingo.app.data.bundled.BundledLanguageData
import com.keylingo.app.testing.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Everyday messages people actually type (work, travel, health, plans), checked
 * as whole sentences: each translation must be correct natural Spanish, not just
 * "covered". Before the frequency audit this corpus averaged 72% coverage with
 * 16/40 sentences fully translated; several fully covered ones were wrong
 * ("Agradece para tu ayuda", "llamada me espalda", "¿Qué el tiempo hace...").
 */
class EverydayTranslationTest {

    private val data = TestData.bundled

    private val corpus = listOf(
        "Can you send me the report before the meeting?" to "¿Me puedes enviar el informe antes de la reunión?",
        "The system is not working again." to "El sistema no está funcionando otra vez.",
        "Please check the data and call me back." to "Por favor revisa los datos y llámame.",
        "What time does the service start tomorrow?" to "¿A qué hora empieza el servicio mañana?",
        "The police closed the road near the school." to "La policía cerró el camino cerca de la escuela.",
        "We are planning a trip to the coast this summer." to "Estamos planeando un viaje a la costa este verano.",
        "My manager wants a new strategy for the project." to "Mi gerente quiere una estrategia nueva para el proyecto.",
        "I have a doctor appointment on Friday morning." to "Tengo una cita de médico el viernes por la mañana.",
        "The price of gas is very high." to "El precio de gas es muy alto.",
        "Can we change the date of the event?" to "¿Podemos cambiar la fecha del evento?",
        "The internet connection is slow today." to "La conexión de internet es lenta hoy.",
        "She is studying chemistry at the university." to "Ella está estudiando química en la universidad.",
        "I lost my phone at the airport." to "Perdí mi teléfono en el aeropuerto.",
        "Our team needs more time to finish the presentation." to "Nuestro equipo necesita más tiempo para terminar la presentación.",
        "The weather is cold and the roads are dangerous." to "Hace frío y los caminos son peligrosos.",
        "I bought a new laptop for work." to "Compré un portátil nuevo para el trabajo.",
        "My brother works as a mechanic in a small town." to "Mi hermano trabaja como mecánico en un pueblo pequeño.",
        "The patient is waiting in the clinic." to "El paciente está esperando en la clínica.",
        "Do you have any questions about the course?" to "¿Tienes preguntas sobre el curso?",
        "The store is closed on Sunday." to "La tienda está cerrada el domingo.",
        "We watched a fantastic movie last night." to "Vimos una película fantástica anoche.",
        "I forgot my password again." to "Olvidé mi contraseña otra vez.",
        "The kids are playing in the park." to "Los niños están jugando en el parque.",
        "My grandmother lives in a small house in the north." to "Mi abuela vive en una casa pequeña en el norte.",
        "Thanks for your help with the project." to "Gracias por tu ayuda con el proyecto.",
        "The company announced a new product." to "La empresa anunció un producto nuevo.",
        "I want to improve my Spanish this year." to "Quiero mejorar mi español este año.",
        "Please send me the invoice by email." to "Por favor envíame la factura por correo electrónico.",
        "The movie starts at eight." to "La película empieza a las ocho.",
        "My sister is a journalist." to "Mi hermana es periodista.",
        "I think the plan is excellent." to "Creo que el plan es excelente.",
        "He doesn't like his new job." to "A él no le gusta su trabajo nuevo.",
        "The traffic was terrible this morning." to "El tráfico era terrible esta mañana.",
        "The music was too loud." to "La música era demasiado ruidosa.",
        "I can't find my keys." to "No puedo encontrar mis llaves.",
        "We have class on Mondays at 9." to "Tenemos clase los lunes a las 9.",
        "The coffee is hot." to "El café está caliente.",
        "My sister works as a nurse." to "Mi hermana trabaja como enfermera.",
        "The soup is cold." to "La sopa está fría.",
    )

    @Test fun everydayMessagesTranslateCorrectly() {
        val wrong = corpus.mapNotNull { (en, es) ->
            val r = data.translator.translate(en)
            when {
                r.text != es -> "  \"$en\"\n    expected: $es\n    actual:   ${r.text}"
                r.coverage < 0.999f -> "  \"$en\" coverage ${r.coverage}"
                else -> null
            }
        }
        assertTrue("Mistranslations:\n" + wrong.joinToString("\n"), wrong.isEmpty())
    }

    @Test fun frequentEnglishContentWordsHaveTranslations() {
        // Top suggestable English words by real frequency; function words are handled by grammar rules, not the dictionary.
        val words = TestData.assetLines(BundledLanguageData.ENGLISH_LEXICON).filter { !it.startsWith("#") }
            .map { it.split('\t') }.filter { it.getOrElse(2) { "" } != "n" }.map { it[0] }
        fun coverage(n: Int) = words.take(n).count { data.dictionary.resolveEnglish(it).isNotEmpty() } * 100 / n
        println("EN_ES_COVERAGE top2000=${coverage(2000)}% top3000=${coverage(3000)}% top5000=${coverage(5000)}%")
        assertTrue("top 3000: ${coverage(3000)}%", coverage(3000) >= 80) // was 59% before the audit
        for (w in listOf("state", "system", "service", "information", "video", "data", "police", "account", "problem", "strategy"))
            assertTrue(w, data.dictionary.resolveEnglish(w).isNotEmpty())
        assertEquals("datos", data.dictionary.sense("data")?.baseTranslation)
    }
}
