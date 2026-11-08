package com.keylingo.app.data.translation

import com.keylingo.app.data.dictionary.BundledDictionary
import com.keylingo.app.data.dictionary.SpanishFormMatch
import com.keylingo.app.data.language.EnglishMorphology
import com.keylingo.app.data.language.spanish.SpanishPerson
import com.keylingo.app.data.language.spanish.SpanishTense
import com.keylingo.app.data.language.spanish.SpanishText
import com.keylingo.app.data.language.spanish.VerbFormKind
import com.keylingo.app.domain.model.PartOfSpeech

/**
 * Deterministic, offline Spanish -> English sentence translator, the mirror
 * of [EnglishToSpanishTranslator]. It reuses the dictionary's reverse index
 * of generated Spanish forms, so a conjugated verb is understood with its
 * tense and person ("fuimos" -> we went, "estás" -> you are) and English is
 * generated with the right subject, auxiliaries and word order:
 *
 * - pro-drop: the implied subject is restored from the verb ending;
 * - negation and questions use do-support (no entiendo -> I don't
 *   understand; ¿dónde vives? -> where do you live?) or inversion with be,
 *   will and can;
 * - estar + gerund, haber + participle, ir a + infinitive, verb +
 *   infinitive (quiero aprender -> I want to learn), acabar de;
 * - clitic pronouns before the verb become objects after it (te quiero ->
 *   I love you); gustar/encantar turn the indirect object into the subject
 *   (me gustan los perros -> I like dogs);
 * - tener and hacer idioms (tengo hambre -> I am hungry, hace frío -> it is
 *   cold), hay, me llamo;
 * - noun phrases: adjectives move before the noun, plurals, a/an, and the
 *   Spanish generic article is dropped where English has none.
 *
 * Unknown words are kept as typed and lower the coverage.
 */
