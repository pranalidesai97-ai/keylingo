package com.keylingo.app.data.language.spanish

/** Grammatical person/number. Vosotros is intentionally not modelled (KeyLingo teaches Latin-American-neutral Spanish; "ustedes" uses the ELLOS form). */
enum class SpanishPerson { YO, TU, EL, NOSOTROS, ELLOS;
    val isPlural: Boolean get() = this == NOSOTROS || this == ELLOS
}

enum class SpanishTense { PRESENT, PRETERITE, IMPERFECT, FUTURE, CONDITIONAL }

/**
 * Deterministic Spanish verb conjugator: regular -ar/-er/-ir paradigms,
 * stem-changing verbs (e>ie, o>ue, u>ue, e>i), orthographic changes
 * (-car/-gar/-zar, -ger/-gir, -cer/-cir, -uir, vowel stems), -iar/-uar
 * accentuation, and full tables for the common irregular verbs.
 *
 * Accepts reflexive infinitives ("quedarse") and multi-word verbal
 * expressions ("estar de acuerdo", "hacer clic"): the first word is
 * conjugated, the rest is kept. Reflexive pronouns are returned
 * separately via [reflexivePronoun] so callers can place them correctly.
 *
 * Coverage is deliberately bounded: verbs not in the irregular tables are
 * conjugated by rule, which is correct for the overwhelming majority of
 * Spanish verbs (new verbs entering the language are regular).
 */
object SpanishConjugator {

    data class VerbParts(val infinitive: String, val reflexive: Boolean, val tail: String)

    /** Splits "quedarse", "estar de acuerdo", "hacer clic" into the conjugatable infinitive, reflexivity and trailing words. */
    fun parse(expression: String): VerbParts {
        val trimmed = expression.trim()
        val head = trimmed.substringBefore(' ')
        val tail = if (trimmed.contains(' ')) trimmed.substringAfter(' ') else ""
        val reflexive = head.length > 4 && head.endsWith("se") && head.dropLast(2).let { it.endsWith("ar") || it.endsWith("er") || it.endsWith("ir") || it.endsWith("ír") }
        val infinitive = if (reflexive) head.dropLast(2) else head
        return VerbParts(infinitive, reflexive, tail)
    }

    fun reflexivePronoun(person: SpanishPerson): String = when (person) {
        SpanishPerson.YO -> "me"; SpanishPerson.TU -> "te"; SpanishPerson.EL -> "se"
        SpanishPerson.NOSOTROS -> "nos"; SpanishPerson.ELLOS -> "se"
    }

    /** Conjugated form of [expression] including any reflexive pronoun and trailing words: "quedarse" PRESENT YO -> "me quedo". */
    fun conjugatePhrase(expression: String, tense: SpanishTense, person: SpanishPerson): String {
        val parts = parse(expression)
        val verb = conjugate(parts.infinitive, tense, person)
        val withPronoun = if (parts.reflexive) "${reflexivePronoun(person)} $verb" else verb
        return if (parts.tail.isEmpty()) withPronoun else "$withPronoun ${parts.tail}"
    }

    // ---------------------------------------------------------------- tables

    private val P = SpanishPerson.entries

    private fun forms(vararg f: String): Map<SpanishPerson, String> = P.zip(f.toList()).toMap()

