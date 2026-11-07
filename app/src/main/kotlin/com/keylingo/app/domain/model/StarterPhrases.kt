package com.keylingo.app.domain.model

/**
 * Everyday phrases offered in an empty field, so the keyboard opens like a
 * language-learning companion rather than with single-word guesses.
 * One list per typing language; never mixed. Tapping one inserts it.
 * They are constants, so they are shown immediately, before the bundled
 * language data has finished loading.
 */
object StarterPhrases {
    val ENGLISH: List<String> = listOf(
        "Hello", "Thanks", "Good morning", "Good night", "How are you?", "How are you doing?", "Nice to meet you",
        "Please", "Sorry", "Thank you", "You're welcome", "See you", "Good luck", "Have a nice day",
        // Learning-related
        "Basic", "English Course", "Learn English", "More",
    )

    val SPANISH: List<String> = listOf(
        "Hola", "Gracias", "Buenos días", "Buenas noches", "¿Cómo estás?", "¿Cómo está usted?", "Mucho gusto",
        "Por favor", "Lo siento", "De nada", "Hasta luego", "Buena suerte", "Que tengas un buen día",
        // Learning-related
        "Curso básico", "Aprende español", "Más",
    )

    fun forLanguage(languageCode: String?): List<String> = if (languageCode == "es") SPANISH else ENGLISH
}
