package com.keylingo.app.data.language.spanish

import org.junit.Assert.assertEquals
import org.junit.Test

class SpanishMorphologyTest {

    @Test fun pluralsFollowSpellingAndAccentRules() {
        val cases = mapOf(
            "casa" to "casas", "perro" to "perros", "café" to "cafés", "ciudad" to "ciudades",
            "canción" to "canciones", "árbol" to "árboles", "lápiz" to "lápices", "luz" to "luces",
            "joven" to "jóvenes", "examen" to "exámenes", "país" to "países", "mes" to "meses",
            "lunes" to "lunes", "jardín" to "jardines", "inglés" to "ingleses", "autobús" to "autobuses",
            "fin de semana" to "fines de semana", "reloj" to "relojes", "flor" to "flores",
        )
        cases.forEach { (singular, plural) -> assertEquals(singular, plural, SpanishMorphology.plural(singular)) }
    }

    @Test fun adjectiveAgreement() {
        assertEquals("roja", SpanishMorphology.agreeAdjective("rojo", Gender.FEMININE, false))
        assertEquals("rojos", SpanishMorphology.agreeAdjective("rojo", Gender.MASCULINE, true))
        assertEquals("grandes", SpanishMorphology.agreeAdjective("grande", Gender.FEMININE, true))
        assertEquals("felices", SpanishMorphology.agreeAdjective("feliz", Gender.MASCULINE, true))
        assertEquals("trabajadora", SpanishMorphology.agreeAdjective("trabajador", Gender.FEMININE, false))
        assertEquals("difíciles", SpanishMorphology.agreeAdjective("difícil", Gender.FEMININE, true))
        assertEquals("azul", SpanishMorphology.agreeAdjective("azul", Gender.FEMININE, false))
        assertEquals("buen", SpanishMorphology.apocopate("bueno", Gender.MASCULINE, false))
        assertEquals("buena", SpanishMorphology.apocopate("bueno", Gender.FEMININE, false))
        assertEquals("gran", SpanishMorphology.apocopate("grande", Gender.FEMININE, false))
    }

    @Test fun articlesIncludingStressedA() {
        assertEquals("el", SpanishMorphology.definiteArticle("agua", Gender.FEMININE, false))
        assertEquals("las", SpanishMorphology.definiteArticle("agua", Gender.FEMININE, true))
        assertEquals("la", SpanishMorphology.definiteArticle("casa", Gender.FEMININE, false))
        assertEquals("unos", SpanishMorphology.indefiniteArticle("libro", Gender.MASCULINE, true))
    }

    @Test fun cliticAttachmentKeepsStress() {
        assertEquals("llámame", SpanishText.attachUnstressed("llama", "me"))
        assertEquals("comiéndolo", SpanishText.attachUnstressed("comiendo", "lo"))
        assertEquals("dime", SpanishText.attachUnstressed("di", "me"))
        assertEquals("verte", SpanishText.attachUnstressed("ver", "te"))
        assertEquals("comprarlo", SpanishText.attachUnstressed("comprar", "lo"))
        assertEquals("quedándose", SpanishText.attachUnstressed("quedando", "se"))
    }

    @Test fun foldingKeepsEnyeDistinct() {
        assertEquals("tambien", SpanishText.fold("También"))
        assertEquals("año", SpanishText.fold("año"))
        assertEquals("ano", SpanishText.foldIncludingEnye("año"))
        assertEquals("e", SpanishText.conjunctionAnd("inglés"))
        assertEquals("u", SpanishText.conjunctionOr("otro"))
    }
}