    private val irregularPresent: Map<String, Map<SpanishPerson, String>> = mapOf(
        "ser" to forms("soy", "eres", "es", "somos", "son"),
        "estar" to forms("estoy", "estás", "está", "estamos", "están"),
        "ir" to forms("voy", "vas", "va", "vamos", "van"),
        "tener" to forms("tengo", "tienes", "tiene", "tenemos", "tienen"),
        "hacer" to forms("hago", "haces", "hace", "hacemos", "hacen"),
        "decir" to forms("digo", "dices", "dice", "decimos", "dicen"),
        "saber" to forms("sé", "sabes", "sabe", "sabemos", "saben"),
        "venir" to forms("vengo", "vienes", "viene", "venimos", "vienen"),
        "ver" to forms("veo", "ves", "ve", "vemos", "ven"),
        "dar" to forms("doy", "das", "da", "damos", "dan"),
        "poner" to forms("pongo", "pones", "pone", "ponemos", "ponen"),
        "salir" to forms("salgo", "sales", "sale", "salimos", "salen"),
        "traer" to forms("traigo", "traes", "trae", "traemos", "traen"),
        "caer" to forms("caigo", "caes", "cae", "caemos", "caen"),
        "oír" to forms("oigo", "oyes", "oye", "oímos", "oyen"),
        "haber" to forms("he", "has", "ha", "hemos", "han"),
        "reír" to forms("río", "ríes", "ríe", "reímos", "ríen"),
        "sonreír" to forms("sonrío", "sonríes", "sonríe", "sonreímos", "sonríen"),
        "oler" to forms("huelo", "hueles", "huele", "olemos", "huelen"),
        "caber" to forms("quepo", "cabes", "cabe", "cabemos", "caben"),
    )

    private val irregularPreterite: Map<String, Map<SpanishPerson, String>> = mapOf(
        "ser" to forms("fui", "fuiste", "fue", "fuimos", "fueron"),
        "ir" to forms("fui", "fuiste", "fue", "fuimos", "fueron"),
        "estar" to forms("estuve", "estuviste", "estuvo", "estuvimos", "estuvieron"),
        "tener" to forms("tuve", "tuviste", "tuvo", "tuvimos", "tuvieron"),
        "hacer" to forms("hice", "hiciste", "hizo", "hicimos", "hicieron"),
        "poder" to forms("pude", "pudiste", "pudo", "pudimos", "pudieron"),
        "querer" to forms("quise", "quisiste", "quiso", "quisimos", "quisieron"),
        "decir" to forms("dije", "dijiste", "dijo", "dijimos", "dijeron"),
        "saber" to forms("supe", "supiste", "supo", "supimos", "supieron"),
        "venir" to forms("vine", "viniste", "vino", "vinimos", "vinieron"),
        "ver" to forms("vi", "viste", "vio", "vimos", "vieron"),
        "dar" to forms("di", "diste", "dio", "dimos", "dieron"),
        "poner" to forms("puse", "pusiste", "puso", "pusimos", "pusieron"),
        "traer" to forms("traje", "trajiste", "trajo", "trajimos", "trajeron"),
        "haber" to forms("hube", "hubiste", "hubo", "hubimos", "hubieron"),
        "andar" to forms("anduve", "anduviste", "anduvo", "anduvimos", "anduvieron"),
        "caber" to forms("cupe", "cupiste", "cupo", "cupimos", "cupieron"),
        "reír" to forms("reí", "reíste", "rio", "reímos", "rieron"),
        "sonreír" to forms("sonreí", "sonreíste", "sonrio", "sonreímos", "sonrieron"),
    )

    private val irregularImperfect: Map<String, Map<SpanishPerson, String>> = mapOf(
        "ser" to forms("era", "eras", "era", "éramos", "eran"),
        "ir" to forms("iba", "ibas", "iba", "íbamos", "iban"),
        "ver" to forms("veía", "veías", "veía", "veíamos", "veían"),
    )

    private val irregularFutureStem: Map<String, String> = mapOf(
        "tener" to "tendr", "poner" to "pondr", "salir" to "saldr", "venir" to "vendr",
        "poder" to "podr", "saber" to "sabr", "querer" to "querr", "haber" to "habr",
        "caber" to "cabr", "hacer" to "har", "decir" to "dir", "valer" to "valdr",
    )

    private val irregularGerund: Map<String, String> = mapOf(
        "ir" to "yendo", "poder" to "pudiendo", "decir" to "diciendo", "venir" to "viniendo",
        "reír" to "riendo", "sonreír" to "sonriendo", "oír" to "oyendo",
    )

    private val irregularParticiple: Map<String, String> = mapOf(
        "hacer" to "hecho", "decir" to "dicho", "ver" to "visto", "escribir" to "escrito",
        "describir" to "descrito", "abrir" to "abierto", "poner" to "puesto", "volver" to "vuelto",
        "resolver" to "resuelto", "morir" to "muerto", "romper" to "roto", "cubrir" to "cubierto",
        "imprimir" to "impreso", "devolver" to "devuelto", "descubrir" to "descubierto",
    )

