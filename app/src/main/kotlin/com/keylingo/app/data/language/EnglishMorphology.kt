package com.keylingo.app.data.language

/** What an English surface form tells us about the lemma it came from. */
enum class EnglishForm { BASE, THIRD_PERSON, PAST, PAST_PARTICIPLE, PAST_OR_PARTICIPLE, GERUND, PLURAL, COMPARATIVE, SUPERLATIVE }

data class EnglishAnalysis(val lemma: String, val form: EnglishForm)

/**
 * Deterministic English inflection analysis. Produces candidate
 * (lemma, form) pairs for a surface word; the caller keeps the first
 * candidate whose lemma exists in its dictionary. Irregular verbs are
 * table-driven; regular inflection uses the standard spelling rules
 * (consonant doubling, e-drop, y -> ie).
 */
object EnglishMorphology {

    /** base, past, past participle. */
    private val irregularVerbs: List<Triple<String, String, String>> = """
        be was/were been|have had had|do did done|go went gone|say said said|get got gotten/got|make made made
        know knew known|think thought thought|take took taken|see saw seen|come came come|give gave given
        find found found|tell told told|become became become|leave left left|feel felt felt|bring brought brought
        begin began begun|keep kept kept|hold held held|write wrote written|stand stood stood|hear heard heard
        let let let|mean meant meant|set set set|meet met met|run ran run|pay paid paid|sit sat sat|speak spoke spoken
        lie lay lain|lead led led|read read read|grow grew grown|lose lost lost|fall fell fallen|send sent sent
        build built built|understand understood understood|draw drew drawn|break broke broken|spend spent spent
        cut cut cut|rise rose risen|drive drove driven|buy bought bought|wear wore worn|choose chose chosen
        seek sought sought|throw threw thrown|catch caught caught|deal dealt dealt|win won won|forget forgot forgotten
        lay laid laid|sell sold sold|fight fought fought|eat ate eaten|teach taught taught|sing sang sung
        drink drank drunk|swim swam swum|fly flew flown|ride rode ridden|sleep slept slept|wake woke woken
        put put put|hurt hurt hurt|hit hit hit|shut shut shut|cost cost cost|quit quit quit|bet bet bet|beat beat beaten
        bite bit bitten|blow blew blown|dig dug dug|feed fed fed|forgive forgave forgiven|freeze froze frozen
        hang hung hung|hide hid hidden|kneel knelt knelt|light lit lit|ring rang rung|shake shook shaken
        shine shone shone|shoot shot shot|show showed shown|shrink shrank shrunk|sink sank sunk|slide slid slid
        steal stole stolen|stick stuck stuck|sting stung stung|strike struck struck|swear swore sworn|sweep swept swept
        swing swung swung|tear tore torn|wind wound wound|bend bent bent|bleed bled bled|breed bred bred|creep crept crept
        flee fled fled|lend lent lent|spin spun spun|split split split|spread spread spread|upset upset upset
        withdraw withdrew withdrawn|overcome overcame overcome|undertake undertook undertaken|forbid forbade forbidden
        can could could|will would would|shall should should|may might might|must must must
    """.trimIndent().split('|', '\n').map { it.trim() }.filter { it.isNotEmpty() }.map {
        val (base, past, participle) = it.split(' ')
        Triple(base, past, participle)
    }

    private val irregularPresent = mapOf(
        "am" to EnglishAnalysis("be", EnglishForm.BASE), "is" to EnglishAnalysis("be", EnglishForm.THIRD_PERSON),
        "are" to EnglishAnalysis("be", EnglishForm.BASE), "has" to EnglishAnalysis("have", EnglishForm.THIRD_PERSON),
        "does" to EnglishAnalysis("do", EnglishForm.THIRD_PERSON), "goes" to EnglishAnalysis("go", EnglishForm.THIRD_PERSON),
    )

