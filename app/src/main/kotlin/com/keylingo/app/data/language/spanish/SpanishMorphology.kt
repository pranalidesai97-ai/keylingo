package com.keylingo.app.data.language.spanish

enum class Gender { MASCULINE, FEMININE }

/** Noun-phrase morphology: plurals, adjective agreement, articles and determiners. */
object SpanishMorphology {

    /** Feminine nouns beginning with a stressed a-/ha- take "el"/"un" in the singular (el agua, un águila). */
    private val stressedA = setOf(
        "agua", "águila", "alma", "área", "arma", "aula", "ala", "hacha", "hambre", "hada", "ama", "ancla", "asma",
    )

    /** Plural of a noun or adjective, including accent changes (canción -> canciones, joven -> jóvenes). */
    fun plural(word: String): String {
        if (word.isEmpty()) return word
        if (word.contains(' ')) {
            // Multi-word noun ("fin de semana"): pluralise the head only.
            val head = word.substringBefore(' ')
            return plural(head) + " " + word.substringAfter(' ')
        }
        val w = word
        val last = w.last()
        return when {
            last in "aeiouáéó" -> w + "s"
            last == 'í' || last == 'ú' -> w + "es" // jabalí -> jabalíes, menú is an exception below
            w.endsWith("z") -> w.dropLast(1) + "ces"
            w.endsWith("s") || w.endsWith("x") -> {
                // Unstressed final syllable -> invariable (el lunes / los lunes, la crisis / las crisis).
                // Stressed final syllable (monosyllables, or a written accent there) -> add -es (mes -> meses, país -> países).
                val lastSyllableStressed = countVowelGroups(w) == 1 ||
                    Regex("[áéíóú][^aeiouáéíóú]*$").containsMatchIn(w)
                if (lastSyllableStressed) SpanishText.attachUnstressed(w, "es") else w
            }
            else -> SpanishText.attachUnstressed(w, "es")
        }.let { exceptions[word] ?: it }
    }

    private val exceptions = mapOf(
        "menú" to "menús", "sofá" to "sofás", "papá" to "papás", "mamá" to "mamás", "bebé" to "bebés",
        "café" to "cafés", "carácter" to "caracteres", "régimen" to "regímenes", "examen" to "exámenes",
        "joven" to "jóvenes", "imagen" to "imágenes", "origen" to "orígenes", "mes" to "meses",
        "inglés" to "ingleses", "país" to "países", "estrés" to "estrés", "virus" to "virus",
    )

    private fun countVowelGroups(word: String): Int =
        Regex("[aeiouáéíóúü]+").findAll(word).count()

    /** Feminine form of an adjective given in masculine singular (rojo -> roja, trabajador -> trabajadora, inglés -> inglesa, feliz -> feliz). */
    fun feminineAdjective(adjective: String): String {
        val a = adjective
        if (a.contains(' ')) return feminineAdjective(a.substringBefore(' ')) + " " + a.substringAfter(' ')
        return when {
            a.endsWith("o") -> a.dropLast(1) + "a"
            a.endsWith("ón") -> a.dropLast(2) + "ona"
            a.endsWith("án") -> a.dropLast(2) + "ana"
            a.endsWith("és") -> a.dropLast(2) + "esa"
            a.endsWith("dor") || a.endsWith("tor") || a.endsWith("sor") -> a + "a"
            else -> a // grande, feliz, azul, fácil, joven, cortés is caught above
        }
    }

    /** Adjective inflected to agree with a noun of [gender]/[plural]. */
    fun agreeAdjective(adjective: String, gender: Gender, plural: Boolean): String {
        val base = if (gender == Gender.FEMININE) feminineAdjective(adjective) else adjective
        return if (plural) plural(base) else base
    }

    /** Short forms used before a masculine singular noun: bueno -> buen, malo -> mal, primero -> primer; grande -> gran before any singular noun. */
    fun apocopate(adjective: String, gender: Gender, plural: Boolean): String {
        if (plural) return agreeAdjective(adjective, gender, true)
        if (adjective == "grande") return "gran"
        if (gender == Gender.MASCULINE) {
            when (adjective) {
                "bueno" -> return "buen"; "malo" -> return "mal"; "primero" -> return "primer"
                "tercero" -> return "tercer"; "alguno" -> return "algún"; "ninguno" -> return "ningún"
                "uno" -> return "un"
            }
        }
        return agreeAdjective(adjective, gender, false)
    }

    fun definiteArticle(noun: String, gender: Gender, plural: Boolean): String = when {
        plural && gender == Gender.FEMININE -> "las"
        plural -> "los"
        gender == Gender.FEMININE && noun.substringBefore(' ').lowercase() in stressedA -> "el"
        gender == Gender.FEMININE -> "la"
        else -> "el"
    }

    fun indefiniteArticle(noun: String, gender: Gender, plural: Boolean): String = when {
        plural && gender == Gender.FEMININE -> "unas"
        plural -> "unos"
        gender == Gender.FEMININE && noun.substringBefore(' ').lowercase() in stressedA -> "un"
        gender == Gender.FEMININE -> "una"
        else -> "un"
    }

    /** this/that/these/those, my/your/our... agreement helpers. */
    fun demonstrative(near: Boolean, gender: Gender, plural: Boolean): String = when {
        near && !plural -> if (gender == Gender.FEMININE) "esta" else "este"
        near -> if (gender == Gender.FEMININE) "estas" else "estos"
        !plural -> if (gender == Gender.FEMININE) "esa" else "ese"
        else -> if (gender == Gender.FEMININE) "esas" else "esos"
    }

    /** Possessive determiners that inflect only for number (mi/mis, tu/tus, su/sus) or for gender and number (nuestro). */
    fun possessive(base: String, gender: Gender, plural: Boolean): String = when (base) {
        "nuestro" -> agreeAdjective("nuestro", gender, plural)
        else -> if (plural) base + "s" else base
    }

    /** Best-effort gender for a noun with no explicit tag (rules of thumb; bundled data tags gender explicitly). */
    fun guessGender(noun: String): Gender {
        val head = noun.substringBefore(' ')
        return when {
            head.endsWith("a") || head.endsWith("ción") || head.endsWith("sión") || head.endsWith("dad") ||
                head.endsWith("tad") || head.endsWith("tud") || head.endsWith("umbre") -> Gender.FEMININE
            else -> Gender.MASCULINE
        }
    }
}