    /** Stem-change class, keyed by infinitive. Applies to the last e/o/u of the stem. */
    private enum class StemChange { E_IE, O_UE, U_UE, E_I }

    private val stemChanges: Map<String, StemChange> = buildMap {
        listOf("pensar", "empezar", "cerrar", "entender", "querer", "perder", "preferir", "sentir",
            "mentir", "despertar", "sentar", "comenzar", "defender", "invertir", "requerir", "nevar",
            "divertir", "convertir", "encender", "recomendar", "atravesar", "calentar", "confesar",
            "negar", "fregar", "tener", "venir", "advertir", "sugerir", "herir", "hervir")
            .forEach { put(it, StemChange.E_IE) }
        listOf("recordar", "costar", "volar", "encontrar", "mostrar", "dormir", "morir", "volver",
            "poder", "contar", "soñar", "almorzar", "resolver", "doler", "llover", "colgar", "probar",
            "mover", "devolver", "acostar", "rogar", "sonar", "aprobar", "demostrar",
            "torcer", "morder", "soler")
            .forEach { put(it, StemChange.O_UE) }
        put("jugar", StemChange.U_UE)
        listOf("pedir", "repetir", "seguir", "elegir", "servir", "vestir", "competir", "conseguir",
            "medir", "despedir", "impedir", "perseguir", "corregir", "reír", "sonreír")
            .forEach { put(it, StemChange.E_I) }
    }

    /** -iar / -uar verbs that stress (and accent) the i/u in the singular + 3rd plural present. */
    private val stressedIarUar = setOf(
        "enviar", "esquiar", "confiar", "guiar", "variar", "fiar", "criar", "desconfiar",
        "continuar", "actuar", "evaluar", "graduar", "situar", "acentuar",
    )

    /** -cer / -cir verbs after a vowel take -zco in the yo present (conocer -> conozco). Excludes hacer/decir (irregular) and cocer-type verbs. */
    private fun takesZco(inf: String): Boolean {
        if (inf in setOf("hacer", "decir", "cocer", "torcer", "mecer", "vencer", "convencer", "ejercer")) return false
        if (!(inf.endsWith("cer") || inf.endsWith("cir"))) return false
        return inf.length >= 4 && inf[inf.length - 4] in "aeiou"
    }

    private fun isDucir(inf: String) = inf.endsWith("ducir")

    private fun isUir(inf: String) = inf.endsWith("uir") && !inf.endsWith("guir") && !inf.endsWith("quir")

    // -------------------------------------------------------------- helpers

    private fun ending(inf: String): String = when {
        inf.endsWith("ar") -> "ar"
        inf.endsWith("er") -> "er"
        inf.endsWith("ír") -> "ir"
        else -> "ir"
    }

    private fun stem(inf: String): String = inf.dropLast(2)

    private fun applyStemChange(stem: String, change: StemChange): String {
        val target = when (change) {
            StemChange.E_IE, StemChange.E_I -> 'e'
            StemChange.O_UE -> 'o'
            StemChange.U_UE -> 'u'
        }
        // Skip the silent u of "gu"/"qu" when looking for the vowel to change.
        var index = stem.lastIndexOf(target)
        if (change == StemChange.U_UE) index = stem.lastIndexOf('u')
        if (index < 0) return stem
        val replacement = when (change) {
            StemChange.E_IE -> "ie"; StemChange.O_UE -> "ue"; StemChange.U_UE -> "ue"; StemChange.E_I -> "i"
        }
        val changed = stem.substring(0, index) + replacement + stem.substring(index + 1)
        // o > ue at the start of a word is written "hue" (oler -> huelo is tabled; this covers others).
        return if (index == 0 && change == StemChange.O_UE) "h$changed" else changed
    }