    private val irregularPlurals = mapOf(
        "men" to "man", "women" to "woman", "children" to "child", "people" to "person", "feet" to "foot",
        "teeth" to "tooth", "mice" to "mouse", "geese" to "goose", "lives" to "life", "wives" to "wife",
        "knives" to "knife", "leaves" to "leaf", "halves" to "half", "shelves" to "shelf", "wolves" to "wolf",
        "potatoes" to "potato", "tomatoes" to "tomato", "heroes" to "hero",
    )

    private val irregularAdjectives = mapOf(
        "better" to EnglishAnalysis("good", EnglishForm.COMPARATIVE), "best" to EnglishAnalysis("good", EnglishForm.SUPERLATIVE),
        "worse" to EnglishAnalysis("bad", EnglishForm.COMPARATIVE), "worst" to EnglishAnalysis("bad", EnglishForm.SUPERLATIVE),
        "more" to EnglishAnalysis("much", EnglishForm.COMPARATIVE), "most" to EnglishAnalysis("much", EnglishForm.SUPERLATIVE),
    )

    private val pastLookup: Map<String, List<EnglishAnalysis>> = buildMap<String, MutableList<EnglishAnalysis>> {
        for ((base, pastField, participleField) in irregularVerbs) {
            val pasts = pastField.split('/')
            val participles = participleField.split('/')
            for (p in pasts) {
                val form = if (p in participles) EnglishForm.PAST_OR_PARTICIPLE else EnglishForm.PAST
                getOrPut(p) { mutableListOf() }.add(EnglishAnalysis(base, form))
            }
            for (pp in participles) if (pp !in pasts) getOrPut(pp) { mutableListOf() }.add(EnglishAnalysis(base, EnglishForm.PAST_PARTICIPLE))
        }
    }

    private val irregularBases: Set<String> = irregularVerbs.map { it.first }.toSet()

    fun isIrregularVerb(lemma: String) = lemma in irregularBases

    /** Past tense of a verb lemma ("go" -> "went", "play" -> "played"), for generating English glosses. */
    fun pastTense(lemma: String): String {
        irregularVerbs.firstOrNull { it.first == lemma }?.let { return it.second.substringBefore('/') }
        return regularSuffix(lemma, "ed")
    }

    fun thirdPerson(lemma: String): String = when {
        lemma == "be" -> "is"; lemma == "have" -> "has"
        lemma.endsWith("y") && lemma.length > 1 && lemma[lemma.length - 2] !in "aeiou" -> lemma.dropLast(1) + "ies"
        lemma.endsWith("s") || lemma.endsWith("x") || lemma.endsWith("z") || lemma.endsWith("ch") || lemma.endsWith("sh") || lemma.endsWith("o") -> lemma + "es"
        else -> lemma + "s"
    }

    fun gerund(lemma: String): String = when {
        lemma == "be" -> "being"
        lemma.endsWith("ie") -> lemma.dropLast(2) + "ying"
        lemma.endsWith("e") && !lemma.endsWith("ee") && lemma.length > 2 -> lemma.dropLast(1) + "ing"
        else -> regularSuffix(lemma, "ing")
    }

    private fun regularSuffix(lemma: String, suffix: String): String {
        if (suffix == "ed") {
            if (lemma.endsWith("e")) return lemma + "d"
            if (lemma.endsWith("y") && lemma.length > 1 && lemma[lemma.length - 2] !in "aeiou") return lemma.dropLast(1) + "ied"
        }
        return if (shouldDouble(lemma)) lemma + lemma.last() + suffix else lemma + suffix
    }

    /** CVC monosyllables double their final consonant (stop -> stopped); a curated list covers stressed-final multi-syllable verbs. */
    private fun shouldDouble(lemma: String): Boolean {
        if (lemma.length < 3) return false
        val c3 = lemma[lemma.length - 1]; val v = lemma[lemma.length - 2]; val c1 = lemma[lemma.length - 3]
        val vowels = "aeiou"
        if (c3 in vowels || c3 in "wxy" || v !in vowels || c1 in vowels) return false
        val vowelGroups = Regex("[aeiou]+").findAll(lemma).count()
        return vowelGroups == 1 || lemma in setOf("prefer", "admit", "commit", "forget", "begin", "occur", "refer", "regret", "permit", "control", "upset")
    }