class SpanishToEnglishTranslator(
    private val dictionary: BundledDictionary,
    /** Canonical English casing ("spanish" -> "Spanish", "monday" -> "Monday"). */
    private val englishCasing: (String) -> String = { it }
) {
    private data class Tok(val w: String, val raw: String, val punct: Boolean)

    private val tokenRegex = Regex("[\\p{L}]+|\\d+|[^\\s\\p{L}\\d]")

    private data class Subject(val person: SpanishPerson, val english: String, val explicit: Boolean)

    private val subjectPronouns = mapOf(
        "yo" to Subject(SpanishPerson.YO, "I", true), "tú" to Subject(SpanishPerson.TU, "you", true),
        "él" to Subject(SpanishPerson.EL, "he", true), "ella" to Subject(SpanishPerson.EL, "she", true),
        "usted" to Subject(SpanishPerson.EL, "you", true), "nosotros" to Subject(SpanishPerson.NOSOTROS, "we", true),
        "nosotras" to Subject(SpanishPerson.NOSOTROS, "we", true), "ellos" to Subject(SpanishPerson.ELLOS, "they", true),
        "ellas" to Subject(SpanishPerson.ELLOS, "they", true), "ustedes" to Subject(SpanishPerson.ELLOS, "you", true),
    )
    /** Verbs whose dropped third-person subject is almost always a thing: "¿Cuánto cuesta?" -> "How much does it cost?". */
    private val itSubjectVerbs = setOf("costar", "valer", "ocurrir", "suceder", "funcionar", "servir")
    private fun impliedSubject(p: SpanishPerson) = Subject(p, when (p) {
        SpanishPerson.YO -> "I"; SpanishPerson.TU -> "you"; SpanishPerson.EL -> "he/she"
        SpanishPerson.NOSOTROS -> "we"; SpanishPerson.ELLOS -> "they"
    }, false)

    private val clitics = mapOf(
        "me" to "me", "te" to "you", "lo" to "it", "la" to "her", "le" to "him", "nos" to "us", "los" to "them", "las" to "them", "les" to "them", "se" to "",
    )
    /** Indirect-object clitic -> English subject, for gustar-type verbs. */
    private val experiencer = mapOf(
        "me" to Subject(SpanishPerson.YO, "I", true), "te" to Subject(SpanishPerson.TU, "you", true), "le" to Subject(SpanishPerson.EL, "he/she", true),
        "nos" to Subject(SpanishPerson.NOSOTROS, "we", true), "les" to Subject(SpanishPerson.ELLOS, "they", true),
    )
    private val gustarLike = mapOf("gustar" to "like", "encantar" to "love", "importar" to "care about", "doler" to "hurt")

    private val determiners = mapOf(
        "el" to "the", "la" to "the", "los" to "the", "las" to "the", "un" to "a", "una" to "a", "unos" to "some", "unas" to "some",
        "mi" to "my", "mis" to "my", "tu" to "your", "tus" to "your", "su" to "his/her", "sus" to "their", "nuestro" to "our", "nuestra" to "our",
        "nuestros" to "our", "nuestras" to "our", "este" to "this", "esta" to "this", "estos" to "these", "estas" to "these", "ese" to "that",
        "esa" to "that", "esto" to "this", "eso" to "that", "esos" to "those", "esas" to "those", "cada" to "every", "mucho" to "a lot of", "mucha" to "a lot of", "muchos" to "many",
        "muchas" to "many", "otro" to "another", "otra" to "another", "otros" to "other", "otras" to "other", "algún" to "some", "alguna" to "some",
        "todo" to "all", "toda" to "all", "todos" to "all", "todas" to "all", "poco" to "a little", "poca" to "a little", "pocos" to "few", "pocas" to "few",
    )
    private val prepositions = mapOf(
        "en" to "in", "a" to "to", "de" to "of", "con" to "with", "para" to "for", "por" to "for", "sin" to "without", "sobre" to "about",
        "desde" to "from", "hasta" to "until", "entre" to "between", "hacia" to "toward", "contra" to "against", "durante" to "during",
        "al" to "to the", "del" to "of the",
    )
    private val conjunctions = mapOf("y" to "and", "e" to "and", "o" to "or", "u" to "or", "pero" to "but", "porque" to "because",
        "que" to "that", "si" to "if", "cuando" to "when", "mientras" to "while", "aunque" to "although", "entonces" to "then")
    private val whWords = mapOf("qué" to "what", "quién" to "who", "quiénes" to "who", "dónde" to "where", "adónde" to "where", "cuándo" to "when",
        "cómo" to "how", "cuál" to "which", "cuáles" to "which", "cuánto" to "how much", "cuánta" to "how much", "cuántos" to "how many", "cuántas" to "how many")
    private val adverbs = mapOf(
        "ya" to "already", "también" to "too", "muy" to "very", "más" to "more", "menos" to "less", "bien" to "well", "mal" to "badly",
        "aquí" to "here", "allí" to "there", "ahí" to "there", "hoy" to "today", "ayer" to "yesterday", "mañana" to "tomorrow", "ahora" to "now",
        "siempre" to "always", "nunca" to "never", "todavía" to "still", "después" to "later", "antes" to "before", "pronto" to "soon",
        "temprano" to "early", "casi" to "almost", "solo" to "only", "sólo" to "only", "anoche" to "last night", "tampoco" to "either", "juntos" to "together",
    )
    /** Adverbs English puts before the verb ("siempre como" -> I always eat). */
    private val preVerbAdverbs = setOf("always", "never", "already", "still", "almost", "only")
    private val tenerIdioms = mapOf("hambre" to "hungry", "sed" to "thirsty", "frío" to "cold", "calor" to "hot", "miedo" to "afraid",
        "sueño" to "sleepy", "razón" to "right", "suerte" to "lucky", "prisa" to "in a hurry")
    private val weather = mapOf("frío" to "cold", "calor" to "hot", "sol" to "sunny", "viento" to "windy", "buen tiempo" to "nice out")

    // ------------------------------------------------------------------ helpers

    /**
     * Accent-insensitive lookup, except that a function word typed exactly
     * never matches a different accented word: "te" (you) is not "té" (tea),
     * "el" (the) is not "él" (he), "tu" (your) is not "tú" (you).
     */
    private val accentDistinguished = setOf("te", "el", "tu", "mi", "si", "se", "de", "mas", "este", "esta", "ese", "esa", "aun", "que", "como", "donde", "cuando", "quien")
    private fun readings(w: String): List<SpanishFormMatch> {
        val all = dictionary.lookupSpanish(w)
        val exact = all.filter { it.surface == w }
        return if (exact.isNotEmpty() || w in accentDistinguished || clitics.containsKey(w) || determiners.containsKey(w)) exact else all
    }
    private fun verbReadings(w: String): List<SpanishFormMatch> = readings(w).filter { it.entry.partOfSpeech == PartOfSpeech.VERB }
        .sortedWith(compareBy({ if (it.surface == w) 0 else 1 }, { it.entry.frequencyRank }))
    private fun finite(w: String, preferred: SpanishPerson? = null) = verbReadings(w).filter { it.kind == VerbFormKind.FINITE }
        .let { r -> r.firstOrNull { preferred != null && it.person == preferred } ?: r.firstOrNull() }
    private fun nonFinite(w: String, kind: VerbFormKind) = verbReadings(w).firstOrNull { it.kind == kind }
        ?: splitEnclitic(w)?.first?.let { base -> verbReadings(base).firstOrNull { it.kind == kind } }

    private val encliticEnglish = mapOf("me" to "me", "te" to "you", "lo" to "it", "la" to "her", "le" to "him", "nos" to "us",
        "los" to "them", "las" to "them", "les" to "them", "se" to "")
    /** "ayudarte" -> ("ayudar", ["you"]), "dímelo" -> ("di", ["me", "it"]): a verb form with attached object pronouns. */
    private fun splitEnclitic(w: String): Pair<String, List<String>>? {
        var rest = w
        val found = mutableListOf<String>()
        repeat(2) {
            val c = encliticEnglish.keys.sortedByDescending { it.length }.firstOrNull { rest.length > it.length + 2 && rest.endsWith(it) } ?: return@repeat
            found.add(0, encliticEnglish.getValue(c)); rest = rest.dropLast(c.length)
        }
        if (found.isEmpty()) return null
        val base = SpanishText.fold(rest) // the accent added by attachment (ayudándote) is not part of the bare form
        return if (verbReadings(base).isNotEmpty()) base to found.filter { it.isNotEmpty() } else null
    }
    private fun encliticsOf(w: String): List<String> = if (verbReadings(w).isNotEmpty()) emptyList() else splitEnclitic(w)?.second.orEmpty()
    /** Preferred English sense when one Spanish noun has several (casa: house rather than home; US English soccer). */
    private val preferredNounSense = mapOf("casa" to "house", "futbol" to "soccer") // keys are accent-folded
    private fun noun(w: String): SpanishFormMatch? {
        val nouns = readings(w).filter { it.entry.partOfSpeech == PartOfSpeech.NOUN }
        val preferred = preferredNounSense[SpanishText.fold(w).removeSuffix("s")]
        return nouns.firstOrNull { it.entry.sourceWord == preferred } ?: nouns.minByOrNull { it.entry.frequencyRank }
    }
    private fun adjective(w: String) = readings(w).firstOrNull { it.entry.partOfSpeech == PartOfSpeech.ADJECTIVE }
    private fun otherWord(w: String) = readings(w).firstOrNull { it.entry.partOfSpeech != PartOfSpeech.VERB && it.entry.partOfSpeech != PartOfSpeech.NOUN }

    /** English lemma of a Spanish verb entry ("be called" stays multi-word). */
    private fun lemmaOf(m: SpanishFormMatch) = m.entry.sourceWord

    private fun article(word: String) = if ((word.firstOrNull()?.lowercaseChar() ?: 'x') in "aeiou" && !word.lowercase().startsWith("uni") && !word.lowercase().startsWith("eu")) "an" else "a"

    // ------------------------------------------------------------------ verb phrase

    private data class VerbPhrase(val aux: String?, val main: String, val negative: Boolean)

    private fun be(tense: SpanishTense, s: SpanishPerson, english: String): String = when (tense) {
        SpanishTense.PRESENT -> when { s == SpanishPerson.YO -> "am"; s == SpanishPerson.EL && english != "you" -> "is"; else -> "are" }
        SpanishTense.PRETERITE, SpanishTense.IMPERFECT -> if ((s == SpanishPerson.YO || s == SpanishPerson.EL) && english != "you") "was" else "were"
        SpanishTense.FUTURE -> "will be"
        SpanishTense.CONDITIONAL -> "would be"
    }

    /**
     * English verb phrase for [lemma] in [tense] with subject [s]. Returns the
     * auxiliary separately so questions can invert it and negation attaches to it.
     */
    private fun verbPhrase(lemma: String, tense: SpanishTense, s: Subject, negative: Boolean, question: Boolean): VerbPhrase {
        val third = s.person == SpanishPerson.EL && s.english != "you"
        val words = lemma.split(' ')
        val head = words.first(); val tail = words.drop(1).joinToString(" ").let { if (it.isEmpty()) "" else " $it" }
        if (head == "be") {
            val form = be(tense, s.person, s.english)
            return when {
                form.startsWith("will ") || form.startsWith("would ") -> VerbPhrase(form.substringBefore(' '), "be$tail", negative)
                else -> VerbPhrase(form, tail.trim(), negative)
            }
        }
        if (head == "can") {
            val modal = if (tense == SpanishTense.CONDITIONAL || tense == SpanishTense.PRETERITE || tense == SpanishTense.IMPERFECT) "could" else "can"
            return VerbPhrase(modal, tail.trim(), negative)
        }
        if (head == "should") return VerbPhrase(if (tense == SpanishTense.PRESENT) "should" else "should", tail.trim(), negative)
        return when (tense) {
            SpanishTense.FUTURE -> VerbPhrase("will", lemma, negative)
            SpanishTense.CONDITIONAL -> VerbPhrase("would", lemma, negative)
            SpanishTense.PRESENT ->
                if (negative || question) VerbPhrase(if (third) "does" else "do", lemma, negative)
                else VerbPhrase(null, (if (third) EnglishMorphology.thirdPerson(head) else head) + tail, false)
            SpanishTense.PRETERITE, SpanishTense.IMPERFECT ->
                if (negative || question) VerbPhrase("did", lemma, negative)
                else VerbPhrase(null, EnglishMorphology.pastTense(head) + tail, false)
        }
    }

    private fun negate(aux: String): String = when (aux) {
        "do" -> "don't"; "does" -> "doesn't"; "did" -> "didn't"; "will" -> "won't"; "would" -> "wouldn't"; "can" -> "can't"
        "could" -> "couldn't"; "should" -> "shouldn't"; "is" -> "isn't"; "are" -> "aren't"; "was" -> "wasn't"; "were" -> "weren't"
        "am" -> "am not"; "have" -> "haven't"; "has" -> "hasn't"; "had" -> "hadn't"
        else -> "$aux not"
    }

    // ------------------------------------------------------------------ parser

    private inner class Parser(val t: List<Tok>) {
        var i = 0
        val out = mutableListOf<String>()
        var known = 0
        var total = 0
        var question = false

        fun w(k: Int = 0): String? = t.getOrNull(i + k)?.takeIf { !it.punct }?.w
        fun countKnown(n: Int = 1) { known += n; total += n }

        /** A number at token [at] ("25", "veinte", "treinta y dos") -> (English, tokens used). */
        fun spanishNumber(at: Int): Pair<String, Int>? {
            val a = t.getOrNull(at)?.takeIf { !it.punct }?.w ?: return null
            if (a.all(Char::isDigit)) return a to 1
            val tens = numberTens[a]
            if (tens != null && t.getOrNull(at + 1)?.w == "y") {
                val unit = numberUnits[t.getOrNull(at + 2)?.w ?: ""]
                if (unit != null && unit in 1..9) return "${tens + unit}" to 3
            }
            val n = numberUnits[a] ?: tens ?: return null
            return (if (n <= 10) numberWordsEn[n] else "$n") to 1
        }

        fun run(): String {
            question = t.any { it.raw == "?" || it.raw == "¿" }
            while (i < t.size) {
                val start = i
                clause()
                if (i == start) {
                    val tok = t[i]
                    if (!tok.punct) { total++; out += tok.raw } else if (tok.raw == ",") out += ","
                    i++
                }
            }
            return out.joinToString(" ").replace(" ,", ",").replace(Regex("\\s+"), " ").trim()
        }

        fun clause() {
            val tok = t.getOrNull(i) ?: return
            if (tok.punct) { if (tok.raw == ",") out += ","; i++; return }
            whPlural = false
            conjunctions[tok.w]?.let { if (tok.w != "que" || out.isNotEmpty()) { out += it; i++; countKnown(); return } }

            // Fixed expressions.
            if (tok.w == "por" && w(1) == "qué") { i += 2; countKnown(2); return verbClause(wh = "why") }
            if (tok.w == "por" && w(1) == "favor") { i += 2; countKnown(2); out += "please"; return }
            if (tok.w == "buenos" && w(1) == "días") { i += 2; countKnown(2); out += "good morning"; return }
            if (tok.w == "buenas" && (w(1) == "noches" || w(1) == "tardes")) { out += if (w(1) == "noches") "good night" else "good afternoon"; i += 2; countKnown(2); return }
            if (tok.w == "de" && w(1) == "nada") { i += 2; countKnown(2); out += "you're welcome"; return }
            if (tok.w == "lo" && w(1) == "siento") { i += 2; countKnown(2); out += "I'm sorry"; return }
            if (tok.w == "yo" && w(1) == "también") { i += 2; countKnown(2); out += "me too"; return }
            for ((words, english) in fixedPhrases) {
                if (words.indices.all { k -> w(k) == words[k] } && (w(words.size) == null)) {
                    i += words.size; countKnown(words.size); out += english; return
                }
            }

            // ¿Cuántos años tienes? -> How old are you?
            if (tok.w == "cuántos" && w(1) == "años") {
                val v = w(2)?.let { finite(it) }
                if (v != null && SpanishText.fold(v.entry.baseTranslation) == "tener") {
                    i += 3; countKnown(3)
                    val subj = postverbalSubject(v.person ?: SpanishPerson.EL) ?: impliedSubject(v.person ?: SpanishPerson.EL)
                    emit("how old", subj, verbPhrase("be", v.tense ?: SpanishTense.PRESENT, subj, false, true), true)
                    complements(null, false); return
                }
            }
            if (tok.w == "qué" && w(1) == "hora" && w(2) == "es") { i += 3; countKnown(3); out += listOf("what", "time", "is", "it"); return }
            // ¿Cuántos hermanos tienes? -> How many brothers do you have?  ¿Qué libro lees? -> What book are you reading?
            if (tok.w in setOf("cuántos", "cuántas", "cuánto", "cuánta", "qué") && w(1)?.let { noun(it) != null && finite(it) == null } == true) {
                val n = noun(w(1)!!)!!
                val plural = n.plural || tok.w.endsWith("s")
                var nounEn = englishCasing(n.entry.sourceWord)
                if (plural && !n.entry.pluralOnly) nounEn = EnglishMorphology.plural(nounEn)
                val whWord = when (tok.w) { "qué" -> "what"; "cuántos", "cuántas" -> "how many"; else -> "how much" }
                i += 2; countKnown(2)
                whPlural = plural
                return verbClause("$whWord $nounEn")
            }
            whWords[tok.w]?.let { wh -> i++; countKnown(); return verbClause(wh) }
            verbClause(null)
        }

        /** The noun at [at] (after an optional pre-adjective) names a thing, not a person. */
        fun isThingPhrase(at: Int): Boolean {
            var k = at
            if (t.getOrNull(k)?.w in setOf("buen", "buena", "mal", "mala", "gran", "pequeño", "pequeña")) k++
            val n = t.getOrNull(k)?.takeIf { !it.punct }?.w?.let { noun(it) } ?: return false
            return n.entry.gender != "mf" && n.entry.sourceWord !in personNouns
        }

        /** Whether the current clause is negated (nada -> anything, nadie -> anybody). */
        var clauseNegative = false

        /** Set when the wh-phrase itself is plural ("cuántas personas hay" -> "are there"). */
        var whPlural = false

        /**
         * In a wh-question the subject may follow the verb: "¿Quién es ella?",
         * "¿Dónde vive tu hermano?". Takes a pronoun agreeing with [person], or a
         * third-person noun phrase that ends the clause.
         */
        fun postverbalSubject(person: SpanishPerson): Subject? {
            subjectPronouns[w()]?.let { p -> if (p.person == person) { i++; countKnown(); return p } }
            if (person != SpanishPerson.EL && person != SpanishPerson.ELLOS) return null
            if (w() == null || !(determiners.containsKey(w()) || noun(w()!!)?.entry?.sourceWord?.first()?.isUpperCase() == true)) return null
            val save = i; val k = known; val tt = total
            val np = nounPhrase(dropGenericArticle = false)
            if (np != null && (w() == null) && (np.second == (person == SpanishPerson.ELLOS))) return Subject(person, np.first, true)
            i = save; known = k; total = tt
            return null
        }

        /** [wh] = a question word already consumed. */
        fun verbClause(wh: String?) {
            // Clause-initial time adverbs keep their position: "Mañana voy" -> "Tomorrow I am going".
            var leadingTime = false
            while (wh == null && w() in setOf("hoy", "ayer", "mañana", "ahora", "anoche", "luego", "después") &&
                (i == 0 || t[i - 1].punct || conjunctions.containsKey(t[i - 1].w))) {
                out += adverbs.getValue(w()!!.let { if (it == "luego") "después" else it }); i++; countKnown(); leadingTime = true
            }
            // Subject: pronoun or noun phrase followed by a verb.
            var subject: Subject? = subjectPronouns[w()]?.also { i++; countKnown() }
            var subjectNp: String? = null
            // "la/los/las" are articles before a noun ("la comida estaba...") and pronouns before a verb ("la veo").
            val articleBeforeNoun = determiners.containsKey(w()) && w(1)?.let { noun(it) != null || adjective(it) != null } == true
            if (subject == null && w() != null && (articleBeforeNoun || !clitics.containsKey(w())) && w() != "no" && !adverbs.containsKey(w()) &&
                (determiners.containsKey(w()) || noun(w()!!) != null) && finite(w()!!) == null) {
                val save = i; val k = known; val tt = total
                val np = nounPhrase(dropGenericArticle = false) // a subject keeps "the": "La comida estaba deliciosa" -> "The food was delicious"
                val next = w()
                if (np != null && next != null && (finite(next) != null || next == "no" || clitics.containsKey(next))) {
                    subjectNp = np.first
                    subject = Subject(if (np.second) SpanishPerson.ELLOS else SpanishPerson.EL, np.first, true)
                } else { i = save; known = k; total = tt }
            }
            val preAdverbs = mutableListOf<String>()
            while (w() in setOf("siempre", "nunca", "ya", "todavía", "casi", "solo", "también", "tampoco") && w() != null) {
                val a = w()!!
                if (a == "también" && finite(w(1) ?: "") == null) break
                preAdverbs += adverbs.getValue(a); i++; countKnown()
            }
            var negative = false
            if (w() == "no") { negative = true; i++; countKnown() }
            clauseNegative = negative
            if (w() == "nunca") { preAdverbs += "never"; i++; countKnown() }
            val objectClitics = mutableListOf<String>()
            var experiencerClitic: String? = null
            while (w() != null && clitics.containsKey(w()) && t.getOrNull(i + 1)?.let { !it.punct && (finite(it.w) != null || clitics.containsKey(it.w)) } == true) {
                val c = w()!!
                if (experiencer.containsKey(c)) experiencerClitic = c
                objectClitics += c; i++; countKnown()
            }
            lastVerb = null
            val verbTok = w()
            if (verbTok == "hay") {
                i++; countKnown()
                val plural = whPlural || w()?.let { determiners[it] == "some" || determiners[it] == "many" || noun(it)?.plural == true } == true
                val beForm = if (plural) "are" else "is"
                if (wh != null) out += wh
                if (question) out += listOf(beForm, "there") else out += listOf("there", if (negative) negate(beForm) else beForm)
                complements(null, false)
                return
            }
            var verb = verbTok?.let { finite(it, subject?.person) }
            // fui/fue/fuimos/fueron are both ser and ir: followed by a/al/a casa it is "go".
            if (verb != null && verb.tense == SpanishTense.PRETERITE && verb.surface in setOf("fui", "fuiste", "fue", "fuimos", "fueron") && w(1) in setOf("a", "al")) {
                verb = verbReadings(verbTok!!).firstOrNull { it.kind == VerbFormKind.FINITE && it.entry.sourceWord == "go" } ?: verb
            }
            if (verb == null) {
                // No finite verb: a fragment ("en casa", "la casa grande", "mañana").
                if (wh != null) out += wh
                subjectNp?.let { out += it }
                complements(null, negative)
                return
            }
            i++; countKnown()
            val lemmaEs = SpanishText.fold(verb.entry.baseTranslation.substringBefore(' ').removeSuffix("se"))
            val english = lemmaOf(verb)
            val tense = verb.tense ?: SpanishTense.PRESENT
            val person = verb.person ?: SpanishPerson.EL

            // gustar-type: indirect object becomes the subject; the liked thing follows.
            // doler + body part: "me duele la cabeza" -> "my head hurts".
            if (lemmaEs == "doler" && experiencerClitic != null && w() in setOf("el", "la", "los", "las")) {
                val exp = experiencer.getValue(experiencerClitic!!)
                val possessive = when (exp.english) { "I" -> "my"; "you" -> "your"; "we" -> "our"; "they" -> "their"; "him" -> "his"; "her" -> "her"; else -> "his/her" }
                val save = i; val k = known; val tt = total
                i++; countKnown()
                val np = nounPhrase(dropGenericArticle = true)
                if (np != null) {
                    val part = Subject(if (np.second) SpanishPerson.ELLOS else SpanishPerson.EL, "$possessive ${np.first}", true)
                    emit(wh, part, verbPhrase("hurt", tense, part, negative, question), question)
                    complements(null, false); return
                }
                i = save; known = k; total = tt
            }
            gustarLike[lemmaEs]?.let { en ->
                val exp = experiencerClitic?.let { experiencer.getValue(it) } ?: subject ?: impliedSubject(person)
                val vp = verbPhrase(en, tense, exp, negative, question)
                emit(wh, exp, vp, question)
                // Nothing liked is named: "me gusta mucho" -> "I like it a lot".
                if (w() == null || w() in setOf("mucho", "muchísimo", "bastante", "también")) out += if (person == SpanishPerson.ELLOS) "them" else "it"
                complements("gustar", false, dropGeneric = true)
                return
            }
            // me llamo X -> my name is X; ¿cómo te llamas? -> what is your name?
            if (lemmaEs == "llamar" && objectClitics.isNotEmpty() && isReflexiveClitic(objectClitics.first(), person)) {
                val possessive = when (person) { SpanishPerson.YO -> "my"; SpanishPerson.TU -> "your"; SpanishPerson.NOSOTROS -> "our"; SpanishPerson.ELLOS -> "their"; else -> "his/her" }
                if (wh != null) out += listOf("what", "is", "$possessive name") else out += listOf("$possessive name", if (negative) "isn't" else "is")
                complements(null, false)
                return
            }
            val subj = subject
                ?: (if (wh != null && question) postverbalSubject(person) else null)
                ?: (if (person == SpanishPerson.EL && lemmaEs in itSubjectVerbs) Subject(person, "it", false) else null)
                // "Hoy es lunes" -> "Today is Monday": the time word is the English subject.
                ?: (if (leadingTime && lemmaEs == "ser" && person == SpanishPerson.EL) Subject(person, "", false) else null)
                // "Es un buen libro" -> "It is a good book" (but "Es un buen amigo" -> "He/she is a good friend").
                ?: (if (lemmaEs == "ser" && person == SpanishPerson.EL && w() in setOf("un", "una") && isThingPhrase(i + 1)) Subject(person, "it", false) else null)
                // "Es tarde / importante / posible" -> "It is late / important / possible".
                ?: (if (lemmaEs == "ser" && person == SpanishPerson.EL && tense != SpanishTense.PRETERITE && (SpanishText.fold(w() ?: "") in impersonalSerComplements || w() == "la" && w(1) == "hora")) Subject(person, "it", false) else null)
                ?: impliedSubject(person)
            // querer + a person pronoun object (and nothing else to want) = to love: "te quiero" -> "I love you".
            if (lemmaEs == "querer" && objectClitics.any { it in setOf("te", "lo", "la", "los", "las", "me", "nos", "le", "les") } &&
                (w() == null || t.getOrNull(i)?.punct == true)) {
                val vp = verbPhrase("love", tense, subj, negative, question)
                emit(wh, subj, vp, question, preAdverbs); addClitics(objectClitics)
                complements("love", false); return
            }
            // tener idioms and age; hacer weather; hay.
            if (lemmaEs == "tener" && w() != null && (tenerIdioms.containsKey(w()) || (spanishNumber(i) != null && w(spanishNumber(i)!!.second) == "años"))) {
                val vp = verbPhrase("be", tense, subj, negative, question)
                val age = spanishNumber(i)
                if (age != null) { emit(wh, subj, vp, question); out += listOf(age.first, "years old"); countKnown(age.second + 1); i += age.second + 1; return }
                val adj = tenerIdioms.getValue(w()!!); i++; countKnown()
                emit(wh, subj, vp, question); out += adj
                complements(null, false); return
            }
            if (lemmaEs == "tener" && w() == "que") {
                i++; countKnown()
                val inf = w()?.let { nonFinite(it, VerbFormKind.INFINITIVE) }
                if (inf != null) {
                    i++; countKnown()
                    val vp = verbPhrase("have", tense, subj, negative, question)
                    emit(wh, subj, vp, question); out += listOf("to", lemmaOf(inf)); addClitics(objectClitics)
                    complements(null, false); return
                }
            }
            val muchoWeather = w() in setOf("mucho", "mucha") && w(1)?.let { weather.containsKey(it) } == true
            if (lemmaEs == "hacer" && subject == null && w() != null && (weather.containsKey(w()) || muchoWeather)) {
                val vp = verbPhrase("be", tense, Subject(SpanishPerson.EL, "it", false), negative, question)
                emit(wh, Subject(SpanishPerson.EL, "it", false), vp, question)
                if (muchoWeather) { out += "very"; i++; countKnown() } // "hace mucho calor" -> "it is very hot"
                out += weather.getValue(w()!!); i++; countKnown(); return
            }
            if (lemmaEs == "haber" && verb.surface in setOf("había", "hubo", "habrá") && nonFinite(w() ?: "", VerbFormKind.PARTICIPLE) == null) {
                val plural = w()?.let { determiners[it] == "some" || it.endsWith("s") } == true
                val beForm = when { verb.surface == "habrá" -> "will be"; else -> if (plural) "were" else "was" }
                if (question) out += listOf(beForm, "there") else out += listOf("there", if (negative) negate(beForm) else beForm)
                complements(null, false); return
            }
            // Weather verbs and other impersonal verbs take "it".
            val impersonal = subject == null && (english in setOf("rain", "snow") ||
                english == "be" && w()?.let { nonFinite(it, VerbFormKind.GERUND) }?.let { lemmaOf(it) in setOf("rain", "snow") } == true)
            val realSubject = if (impersonal) Subject(SpanishPerson.EL, "it", false) else subj

            // Verb chains.
            val next = w()
            val gerund = next?.let { nonFinite(it, VerbFormKind.GERUND) }
            val participle = next?.let { nonFinite(it, VerbFormKind.PARTICIPLE) }
            val infinitive = next?.let { nonFinite(it, VerbFormKind.INFINITIVE) }
            when {
                english == "be" && lemmaEs == "estar" && gerund != null -> { // estar + gerund
                    i++; countKnown()
                    val vp = verbPhrase("be", tense, realSubject, negative, question)
                    emit(wh, realSubject, vp, question)
                    out += EnglishMorphology.gerund(lemmaOf(gerund).substringBefore(' ')) + lemmaOf(gerund).substringAfter(' ', "").let { if (it.isEmpty()) "" else " $it" }
                    addClitics(objectClitics)
                    lastVerb = lemmaOf(gerund)
                }
                lemmaEs == "haber" && participle != null -> { // haber + participle
                    i++; countKnown()
                    val third = realSubject.person == SpanishPerson.EL && realSubject.english != "you"
                    val aux = when (tense) { SpanishTense.PRESENT -> if (third) "has" else "have"; SpanishTense.FUTURE -> "will have"; else -> "had" }
                    emit(wh, realSubject, VerbPhrase(aux, EnglishMorphology.pastParticiple(lemmaOf(participle).substringBefore(' ')), negative), question)
                    addClitics(objectClitics)
                }
                lemmaEs == "ir" && next == "a" && w(1)?.let { nonFinite(it, VerbFormKind.INFINITIVE) } != null -> { // ir a + infinitive
                    val inf = nonFinite(w(1)!!, VerbFormKind.INFINITIVE)!!
                    i += 2; countKnown(2)
                    // "va a llover" -> "it is going to rain"
                    val s2 = if (subject == null && lemmaOf(inf) in setOf("rain", "snow")) Subject(SpanishPerson.EL, "it", false) else realSubject
                    val vp = verbPhrase("be", tense, s2, negative, question)
                    emit(wh, s2, vp, question); out += listOf("going to", lemmaOf(inf)); addClitics(objectClitics)
                    out += encliticsOf(w(-1)!!)
                    lastVerb = lemmaOf(inf)
                }
                lemmaEs == "acabar" && next == "de" && w(1)?.let { nonFinite(it, VerbFormKind.INFINITIVE) } != null -> {
                    val inf = nonFinite(w(1)!!, VerbFormKind.INFINITIVE)!!
                    i += 2; countKnown(2)
                    val third = realSubject.person == SpanishPerson.EL && realSubject.english != "you"
                    emit(wh, realSubject, VerbPhrase(if (third) "has" else "have", "just " + EnglishMorphology.pastParticiple(lemmaOf(inf).substringBefore(' ')), negative), question)
                    addClitics(objectClitics)
                }
                infinitive != null -> { // quiero aprender -> want to learn; puedo ir -> can go; puedo ayudarte -> can help you
                    i++; countKnown()
                    val vp = verbPhrase(english, tense, realSubject, negative, question)
                    emit(wh, realSubject, vp, question, preAdverbs)
                    if (english != "can" && english != "should") out += "to"
                    out += lemmaOf(infinitive)
                    addClitics(objectClitics)
                    out += encliticsOf(next!!)
                    lastVerb = lemmaOf(infinitive)
                }
                else -> {
                    // hablar + a language is "speak", not "talk".
                    // hablar is "speak" (a language, well, slowly) unless talking with someone or about something.
                    val verbEnglish = if (lemmaEs == "hablar" && w() !in setOf("con", "de", "sobre")) "speak" else english
                    val vp = verbPhrase(verbEnglish, tense, realSubject, negative, question)
                    emit(wh, realSubject, vp, question, preAdverbs)
                    addClitics(objectClitics)
                }
            }
            complements(lastVerb ?: english, false)
        }

        /** Lexical verb the complements attach to (the last verb of a chain like "voy a llamar"). */
        private var lastVerb: String? = null

        private fun isReflexiveClitic(clitic: String, person: SpanishPerson) =
            clitic == com.keylingo.app.data.language.spanish.SpanishConjugator.reflexivePronoun(person)

        private fun addClitics(cs: List<String>) {
            for (c in cs) clitics[c]?.takeIf { it.isNotEmpty() }?.let { out += it }
        }

        /** Writes [wh] + subject + verb phrase in statement or question order. */
        private fun emit(wh: String?, s: Subject, vp: VerbPhrase, question: Boolean, preAdverbs: List<String> = emptyList()) {
            val aux = vp.aux?.let { if (vp.negative) negate(it) else it }
            val main = vp.main
            val front = preAdverbs.filter { it in preVerbAdverbs }
            val back = preAdverbs.filter { it !in preVerbAdverbs }
            val subjectIsWh = wh != null && (wh == "who" || wh == "what") && !s.explicit && s.person == SpanishPerson.EL
            // "who" is the subject; "what" is the subject of happen-type verbs (¿Qué pasó? -> What happened?).
            val whIsSubject = subjectIsWh && (wh == "who" || main in setOf("happen", "occur"))
            when {
                whIsSubject && !vp.negative && vp.aux in setOf("do", "does", "did") -> {
                    out += wh!!
                    out += when (vp.aux) { "did" -> EnglishMorphology.pastTense(main); else -> EnglishMorphology.thirdPerson(main) }
                }
                subjectIsWh && aux == null -> { out += wh!!; out += main } // ¿Quién viene? -> who comes?
                question && aux != null && !whIsSubject -> {
                    if (wh != null) out += wh
                    out += aux; out += s.english; out += front; if (main.isNotEmpty()) out += main
                }
                else -> {
                    if (wh != null) out += wh
                    out += s.english
                    if (aux != null) { out += aux; out += front } else out += front
                    if (main.isNotEmpty()) out += main
                }
            }
            out += back
        }

        /** Objects, prepositional phrases, adverbs and adjectives after the verb. */
        fun complements(verbEnglish: String?, negative: Boolean, dropGeneric: Boolean = false) {
            var guard = 0
            var tooAtEnd = false
            while (i < t.size && guard++ < 40) {
                val tok = t[i]
                if (tok.punct) { if (tok.raw == ",") { out += ","; i++ }; break }
                val x = tok.w
                if (conjunctions.containsKey(x) && x != "que") break
                if (x == "que" || subjectPronouns.containsKey(x) && w(1)?.let { finite(it) } != null) break
                if (x == "también") { tooAtEnd = true; i++; countKnown(); continue }
                if (x == "conmigo") { out += "with me"; i++; countKnown(); continue }
                if (x == "contigo") { out += "with you"; i++; countKnown(); continue }
                if (x == "mí" || x == "ti") { out += if (x == "mí") "me" else "you"; i++; countKnown(); continue }
                if (x == "en" && w(1) == "casa") { out += "at home"; i += 2; countKnown(2); continue }
                // Bare "tarde" is late ("la tarde" is the afternoon).
                if (x == "tarde" || x == "temprano") { out += if (x == "tarde") "late" else "early"; i++; countKnown(); continue }
                if (x == "a" && w(1) in setOf("nadie", "alguien")) { i++; known++; total++; continue } // personal a
                // "Soy de México" -> "I am from Mexico"
                if (x == "de" && verbEnglish == "be" && t.getOrNull(i + 1)?.let { !it.punct && it.raw.first().isUpperCase() } == true) { out += "from"; i++; countKnown(); continue }
                if (x == "nada") { out += if (clauseNegative) "anything" else "nothing"; i++; countKnown(); continue }
                if (x == "nadie") { out += if (clauseNegative) "anybody" else "nobody"; i++; countKnown(); continue }
                if (x == "verdad" && verbEnglish == "be") { out += "true"; i++; countKnown(); continue }
                // "es (la) hora de comer" -> "it is time to eat"
                if ((x == "hora" || x == "la" && w(1) == "hora") && verbEnglish == "be") {
                    val at = if (x == "la") 1 else 0
                    val inf = if (w(at + 1) == "de") w(at + 2)?.let { nonFinite(it, VerbFormKind.INFINITIVE) } else null
                    if (inf != null) { out += listOf("time", "to", lemmaOf(inf)); i += at + 3; countKnown(at + 3); continue }
                }
                if ((x == "desde" || x == "de") && w(1) == "casa") { out += "from home"; i += 2; countKnown(2); continue }
                if (x == "al" && w(1) == "trabajo") { out += "to work"; i += 2; countKnown(2); continue }
                if (x == "en" && w(1) == "el" && w(2) == "trabajo") { out += "at work"; i += 3; countKnown(3); continue }
                if (x == "a" && w(1) == "la" && w(2) in institutionNouns) { out += listOf("to", institutionNouns.getValue(w(2)!!)); i += 3; countKnown(3); continue }
                if (x == "en" && w(1) == "la" && w(2) in institutionNouns && w(2) != "cama") { out += listOf("at", institutionNouns.getValue(w(2)!!)); i += 3; countKnown(3); continue }
                if (x == "en" && w(1) in vehicles) { out += listOf("by", vehicles.getValue(w(1)!!)); i += 2; countKnown(2); continue }
                if (x == "a" && w(1) == "pie") { out += "on foot"; i += 2; countKnown(2); continue }
                // en + a surface is "on": "en la mesa" -> "on the table".
                if (x == "en" && w(1) in setOf("el", "la", "los", "las") && w(2)?.let { SpanishText.fold(it) } in surfaceNouns) { out += "on"; i++; countKnown(); continue }
                // jugar al fútbol -> play soccer
                if (x == "al" && verbEnglish == "play") { i++; countKnown(); continue }
                if (x == "por" && w(1) == "la" && w(2) in setOf("noche", "mañana", "tarde")) {
                    out += when (w(2)) { "noche" -> "at night"; "mañana" -> "in the morning"; else -> "in the afternoon" }; i += 3; countKnown(3); continue
                }
                if ((x == "mucho" || x == "mucha") && w(1)?.let { noun(it) } == null) { out += "a lot"; i++; countKnown(); continue }
                if (x == "a" && w(1) == "casa") { out += "home"; i += 2; countKnown(2); continue }
                if ((x == "la" || x == "esta") && w(1) == "mañana") { out += if (x == "esta") "this morning" else "the morning"; i += 2; countKnown(2); continue }
                // personal "a" before a person: drop it
                if (x == "a" && w(1)?.let { determiners.containsKey(it) || noun(it)?.entry?.gender == "mf" } == true && verbEnglish != null && verbEnglish !in setOf("go", "come", "arrive", "return", "travel", "walk", "drive", "fly", "run")) {
                    i++; known++; total++; continue
                }
                val prep = prepositions[x]
                if (prep != null) {
                    i++; countKnown()
                    val inf = w()?.let { nonFinite(it, VerbFormKind.INFINITIVE) }
                    when {
                        inf != null && x != "a" -> { out += prep; out += EnglishMorphology.gerund(lemmaOf(inf).substringBefore(' ')); i++; countKnown() }
                        inf != null -> { out += "to"; out += lemmaOf(inf); i++; countKnown() }
                        else -> {
                            // "al"/"del" already contain "the": keep it only once.
                            val np = nounPhrase(dropGenericArticle = false, afterArticle = if (prep.endsWith(" the")) "the" else null)
                            out += if (np != null && np.first.startsWith("the ")) prep.removeSuffix(" the") else prep
                            if (np != null) out += np.first
                        }
                    }
                    continue
                }
                val adv = adverbs[x]
                if (adv != null && !(x == "mañana" && i > 0 && t[i - 1].w in setOf("la", "esta", "por"))) {
                    out += adv; i++; countKnown(); continue
                }
                // After "be" a bare singular noun takes "a": "era niño" -> "I was a child", "soy estudiante" -> "I am a student".
                val np = nounPhrase(dropGenericArticle = dropGeneric, bareArticle = verbEnglish == "be")
                if (np != null) { out += np.first; continue }
                val adj = adjective(x)
                if (adj != null) { out += englishCasing(adj.entry.sourceWord); i++; countKnown(); continue }
                val inf = nonFinite(x, VerbFormKind.INFINITIVE)
                if (inf != null) { out += listOf("to", lemmaOf(inf)); i++; countKnown(); continue }
                if (finite(x) != null) break // a second clause without a conjunction
                val other = otherWord(x)
                if (other != null) { out += englishCasing(other.entry.sourceWord); i++; countKnown(); continue }
                // Unknown: keep as typed (names stay meaningful); lowers coverage unless capitalised.
                out += tok.raw; total++; if (tok.raw.first().isUpperCase()) known++; i++
            }
            if (tooAtEnd) out += if (out.isEmpty()) "also" else "too"
        }

        /** det? (adj|noun)* noun adj* -> English NP. Returns (text, plural). */
        fun nounPhrase(dropGenericArticle: Boolean, afterArticle: String? = null, bareArticle: Boolean = false): Pair<String, Boolean>? {
            val start = i; val k0 = known; val t0 = total
            var det: String? = null
            var detEs: String? = null
            if (w() != null && determiners.containsKey(w())) { detEs = w(); det = determiners[w()]; i++ }
            val pre = mutableListOf<String>()
            if (w() != null && determiners[w()] == null && w() in setOf("buen", "buena", "mal", "mala", "gran", "primer", "primera", "último", "última", "próximo", "próxima")) {
                adjective(when (w()) { "buen" -> "bueno"; "mal" -> "malo"; "gran" -> "grande"; "primer" -> "primero"; else -> w()!! })?.let { pre += it.entry.sourceWord }
                i++
            }
            val n = w()?.let { noun(it) }
            if (n == null) {
                // Demonstrative pronouns: "esto es...", "esta es mi casa", "esos son míos".
                if (det != null && detEs in setOf("esto", "eso", "este", "ese", "esta", "esa", "estos", "estas", "esos", "esas")) {
                    known++; total++; return Pair(det, detEs!!.endsWith("s"))
                }
                i = start; known = k0; total = t0; return null
            }
            val surface = w()!!
            i++
            val plural = n.plural || detEs?.endsWith("s") == true
            var nounEn = englishCasing(n.entry.sourceWord)
            if (plural && !n.entry.pluralOnly) nounEn = mixedPlurals[surface] ?: EnglishMorphology.plural(nounEn)
            val post = mutableListOf<String>()
            while (w() != null) {
                val a = adjective(w()!!) ?: break
                if (noun(w()!!) != null && finite(w()!!) == null && dictionary.lookupSpanish(w()!!).first().entry.partOfSpeech == PartOfSpeech.NOUN) break
                post += a.entry.sourceWord; i++
            }
            // "de" + noun complement: la casa de mi madre -> my mother's house is overkill; keep "the house of ..." readable.
            val count = 1 + pre.size + post.size + (if (det != null) 1 else 0)
            known += count; total += count
            val isProper = nounEn.first().isUpperCase()
            val englishDet = when {
                det == null && bareArticle && pre.isEmpty() && !plural && !isProper && !isAbstract(n.entry.sourceWord) -> article(post.firstOrNull() ?: nounEn)
                det == null -> null
                isProper -> null
                det == "the" && (dropGenericArticle && (plural || isAbstract(n.entry.sourceWord))) -> null
                det == "the" && n.entry.sourceWord in setOf("dinner", "lunch", "breakfast") -> null // "cocinando la cena" -> "cooking dinner"
                det == "a" -> article((post + pre).firstOrNull() ?: nounEn)
                det == "his/her" && plural -> "their"
                det == "all" -> "all the"
                else -> det
            }.let { if (afterArticle != null && it == "the") null else it }
            val text = (listOfNotNull(englishDet) + pre + post + nounEn).joinToString(" ")
            return Pair(text, plural)
        }

        private fun isAbstract(english: String) = english in setOf("music", "coffee", "tea", "water", "food", "pizza", "chocolate", "money", "love",
            "art", "sport", "soccer", "football", "spanish", "english", "french", "german", "italian", "life", "nature", "history", "science", "wine", "beer")
    }

    private val numberUnits: Map<String, Int> = listOf("cero", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve", "diez",
        "once", "doce", "trece", "catorce", "quince", "dieciséis", "diecisiete", "dieciocho", "diecinueve", "veinte",
        "veintiuno", "veintidós", "veintitrés", "veinticuatro", "veinticinco", "veintiséis", "veintisiete", "veintiocho", "veintinueve")
        .withIndex().associate { (n, w) -> w to n } + mapOf("un" to 1, "una" to 1, "veintiún" to 21, "dieciseis" to 16, "veintidos" to 22, "veintitres" to 23, "veintiseis" to 26)
    private val numberTens = mapOf("treinta" to 30, "cuarenta" to 40, "cincuenta" to 50, "sesenta" to 60, "setenta" to 70, "ochenta" to 80, "noventa" to 90, "cien" to 100)
    private val numberWordsEn = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten")

    /** Whole-clause set phrases (matched only when nothing follows them in the clause). */
    private val fixedPhrases: List<Pair<List<String>, String>> = listOf(
        listOf("mucho", "gusto") to "nice to meet you", listOf("encantado") to "nice to meet you", listOf("encantada") to "nice to meet you",
        listOf("qué", "tal") to "how are you", listOf("hasta", "luego") to "see you later", listOf("hasta", "mañana") to "see you tomorrow",
        listOf("hasta", "pronto") to "see you soon", listOf("con", "permiso") to "excuse me", listOf("buen", "provecho") to "enjoy your meal",
        listOf("qué", "pasa") to "what's up", listOf("no", "pasa", "nada") to "it's okay", listOf("claro", "que", "sí") to "of course",
        listOf("por", "supuesto") to "of course", listOf("me", "da", "igual") to "I don't mind",
    )
    private val personNouns = setOf("man", "woman", "boy", "girl", "friend", "person", "teacher", "doctor", "student", "child", "kid", "guy",
        "professor", "brother", "sister", "father", "mother", "son", "daughter", "neighbor", "boss", "husband", "wife", "boyfriend", "girlfriend",
        "baby", "lady", "gentleman", "sir", "player", "writer", "artist", "actor", "actress", "singer", "nurse", "lawyer", "engineer", "worker")
    private val impersonalSerComplements = setOf("tarde", "temprano", "importante", "necesario", "posible", "imposible", "dificil", "facil",
        "verdad", "mejor", "peor", "hora", "obvio", "normal", "increible", "raro", "interesante", "urgente")
    private val institutionNouns = mapOf("escuela" to "school", "iglesia" to "church", "cama" to "bed", "universidad" to "college", "clase" to "class")
    private val vehicles = mapOf("autobús" to "bus", "autobus" to "bus", "bus" to "bus", "carro" to "car", "coche" to "car", "auto" to "car",
        "tren" to "train", "avión" to "plane", "avion" to "plane", "metro" to "subway", "bicicleta" to "bike", "taxi" to "taxi", "barco" to "boat")
    private val surfaceNouns = setOf("mesa", "cama", "suelo", "piso", "pared", "sofa", "silla", "escritorio", "techo", "estante", "mesas", "sillas")
    /** Masculine plurals that cover both sexes: "mis padres" -> "my parents". */
    private val mixedPlurals = mapOf("padres" to "parents", "hijos" to "children", "abuelos" to "grandparents", "papás" to "parents", "papas" to "parents")

    private val unaccentedWh = mapOf("como" to "cómo", "donde" to "dónde", "adonde" to "adónde", "que" to "qué", "cuando" to "cuándo",
        "quien" to "quién", "cual" to "cuál", "cuanto" to "cuánto", "cuanta" to "cuánta", "cuantos" to "cuántos", "cuantas" to "cuántas")

    /**
     * Questions typed without accents ("Como estas?"): a clause-initial wh-word is
     * the interrogative (cómo), and the word right after it is read as a verb if
     * only its accented spelling is one (estas -> estás). Only applies to questions.
     */
    private fun restoreQuestionAccents(tokens: List<Tok>): List<Tok> {
        if (tokens.none { it.raw == "?" || it.raw == "¿" }) return tokens
        val out = tokens.toMutableList()
        val first = out.indexOfFirst { !it.punct }
        if (first < 0) return tokens
        val wh = unaccentedWh[out[first].w]
        val porQue = out[first].w == "por" && out.getOrNull(first + 1)?.w == "que"
        val whIndex = when { wh != null -> first; porQue -> first + 1; else -> return tokens }
        out[whIndex] = out[whIndex].let { it.copy(w = unaccentedWh.getValue(it.w), raw = it.raw) }
        val next = whIndex + 1
        val v = out.getOrNull(next)?.takeIf { !it.punct } ?: return out
        if (verbReadings(v.w).none { it.kind == VerbFormKind.FINITE }) {
            val accented = dictionary.lookupSpanish(v.w).firstOrNull { it.entry.partOfSpeech == PartOfSpeech.VERB && it.kind == VerbFormKind.FINITE }
            if (accented != null) out[next] = v.copy(w = accented.surface)
        }
        return out
    }

    fun translate(sentence: String): TranslationResult {
        val tokens = tokenRegex.findAll(sentence).map { m ->
            val raw = m.value
            Tok(raw.lowercase(), raw, !raw.first().isLetterOrDigit())
        }.toList().let(::restoreQuestionAccents)
        val parser = Parser(tokens)
        val body = parser.run()
        val trimmed = sentence.trimEnd()
        val text = when {
            body.isEmpty() -> ""
            parser.question -> body.replaceFirstChar { it.titlecase() } + "?"
            trimmed.endsWith("!") -> body.replaceFirstChar { it.titlecase() } + "!"
            else -> body.replaceFirstChar { it.titlecase() } + if (trimmed.endsWith(".")) "." else ""
        }
        val coverage = if (parser.total == 0) (if (body.isEmpty()) 0f else 1f) else (parser.known.toFloat() / parser.total).coerceIn(0f, 1f)
        return TranslationResult(text, emptyList(), coverage, parser.total, parser.question)
    }
}