    /** Spelling fix-ups before an ending starting with [nextVowel] (e/é or o/a). */
    private fun orthographic(stem: String, inf: String, nextVowel: Char): String {
        val front = nextVowel == 'e' || nextVowel == 'é' || nextVowel == 'i'
        return when {
            inf.endsWith("car") && front -> stem.dropLast(1) + "qu"
            inf.endsWith("gar") && front -> stem + "u"
            inf.endsWith("zar") && front -> stem.dropLast(1) + "c"
            (inf.endsWith("ger") || inf.endsWith("gir")) && !front -> stem.dropLast(1) + "j"
            inf.endsWith("guir") && !front -> stem.dropLast(1) // seguir: sigu + o -> sigo
            else -> stem
        }
    }

    private fun vowelStem(inf: String): Boolean {
        val e = ending(inf)
        if (e == "ar") return false
        val s = stem(inf.replace("í", "i"))
        return s.isNotEmpty() && s.last() in "aeo" // leer, creer, caer, oír, poseer, traer
    }

    // ---------------------------------------------------------------- forms

    fun conjugate(infinitive: String, tense: SpanishTense, person: SpanishPerson): String {
        val inf = infinitive.trim().lowercase()
        return when (tense) {
            SpanishTense.PRESENT -> present(inf, person)
            SpanishTense.PRETERITE -> preterite(inf, person)
            SpanishTense.IMPERFECT -> imperfect(inf, person)
            SpanishTense.FUTURE -> future(inf, person)
            SpanishTense.CONDITIONAL -> conditional(inf, person)
        }
    }

    private fun present(inf: String, person: SpanishPerson): String {
        irregularPresent[inf]?.let { return it.getValue(person) }
        val e = ending(inf)
        val endings = when (e) {
            "ar" -> listOf("o", "as", "a", "amos", "an")
            "er" -> listOf("o", "es", "e", "emos", "en")
            else -> listOf("o", "es", "e", "imos", "en")
        }
        val ending = endings[person.ordinal]
        var stem = stem(inf)
        val boot = person != SpanishPerson.NOSOTROS

        if (inf in stressedIarUar && boot) {
            val i = stem.lastIndexOf(if (inf.endsWith("iar")) 'i' else 'u')
            stem = stem.substring(0, i) + (if (inf.endsWith("iar")) "í" else "ú") + stem.substring(i + 1)
            return stem + ending
        }
        if (isUir(inf)) {
            return if (person == SpanishPerson.NOSOTROS) stem + ending else stem + "y" + ending
        }
        if (person == SpanishPerson.YO) {
            if (takesZco(inf)) return stem.dropLast(1) + "zc" + ending
            val sc = stemChanges[inf]
            if (sc != null) stem = applyStemChange(stem, sc)
            return orthographic(stem, inf, ending[0]) + ending
        }
        if (boot) stemChanges[inf]?.let { stem = applyStemChange(stem, it) }
        return orthographic(stem, inf, ending[0]) + ending
    }

    private fun preterite(inf: String, person: SpanishPerson): String {
        irregularPreterite[inf]?.let { return it.getValue(person) }
        val stem = stem(inf)
        if (isDucir(inf)) {
            val s = stem.dropLast(1) + "j" // conduc -> conduj
            return s + listOf("e", "iste", "o", "imos", "eron")[person.ordinal]
        }
        val e = ending(inf)
        if (e == "ar") {
            val endings = listOf("é", "aste", "ó", "amos", "aron")
            val ending = endings[person.ordinal]
            return orthographic(stem, inf, ending[0]) + ending
        }
        if (isUir(inf) || vowelStem(inf)) {
            // leer: leí, leíste, leyó, leímos, leyeron -- construir: construí, construiste, construyó, construimos, construyeron
            val s = stem(inf.replace("í", "i"))
            val accentedI = if (isUir(inf)) "i" else "í"
            return when (person) {
                SpanishPerson.YO -> "${s}í"
                SpanishPerson.TU -> "$s${accentedI}ste"
                SpanishPerson.EL -> "${s}yó"
                SpanishPerson.NOSOTROS -> "$s${accentedI}mos"
                SpanishPerson.ELLOS -> "${s}yeron"
            }
        }
        val endings = listOf("í", "iste", "ió", "imos", "ieron")
        val ending = endings[person.ordinal]
        // -ir stem changers change e>i / o>u in the 3rd persons.
        val sc = stemChanges[inf]
        val thirdPerson = person == SpanishPerson.EL || person == SpanishPerson.ELLOS
        if (e == "ir" && thirdPerson && sc != null) {
            val changed = when (sc) {
                StemChange.E_IE, StemChange.E_I -> replaceLast(stem, 'e', "i")
                StemChange.O_UE -> replaceLast(stem, 'o', "u")
                StemChange.U_UE -> stem
            }
            return changed + ending
        }
        return stem + ending
    }