    /**
     * Candidate analyses for [word] (already lowercased), most plausible
     * first. Always includes the word itself as a BASE candidate.
     */
    fun analyze(word: String): List<EnglishAnalysis> {
        val w = word.lowercase()
        val out = mutableListOf(EnglishAnalysis(w, EnglishForm.BASE))
        irregularPresent[w]?.let { out.add(0, it) }
        pastLookup[w]?.let { out.addAll(0, it) }
        irregularPlurals[w]?.let { out.add(EnglishAnalysis(it, EnglishForm.PLURAL)) }
        irregularAdjectives[w]?.let { out.add(it) }

        fun add(lemma: String, form: EnglishForm) {
            if (lemma.length >= 2 && out.none { it.lemma == lemma && it.form == form }) out.add(EnglishAnalysis(lemma, form))
        }

        when {
            w.endsWith("ying") && w.length > 5 -> { add(w.dropLast(4) + "ie", EnglishForm.GERUND); add(w.dropLast(3), EnglishForm.GERUND) }
            w.endsWith("ing") && w.length > 4 -> {
                val stem = w.dropLast(3)
                add(stem, EnglishForm.GERUND)
                add(stem + "e", EnglishForm.GERUND)
                if (stem.length > 2 && stem.last() == stem[stem.length - 2]) add(stem.dropLast(1), EnglishForm.GERUND)
            }
        }
        when {
            w.endsWith("ied") && w.length > 4 -> add(w.dropLast(3) + "y", EnglishForm.PAST_OR_PARTICIPLE)
            w.endsWith("ed") && w.length > 3 -> {
                val stem = w.dropLast(2)
                add(stem, EnglishForm.PAST_OR_PARTICIPLE)
                add(w.dropLast(1), EnglishForm.PAST_OR_PARTICIPLE) // liked -> like
                if (stem.length > 2 && stem.last() == stem[stem.length - 2]) add(stem.dropLast(1), EnglishForm.PAST_OR_PARTICIPLE)
            }
        }
        // Not mutually exclusive: "movies" -> movie (drop s) and "cities" -> city (ies -> y) both end in -ies.
        if (w.endsWith("ies") && w.length > 4) { add(w.dropLast(3) + "y", EnglishForm.PLURAL); add(w.dropLast(3) + "y", EnglishForm.THIRD_PERSON) }
        if (w.endsWith("es") && w.length > 3 && w.dropLast(2).let { it.endsWith("s") || it.endsWith("x") || it.endsWith("z") || it.endsWith("ch") || it.endsWith("sh") || it.endsWith("o") }) {
            add(w.dropLast(2), EnglishForm.PLURAL); add(w.dropLast(2), EnglishForm.THIRD_PERSON)
        }
        if (w.endsWith("s") && !w.endsWith("ss") && w.length > 2) { add(w.dropLast(1), EnglishForm.PLURAL); add(w.dropLast(1), EnglishForm.THIRD_PERSON) }
        if (w.endsWith("est") && w.length > 5) {
            val stem = w.dropLast(3)
            add(stem, EnglishForm.SUPERLATIVE); add(stem + "e", EnglishForm.SUPERLATIVE)
            if (stem.endsWith("i")) add(stem.dropLast(1) + "y", EnglishForm.SUPERLATIVE)
            if (stem.length > 2 && stem.last() == stem[stem.length - 2]) add(stem.dropLast(1), EnglishForm.SUPERLATIVE)
        } else if (w.endsWith("er") && w.length > 4) {
            val stem = w.dropLast(2)
            add(stem, EnglishForm.COMPARATIVE); add(stem + "e", EnglishForm.COMPARATIVE)
            if (stem.endsWith("i")) add(stem.dropLast(1) + "y", EnglishForm.COMPARATIVE)
            if (stem.length > 2 && stem.last() == stem[stem.length - 2]) add(stem.dropLast(1), EnglishForm.COMPARATIVE)
        }
        return out
    }
}
