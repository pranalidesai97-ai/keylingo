package com.keylingo.app.data.translation

import com.keylingo.app.testing.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression suite for the rule-based Spanish -> English translator, run
 * against the real bundled dictionary. Each test names one construction so
 * a failure points at the rule that regressed. Expected outputs are
 * natural English (not word-for-word glosses).
 */
class SpanishToEnglishTranslatorTest {

    private val translator = TestData.bundled.spanishTranslator

    private fun check(vararg cases: Pair<String, String>) {
        val failures = cases.mapNotNull { (es, en) ->
            val r = translator.translate(es)
            when {
                r.text != en -> "  \"$es\"\n    expected: $en\n    actual:   ${r.text}"
                r.coverage < 0.999f -> "  \"$es\" translated correctly but coverage ${r.coverage} < 1 (the chip would be hidden)"
                else -> null
            }
        }
        assertTrue("Mistranslations:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun userExamples() = check(
        "¿Cómo estás?" to "How are you?",
        "También." to "Also.",
        "Mañana." to "Tomorrow.",
        "Estoy aprendiendo español." to "I am learning Spanish.",
        "Ayer fui al mercado." to "Yesterday I went to the market.",
        "Quiero aprender inglés." to "I want to learn English.",
    )

    @Test fun droppedSubjectsAndNegation() = check(
        "No entiendo." to "I don't understand.",
        "No sé." to "I don't know.",
        "Necesito ayuda." to "I need help.",
        "No tengo tiempo." to "I don't have time.",
        "Ella no habla inglés." to "She doesn't speak English.",
        "Nunca como carne." to "I never eat meat.",
        "Ellos no saben nada." to "They don't know anything.",
        "No veo a nadie." to "I don't see anybody.",
    )

    @Test fun questions() = check(
        "¿Dónde vives?" to "Where do you live?",
        "¿Qué quieres comer?" to "What do you want to eat?",
        "¿Por qué estás triste?" to "Why are you sad?",
        "¿Dónde vive tu hermano?" to "Where does your brother live?",
        "¿Qué come el perro?" to "What does the dog eat?",
        "¿Cuándo llega el tren?" to "When does the train arrive?",
        "¿Dónde está el baño?" to "Where is the bathroom?",
        "¿Quién es ella?" to "Who is she?",
        "¿Quién quiere café?" to "Who wants coffee?",
        "¿Qué pasó?" to "What happened?",
        "¿Qué es esto?" to "What is this?",
        "¿Has comido?" to "Have you eaten?",
        "¿Puedes ayudarme?" to "Can you help me?",
        "¿Tienes hambre?" to "Are you hungry?",
    )

    @Test fun quantityAndAgeQuestions() = check(
        "¿Cuántos años tienes?" to "How old are you?",
        "¿Cuántos años tiene tu hermano?" to "How old is your brother?",
        "¿Cuántos hermanos tienes?" to "How many brothers do you have?",
        "¿Cuántas personas hay?" to "How many people are there?",
        "¿Cuánto cuesta?" to "How much does it cost?",
        "¿Qué hora es?" to "What time is it?",
    )

    @Test fun questionsTypedWithoutAccents() = check(
        "Como estas?" to "How are you?",
        "Donde vives?" to "Where do you live?",
        "Por que estas triste?" to "Why are you sad?",
    )

    @Test fun tensesAndVerbChains() = check(
        "Comió mucho." to "He/she ate a lot.",
        "Lo compré ayer." to "I bought it yesterday.",
        "Vi una película anoche." to "I saw a movie last night.",
        "Estudié mucho para el examen." to "I studied a lot for the exam.",
        "Cuando era niño, vivía en México." to "When I was a child, I lived in Mexico.",
        "La comida estaba deliciosa." to "The food was delicious.",
        "Mañana iré a la playa." to "Tomorrow I will go to the beach.",
        "Voy a llamar a mi mamá." to "I am going to call my mom.",
        "Mañana voy a trabajar." to "Tomorrow I am going to work.",
        "Mi mamá está cocinando la cena." to "My mom is cooking dinner.",
        "Ellos están jugando al fútbol." to "They are playing soccer.",
        "He comido." to "I have eaten.",
        "Acabo de comer." to "I have just eaten.",
        "Tengo que trabajar." to "I have to work.",
        "Puedo ayudarte." to "I can help you.",
        "No quiero ir." to "I don't want to go.",
        "Me gustaría un café." to "I would like a coffee.",
    )

    @Test fun gustarAndExperiencerVerbs() = check(
        "Me gustan los perros." to "I like dogs.",
        "Me gusta la música." to "I like music.",
        "No me gusta el café." to "I don't like coffee.",
        "Me gusta mucho." to "I like it a lot.",
        "¿Te gusta bailar?" to "Do you like to dance?",
        "Me encanta este lugar." to "I love this place.",
        "Me duele la cabeza." to "My head hurts.",
        "Me duelen los pies." to "My feet hurt.",
        "Te quiero." to "I love you.",
        "Te extraño." to "I miss you.",
    )

    @Test fun idiomsTenerHacerHaber() = check(
        "Tengo hambre." to "I am hungry.",
        "Tengo veinte años." to "I am 20 years old.",
        "Mi hijo tiene cinco años." to "My son is five years old.",
        "Hace frío hoy." to "It is cold today.",
        "Hace mucho calor." to "It is very hot.",
        "Llueve mucho." to "It rains a lot.",
        "Está lloviendo." to "It is raining.",
        "Mañana va a llover." to "Tomorrow it is going to rain.",
        "Hay un problema." to "There is a problem.",
        "Me llamo Ana." to "My name is Ana.",
        "¿Cómo te llamas?" to "What is your name?",
    )

    @Test fun serAndEstar() = check(
        "Estoy cansado." to "I am tired.",
        "Ella es muy inteligente." to "She is very smart.",
        "Mis amigos son muy simpáticos." to "My friends are very nice.",
        "Soy estudiante." to "I am a student.",
        "Mi padre es profesor." to "My father is a professor.",
        "Soy de México." to "I am from Mexico.",
        "Hoy es lunes." to "Today is Monday.",
        "Mañana es mi cumpleaños." to "Tomorrow is my birthday.",
        "Es tarde." to "It is late.",
        "Es importante." to "It is important.",
        "Es hora de dormir." to "It is time to sleep.",
        "Es un buen libro." to "It is a good book.",
        "Es un buen amigo." to "He/she is a good friend.",
        "Esta es mi casa." to "This is my house.",
        "Eso es verdad." to "That is true.",
    )

    @Test fun nounPhrasesAndPrepositions() = check(
        "Ellos viven en una casa grande." to "They live in a big house.",
        "Mi casa es tu casa." to "My house is your house.",
        "Estamos en casa." to "We are at home.",
        "Trabajo desde casa." to "I work from home.",
        "Voy al trabajo en autobús." to "I go to work by bus.",
        "Mis hijos van a la escuela." to "My children go to school.",
        "El libro está en la mesa." to "The book is on the table.",
        "Ayer comí con mis padres." to "Yesterday I ate with my parents.",
        "Siempre estudio por la noche." to "I always study at night.",
        "Estudio por la tarde." to "I study in the afternoon.",
        "Hablo español e inglés." to "I speak Spanish and English.",
        "Leo un libro cada noche." to "I read a book every night.",
        "La veo mañana." to "I see her tomorrow.",
        "Estos son mis amigos." to "These are my friends.",
    )

    @Test fun setPhrases() = check(
        "Yo también." to "Me too.",
        "Buenos días." to "Good morning.",
        "Lo siento." to "I'm sorry.",
        "Mucho gusto." to "Nice to meet you.",
        "¿Qué tal?" to "How are you?",
        "Hasta luego." to "See you later.",
        "Gracias por tu ayuda." to "Thanks for your help.",
        "Hola, ¿cómo estás?" to "Hi, how are you?",
    )

    @Test fun unknownWordsLowerCoverageSoNoWrongChipIsShown() {
        // A sentence the rules do not fully understand must not reach full coverage.
        val r = translator.translate("El gato xyzzyq la ventana.")
        assertTrue("coverage ${r.coverage}", r.coverage < 0.999f)
    }

    @Test fun questionFlagAndPunctuation() {
        val r = translator.translate("¿Dónde vives?")
        assertTrue(r.isQuestion)
        assertEquals("Where do you live?", r.text)
        assertEquals("Thanks!", translator.translate("¡Gracias!").text)
    }

    @Test fun functioningNeedAndContextualAdjectives() = check(
        "El sistema no funciona." to "The system doesn't work.",
        "Mi teléfono no funciona." to "My phone doesn't work.",
        "Me falta tiempo." to "I need time.",
        "La presión es alta." to "The pressure is high.",
        "Mi hermano es alto." to "My brother is tall.",
        "El edificio es alto." to "The building is tall.",
    )

    @Test fun negationSurvivesAnUnknownVerb() {
        // Not in the dictionary: the word is kept as typed, but the sentence must not read as positive.
        val text = translator.translate("El perro no ladra.").text
        assertTrue(text, text.contains("not"))
    }
}