    private fun replaceLast(s: String, c: Char, r: String): String {
        val i = s.lastIndexOf(c)
        return if (i < 0) s else s.substring(0, i) + r + s.substring(i + 1)
    }

    private fun imperfect(inf: String, person: SpanishPerson): String {
        irregularImperfect[inf]?.let { return it.getValue(person) }
        val stem = stem(inf.replace("í", "i"))
        val endings = if (ending(inf) == "ar") listOf("aba", "abas", "aba", "ábamos", "aban")
        else listOf("ía", "ías", "ía", "íamos", "ían")
        return stem + endings[person.ordinal]
    }

    private fun futureStem(inf: String): String = irregularFutureStem[inf] ?: inf.replace("í", "i")

    private fun future(inf: String, person: SpanishPerson): String =
        futureStem(inf) + listOf("é", "ás", "á", "emos", "án")[person.ordinal]

    private fun conditional(inf: String, person: SpanishPerson): String =
        futureStem(inf) + listOf("ía", "ías", "ía", "íamos", "ían")[person.ordinal]

    fun gerund(infinitive: String): String {
        val parts = parse(infinitive)
        val inf = parts.infinitive.lowercase()
        val base = irregularGerund[inf] ?: run {
            val stem = stem(inf)
            when {
                ending(inf) == "ar" -> stem + "ando"
                isUir(inf) || vowelStem(inf) -> stem(inf.replace("í", "i")) + "yendo"
                ending(inf) == "ir" && stemChanges[inf] != null -> {
                    when (stemChanges.getValue(inf)) {
                        StemChange.O_UE -> replaceLast(stem, 'o', "u") + "iendo"
                        StemChange.U_UE -> stem + "iendo"
                        else -> replaceLast(stem, 'e', "i") + "iendo"
                    }
                }
                else -> stem + "iendo"
            }
        }
        return if (parts.tail.isEmpty()) base else "$base ${parts.tail}"
    }

    fun pastParticiple(infinitive: String): String {
        val parts = parse(infinitive)
        val inf = parts.infinitive.lowercase()
        val base = irregularParticiple[inf] ?: run {
            val stem = stem(inf.replace("í", "i"))
            when {
                ending(inf) == "ar" -> stem + "ado"
                isUir(inf) -> stem + "ido"
                vowelStem(inf) -> stem + "ído"
                else -> stem + "ido"
            }
        }
        return if (parts.tail.isEmpty()) base else "$base ${parts.tail}"
    }

    private val irregularSubjunctiveStem = mapOf(
        "ser" to "se", "estar" to "est", "ir" to "vay", "dar" to "d", "saber" to "sep", "haber" to "hay", "ver" to "ve",
    )

