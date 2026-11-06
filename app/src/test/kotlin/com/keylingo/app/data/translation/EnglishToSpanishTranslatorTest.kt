package com.keylingo.app.data.translation

import com.keylingo.app.testing.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Grammar regression suite for the rule-based translator, run against the
 * real bundled dictionary. Each case exercises a distinct construction
 * (named in the test), so a failure points at the rule that regressed.
 * Expected outputs were checked as correct, natural (Latin-American neutral)
 * Spanish.
 */
class EnglishToSpanishTranslatorTest {

    private val translator = TestData.bundled.translator

    private fun check(vararg cases: Pair<String, String>) {
        val failures = cases.mapNotNull { (en, es) ->
            val actual = translator.translate(en).text
            if (actual != es) "  \"$en\"\n    expected: $es\n    actual:   $actual" else null
        }
        assertTrue("Mistranslations:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun presentProgressiveAndLocation() = check(
        "I am working at home" to "Estoy trabajando en casa",
        "The kids are playing in the garden" to "Los niños están jugando en el jardín",
        "I'm learning Spanish because I want to travel" to "Estoy aprendiendo español porque quiero viajar",
        "It's raining" to "Está lloviendo",
        "I'm at work" to "Estoy en el trabajo",
    )

    @Test fun pastTenses() = check(
        "We went to the beach yesterday" to "Fuimos a la playa ayer",
        "I bought a new car last week" to "Compré un carro nuevo la semana pasada",
        "I didn't eat breakfast" to "No comí el desayuno",
        "I used to live in Mexico" to "Vivía en México",
        "I was sleeping" to "Estaba durmiendo",
        "He has finished his homework" to "Él ha terminado su tarea",
        "I have been working" to "He estado trabajando",
        "I just got home" to "Acabo de llegar a casa",
    )

    @Test fun futureModalsAndObligation() = check(
        "I am going to call you tomorrow" to "Te voy a llamar mañana",
        "I will see you later" to "Te veré después",
        "They will travel to Spain next summer" to "Ellos viajarán a España el próximo verano",
        "I have to work tonight" to "Tengo que trabajar esta noche",
        "We should study more" to "Deberíamos estudiar más",
        "I can't sleep" to "No puedo dormir",
        "I'll be there soon" to "Estaré allí pronto",
    )

    @Test fun cliticPronounsAndNegation() = check(
        "I love you" to "Te amo",
        "I don't know" to "No sé",
        "Can you help me?" to "¿Me puedes ayudar?",
        "Call me later" to "Llámame después",
        "Please send me the photo" to "Por favor envíame la foto",
        "Please don't call me" to "Por favor no me llames",
        "Don't worry" to "No te preocupes",
        "I miss you so much" to "Te extraño mucho",
    )

    @Test fun gustarConstruction() = check(
        "I like pizza" to "Me gusta la pizza",
        "She likes dogs" to "A ella le gustan los perros",
        "I like you" to "Me gustas",
        "We like the movies" to "Nos gustan las películas",
        "They don't like coffee" to "A ellos no les gusta el café",
        "Do you like music?" to "¿Te gusta la música?",
        "I would like a coffee" to "Me gustaría un café",
        "I would love to go" to "Me encantaría ir",
    )

    @Test fun serEstarTenerAndWeather() = check(
        "I'm hungry" to "Tengo hambre",
        "It is cold today" to "Hace frío hoy",
        "They are very tired" to "Ellos están muy cansados",
        "We are happy" to "Estamos felices",
        "Are you ready?" to "¿Estás listo?",
        "The red car is fast" to "El carro rojo es rápido",
        "Spanish is easy" to "El español es fácil",
        "The food was delicious" to "La comida era deliciosa",
        "I think you are right" to "Creo que tienes razón",
        "I am 25 years old" to "Tengo 25 años",
        "She is a teacher" to "Ella es maestra",
        "He is a good teacher" to "Él es un buen maestro",
    )

    @Test fun questions() = check(
        "Where are you?" to "¿Dónde estás?",
        "Do you want coffee?" to "¿Quieres café?",
        "What is your name?" to "¿Cuál es tu nombre?",
        "Where is my phone?" to "¿Dónde está mi teléfono?",
        "Where does your brother live?" to "¿Dónde vive tu hermano?",
        "How was your day?" to "¿Cómo estuvo tu día?",
        "Did you see the new movie?" to "¿Viste la película nueva?",
        "Do you have any money?" to "¿Tienes dinero?",
        "What are you doing?" to "¿Qué estás haciendo?",
        "Good morning, how are you?" to "Buenos días, ¿cómo estás?",
        "I'm going to the store, do you need anything?" to "Voy a la tienda, ¿necesitas algo?",
    )

    @Test fun subordinateClausesAndSubjunctive() = check(
        "I hope you are well" to "Espero que estés bien",
        "Text me when you arrive" to "Escríbeme cuando llegues",
        "I will call you when I finish" to "Te llamaré cuando termine",
        "Wait until I arrive" to "Espera hasta que llegue",
        "I want you to come" to "Quiero que vengas",
        "I need you to help me" to "Necesito que me ayudes",
        "I don't think he is here" to "No creo que él esté aquí",
    )

    @Test fun nounPhrasesAgreementAndPersonalA() = check(
        "I need a new phone" to "Necesito un teléfono nuevo",
        "My brother lives in a big house" to "Mi hermano vive en una casa grande",
        "My mom is cooking dinner" to "Mi mamá está cocinando la cena",
        "I love my family" to "Amo a mi familia",
        "I called my mom" to "Llamé a mi mamá",
        "She studies every day" to "Ella estudia todos los días",
        "The cities are big" to "Las ciudades son grandes",
        "There is a problem" to "Hay un problema",
        "Let's go to the park" to "Vamos al parque",
        "Let's eat" to "Vamos a comer",
        "My friends are coming tonight" to "Mis amigos vienen esta noche",
        "I think about you" to "Pienso en ti",
        "They talked about the movie" to "Ellos hablaron de la película",
    )

    @Test fun idioms() = check(
        "Thank you very much" to "Muchas gracias",
        "What time is it?" to "¿Qué hora es?",
        "See you tomorrow!" to "¡Hasta mañana!",
        "Have a nice day" to "Que tengas un buen día",
    )

    @Test fun unknownWordsLowerCoverageInsteadOfBeingInvented() {
        val result = translator.translate("I like flibberjabbers")
        assertTrue(result.coverage < 1f)
        assertTrue(result.text.contains("flibberjabbers"))
    }

    @Test fun segmentsAlignEnglishToSpanishForHints() {
        val result = translator.translate("I am working")
        val gerund = result.segments.first { it.sourceText == "working" }
        assertEquals("trabajando", gerund.hintText)
        assertEquals("gerund", gerund.formLabel)
        val subject = result.segments.first { it.sourceText == "I" }
        assertEquals("", subject.targetText) // dropped in the sentence...
        assertEquals("yo", subject.hintText) // ...but still taught as a hint
    }
}