    /**
     * Present subjunctive, built the regular way from the yo present stem
     * (hago -> haga, conozco -> conozca, pienso -> piense) with the -car/-gar/-zar
     * spelling changes and the common fully irregular stems. Used for negative
     * commands ("no te preocupes") and wishes.
     */
    fun presentSubjunctive(infinitive: String, person: SpanishPerson): String {
        val inf = infinitive.lowercase()
        val ar = ending(inf) == "ar"
        val endings = if (ar) listOf("e", "es", "e", "emos", "en") else listOf("a", "as", "a", "amos", "an")
        val ending = endings[person.ordinal]
        irregularSubjunctiveStem[inf]?.let { stem ->
            return when (inf) {
                "estar" -> listOf("esté", "estés", "esté", "estemos", "estén")[person.ordinal]
                "dar" -> listOf("dé", "des", "dé", "demos", "den")[person.ordinal]
                "ser" -> listOf("sea", "seas", "sea", "seamos", "sean")[person.ordinal]
                "ver" -> listOf("vea", "veas", "vea", "veamos", "vean")[person.ordinal]
                else -> stem + ending
            }
        }
        val yo = present(inf, SpanishPerson.YO)
        var stem = if (yo.endsWith("o")) yo.dropLast(1) else stem(inf)
        if (person == SpanishPerson.NOSOTROS) {
            // Boot verbs keep the unchanged stem in nosotros, except -ir stem changers (pidamos, durmamos).
            val sc = stemChanges[inf]
            if (sc != null && !takesZco(inf) && irregularPresent[inf] == null) {
                stem = stem(inf)
                if (ending(inf) == "ir") stem = when (sc) {
                    StemChange.O_UE -> replaceLast(stem, 'o', "u")
                    StemChange.E_IE, StemChange.E_I -> replaceLast(stem, 'e', "i")
                    StemChange.U_UE -> stem
                }
                if (!ar) stem = orthographic(stem, inf, ending[0]) // elegir -> elijamos, seguir -> sigamos
            }
        }
        if (ar) {
            stem = when {
                inf.endsWith("car") -> stem.dropLast(1) + "qu"
                inf.endsWith("gar") -> stem + "u"
                inf.endsWith("zar") -> stem.dropLast(1) + "c"
                else -> stem
            }
        }
        if (inf in stressedIarUar && person == SpanishPerson.NOSOTROS) stem = stem(inf)
        return stem + ending
    }

    /** Negative tú command: "no hables", "no te preocupes". */
    fun negativeImperativeTu(infinitive: String): String {
        val parts = parse(infinitive)
        val verb = presentSubjunctive(parts.infinitive, SpanishPerson.TU)
        val core = if (parts.reflexive) "no te $verb" else "no $verb"
        return if (parts.tail.isEmpty()) core else "$core ${parts.tail}"
    }

    /** Affirmative tú imperative ("¡habla!", "¡ven!"). */
    fun imperativeTu(infinitive: String): String {
        val parts = parse(infinitive)
        val inf = parts.infinitive.lowercase()
        val irregular = mapOf(
            "tener" to "ten", "venir" to "ven", "poner" to "pon", "salir" to "sal", "hacer" to "haz",
            "decir" to "di", "ir" to "ve", "ser" to "sé",
        )
        val base = irregular[inf] ?: present(inf, SpanishPerson.EL)
        val withPronoun = if (parts.reflexive) SpanishText.attachUnstressed(base, "te") else base
        return if (parts.tail.isEmpty()) withPronoun else "$withPronoun ${parts.tail}"
    }

    /** Every finite form plus gerund and participle -- used to build the Spanish typing lexicon and reverse (ES->EN) lookups. */
    fun allForms(infinitive: String): Map<String, SpanishVerbFeatures> {
        val parts = parse(infinitive)
        val inf = parts.infinitive
        val result = LinkedHashMap<String, SpanishVerbFeatures>()
        result[inf] = SpanishVerbFeatures(null, null, VerbFormKind.INFINITIVE)
        for (tense in SpanishTense.entries) for (person in SpanishPerson.entries) {
            result.putIfAbsent(conjugate(inf, tense, person), SpanishVerbFeatures(tense, person, VerbFormKind.FINITE))
        }
        result.putIfAbsent(gerund(inf), SpanishVerbFeatures(null, null, VerbFormKind.GERUND))
        result.putIfAbsent(pastParticiple(inf), SpanishVerbFeatures(null, null, VerbFormKind.PARTICIPLE))
        return result
    }
}

enum class VerbFormKind { INFINITIVE, FINITE, GERUND, PARTICIPLE }

data class SpanishVerbFeatures(val tense: SpanishTense?, val person: SpanishPerson?, val kind: VerbFormKind)
