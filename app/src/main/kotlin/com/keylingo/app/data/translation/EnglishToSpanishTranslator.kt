package com.keylingo.app.data.translation

import com.keylingo.app.data.dictionary.BundledDictionary
import com.keylingo.app.data.language.EnglishAnalysis
import com.keylingo.app.data.language.EnglishForm
import com.keylingo.app.data.language.spanish.Gender
import com.keylingo.app.data.language.spanish.SpanishConjugator
import com.keylingo.app.data.language.spanish.SpanishMorphology
import com.keylingo.app.data.language.spanish.SpanishPerson
import com.keylingo.app.data.language.spanish.SpanishTense
import com.keylingo.app.data.language.spanish.SpanishText
import com.keylingo.app.domain.model.DictionaryEntry
import com.keylingo.app.domain.model.PartOfSpeech

/** One aligned piece of a translation: which English words produced which Spanish words, and why. */
data class TranslationSegment(
    val sourceText: String,
    /** Text that goes into the assembled sentence (may be empty, e.g. a dropped subject pronoun). */
    val targetText: String,
    val partOfSpeech: PartOfSpeech,
    /** Human-readable grammar note shown with a hint, e.g. "present · yo", "noun · f", "phrase". */
    val formLabel: String,
    val entry: DictionaryEntry?,
    /** Content words (nouns, verbs, adjectives, adverbs, phrases) are hint-worthy; function words are not. */
    val isContent: Boolean,
    val confidence: Float,
    /** What to show as a word hint; usually [targetText]. A dropped "yo" still teaches "I -> yo". */
    val hintText: String = targetText
)

data class TranslationResult(
    val text: String,
    val segments: List<TranslationSegment>,
    /** Share of content words that were actually translated (0..1). */
    val coverage: Float,
    val contentWordCount: Int,
    val isQuestion: Boolean
)

/** A fixed idiomatic expression ("thank you" -> "gracias"). Grammar is handled by rules, not by these. */
data class Idiom(val english: String, val spanish: String)

/**
 * Deterministic, offline English -> Spanish translator for the short,
 * conversational sentences people type on a phone. It is a small grammar,
 * not a phrasebook: output is generated from the bundled lemma dictionary
 * plus rules --
 *
 * - clause parsing: subject (pronoun or noun phrase), verb group
 *   (do-support, negation, modals, be + -ing, going to, have + participle,
 *   have to, used to, let's, would like), complements, prepositional
 *   phrases, adverbs, time expressions;
 * - tense/person mapping onto [SpanishConjugator]; subject pronouns
 *   yo/tú/nosotros are dropped as natural Spanish does (the verb ending
 *   carries the person) but still surface as hints;
 * - ser vs. estar, "tener" idioms (hungry -> tener hambre), weather
 *   "hace frío", the "gustar" construction (I like you -> me gustas),
 *   clitic pronoun placement (no lo sé, te voy a llamar, llámame);
 * - noun-phrase agreement (articles, demonstratives, possessives, adjective
 *   position/apocope and gender/number agreement, "N1 N2" -> "N2 de N1",
 *   a + el -> al, de + el -> del);
 * - Spanish question/exclamation punctuation.
 *
 * Words it cannot translate are left as typed and lower
 * [TranslationResult.coverage]; callers hide low-coverage output rather than
 * show a misleading partial translation.
 */
class EnglishToSpanishTranslator(
    private val dictionary: BundledDictionary,
    idioms: List<Idiom>
) {

    // ------------------------------------------------------------ tokens

    private data class Tok(val w: String, val raw: String, val punct: Boolean)

    private val tokenRegex = Regex("[A-Za-z]+(?:['’][A-Za-z]+)?|\\d+(?:[.,]\\d+)?|[^\\sA-Za-z\\d]")

    private val contractions: Map<String, List<String>> = mapOf(
        "i'm" to listOf("i", "am"), "you're" to listOf("you", "are"), "we're" to listOf("we", "are"),
        "they're" to listOf("they", "are"), "he's" to listOf("he", "is"), "she's" to listOf("she", "is"),
        "it's" to listOf("it", "is"), "that's" to listOf("that", "is"), "there's" to listOf("there", "is"),
        "what's" to listOf("what", "is"), "where's" to listOf("where", "is"), "who's" to listOf("who", "is"),
        "how's" to listOf("how", "is"), "here's" to listOf("here", "is"),
        "i've" to listOf("i", "have"), "you've" to listOf("you", "have"), "we've" to listOf("we", "have"), "they've" to listOf("they", "have"),
        "i'll" to listOf("i", "will"), "you'll" to listOf("you", "will"), "he'll" to listOf("he", "will"), "she'll" to listOf("she", "will"),
        "it'll" to listOf("it", "will"), "we'll" to listOf("we", "will"), "they'll" to listOf("they", "will"),
        "i'd" to listOf("i", "would"), "you'd" to listOf("you", "would"), "he'd" to listOf("he", "would"), "she'd" to listOf("she", "would"),
        "we'd" to listOf("we", "would"), "they'd" to listOf("they", "would"),
        "don't" to listOf("do", "not"), "doesn't" to listOf("does", "not"), "didn't" to listOf("did", "not"),
        "can't" to listOf("can", "not"), "cannot" to listOf("can", "not"), "won't" to listOf("will", "not"),
        "wouldn't" to listOf("would", "not"), "shouldn't" to listOf("should", "not"), "couldn't" to listOf("could", "not"),
        "isn't" to listOf("is", "not"), "aren't" to listOf("are", "not"), "wasn't" to listOf("was", "not"),
        "weren't" to listOf("were", "not"), "haven't" to listOf("have", "not"), "hasn't" to listOf("has", "not"),
        "hadn't" to listOf("had", "not"), "mustn't" to listOf("must", "not"), "let's" to listOf("let", "us"),
        "gonna" to listOf("going", "to"), "wanna" to listOf("want", "to"), "gotta" to listOf("have", "to"),
        "im" to listOf("i", "am"), "dont" to listOf("do", "not"), "didnt" to listOf("did", "not"),
        "doesnt" to listOf("does", "not"), "isnt" to listOf("is", "not"), "ive" to listOf("i", "have"),
    )

    private fun tokenize(text: String): List<Tok> {
        val out = mutableListOf<Tok>()
        for (m in tokenRegex.findAll(text)) {
            val raw = m.value
            val lower = raw.lowercase().replace('’', '\'')
            if (!raw.first().isLetterOrDigit()) { out.add(Tok(raw, raw, true)); continue }
            val expansion = contractions[lower]
            when {
                expansion != null -> {
                    out.add(Tok(expansion[0], raw, false))
                    for (k in 1 until expansion.size) out.add(Tok(expansion[k], "", false))
                }
                lower.endsWith("'s") -> out.add(Tok(lower.dropLast(2), raw, false))
                else -> out.add(Tok(lower, raw, false))
            }
        }
        return out
    }

    private val idiomTokens: List<Pair<List<String>, String>> = idioms
        .map { idiom -> tokenize(idiom.english).filter { !it.punct }.map { it.w } to idiom.spanish }
        .filter { it.first.isNotEmpty() }
        .sortedByDescending { it.first.size }

    // ------------------------------------------------------ word classes

    private data class Subject(
        val person: SpanishPerson,
        val gender: Gender,
        /** Spanish rendering; empty when Spanish drops it. */
        val spanish: String,
        val plural: Boolean = person.isPlural,
        /** True for "it"/impersonal subjects (weather, "it is good"). */
        val impersonal: Boolean = false
    )

    private val subjectPronouns: Map<String, Pair<Subject, String>> = mapOf(
        "i" to (Subject(SpanishPerson.YO, Gender.MASCULINE, "") to "yo"),
        "you" to (Subject(SpanishPerson.TU, Gender.MASCULINE, "") to "tú"),
        "he" to (Subject(SpanishPerson.EL, Gender.MASCULINE, "él") to "él"),
        "she" to (Subject(SpanishPerson.EL, Gender.FEMININE, "ella") to "ella"),
        "it" to (Subject(SpanishPerson.EL, Gender.MASCULINE, "", impersonal = true) to ""),
        "we" to (Subject(SpanishPerson.NOSOTROS, Gender.MASCULINE, "") to "nosotros"),
        "they" to (Subject(SpanishPerson.ELLOS, Gender.MASCULINE, "ellos") to "ellos"),
        "everyone" to (Subject(SpanishPerson.ELLOS, Gender.MASCULINE, "todos") to "todos"),
        "everybody" to (Subject(SpanishPerson.ELLOS, Gender.MASCULINE, "todos") to "todos"),
        "someone" to (Subject(SpanishPerson.EL, Gender.MASCULINE, "alguien") to "alguien"),
        "somebody" to (Subject(SpanishPerson.EL, Gender.MASCULINE, "alguien") to "alguien"),
        "nobody" to (Subject(SpanishPerson.EL, Gender.MASCULINE, "nadie") to "nadie"),
        "everything" to (Subject(SpanishPerson.EL, Gender.MASCULINE, "todo") to "todo"),
        "nothing" to (Subject(SpanishPerson.EL, Gender.MASCULINE, "nada") to "nada"),
        "something" to (Subject(SpanishPerson.EL, Gender.MASCULINE, "algo") to "algo"),
    )

    private val objectPronouns = setOf("me", "you", "him", "her", "it", "us", "them")

    /** Verbs whose pronoun object is indirect in Spanish ("tell me" -> "me dice", "give him" -> "le da"). */
    private val indirectObjectVerbs = setOf(
        "give", "tell", "say", "send", "show", "bring", "ask", "write", "buy", "explain", "lend", "pay", "offer", "sell",
        "text", "teach", "answer", "reply",
    )

    private val auxiliaries = setOf("do", "does", "did", "will", "would", "can", "could", "should", "must", "might", "may",
        "am", "is", "are", "was", "were", "have", "has", "had")

    private val whWords = mapOf(
        "what" to "qué", "where" to "dónde", "when" to "cuándo", "why" to "por qué", "how" to "cómo", "who" to "quién", "which" to "cuál",
    )
    private val relativeWh = mapOf(
        "what" to "lo que", "where" to "donde", "when" to "cuando", "why" to "por qué", "how" to "como", "who" to "quien", "which" to "que",
    )
    /** After these verbs a wh-word introduces an indirect question and keeps its accent ("no sé dónde está"). */
    private val indirectQuestionVerbs = setOf("know", "ask", "wonder", "tell", "understand", "remember", "forget", "see", "explain", "decide")

    private val prepositions = mapOf(
        "in" to "en", "on" to "en", "at" to "en", "into" to "en", "with" to "con", "for" to "para", "from" to "de", "of" to "de",
        "about" to "sobre", "without" to "sin", "after" to "después de", "before" to "antes de", "during" to "durante",
        "near" to "cerca de", "under" to "debajo de", "behind" to "detrás de", "between" to "entre", "like" to "como",
        "by" to "por", "around" to "alrededor de", "inside" to "dentro de", "outside" to "fuera de", "until" to "hasta",
        "since" to "desde", "over" to "sobre", "through" to "por", "to" to "a", "against" to "contra", "toward" to "hacia",
    )

    private val conjunctions = mapOf(
        "and" to "y", "or" to "o", "but" to "pero", "because" to "porque", "so" to "así que", "if" to "si", "when" to "cuando",
        "that" to "que", "while" to "mientras", "although" to "aunque", "then" to "luego",
    )

    /** Adjectives describing a state/condition, which take "estar". */
    private val estarAdjectives = setOf(
        "tired", "happy", "sad", "sick", "ill", "busy", "ready", "angry", "bored", "worried", "nervous", "excited", "scared",
        "surprised", "married", "alone", "calm", "open", "closed", "full", "empty", "dirty", "clean", "wet", "dry", "dead",
        "alive", "sure", "wrong", "upset", "confused", "glad", "free", "available", "quiet", "comfortable", "lost", "drunk",
        "awake", "asleep", "broken", "finished", "done", "pregnant", "jealous", "lonely", "proud", "grateful", "unhappy", "cheerful",
    )

    /** "to be X" expressed as "tener" + noun. */
    private val tenerIdioms = mapOf(
        "hungry" to Pair("hambre", Gender.FEMININE), "thirsty" to Pair("sed", Gender.FEMININE), "sleepy" to Pair("sueño", Gender.MASCULINE),
        "afraid" to Pair("miedo", Gender.MASCULINE), "scared" to Pair("miedo", Gender.MASCULINE), "cold" to Pair("frío", Gender.MASCULINE),
        "hot" to Pair("calor", Gender.MASCULINE), "lucky" to Pair("suerte", Gender.FEMININE), "right" to Pair("razón", Gender.FEMININE),
    )

    private val weatherWithHacer = mapOf("cold" to "frío", "hot" to "calor", "sunny" to "sol", "windy" to "viento", "warm" to "calor")

    /** Spanish verbs that need a preposition before a following infinitive. */
    private val infinitivePreposition = mapOf(
        "aprender" to "a", "empezar" to "a", "comenzar" to "a", "ir" to "a", "venir" to "a", "ayudar" to "a", "enseñar" to "a",
        "volver" to "a", "tratar" to "de", "dejar" to "de", "terminar" to "de", "acabar" to "de", "invitar" to "a",
    )

    /** Adjectives that normally precede the noun (with apocope: buen día, primer día, gran idea). */
    private val preNominalAdjectives = setOf("good", "bad", "first", "next", "great", "few", "many", "several", "own", "best", "worst", "better", "worse")

    private val numberWords = setOf("one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve", "twenty", "thirty", "hundred", "thousand")

    private val determiners = setOf(
        "the", "a", "an", "this", "that", "these", "those", "my", "your", "his", "her", "its", "our", "their",
        "some", "any", "every", "each", "no", "many", "much", "few", "several", "another", "other",
    ) + numberWords

    private val pluralDeterminers = setOf("these", "those", "many", "few", "several") + (numberWords - "one")

    private val timeNouns = setOf("week", "month", "year", "weekend", "morning", "afternoon", "evening", "night", "day", "time",
        "summer", "winter", "spring", "autumn", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")

    /** Verbs that take a finite clause complement, introduced by "que" even when English omits "that". */
    private val clauseTakingVerbs = setOf("think", "hope", "know", "say", "believe", "guess", "feel", "wish", "tell", "want", "suppose", "promise", "see", "hear", "mean", "understand", "remember", "forget", "imagine", "expect")
    /** ...of which these require the subjunctive in the subordinate clause. */
    private val subjunctiveVerbs = setOf("hope", "want", "wish", "prefer", "need", "ask", "expect")
    private val humanNouns = setOf("mother", "father", "mom", "dad", "son", "daughter", "brother", "sister", "family", "friend", "wife", "husband",
        "boyfriend", "girlfriend", "baby", "kid", "child", "boy", "girl", "man", "woman", "people", "person", "parents", "grandmother",
        "grandfather", "uncle", "aunt", "cousin", "nephew", "niece", "neighbor", "boss", "teacher", "doctor", "guy", "lady")
    private val institutionalNouns = setOf("work", "school", "church", "class", "college", "university", "hospital", "bed", "office")

    /** Verb-specific prepositions: "think about" -> "pensar en", "listen to" -> direct object (""). */
    private val verbPrepositions: Map<Pair<String?, String>, String> = mapOf(
        ("think" to "about") to "en", ("talk" to "about") to "de", ("speak" to "about") to "de", ("dream" to "about") to "con",
        ("dream" to "of") to "con", ("worry" to "about") to "por", ("depend" to "on") to "de", ("fall" to "in") to "en",
        ("listen" to "to") to "", ("look" to "at") to "", ("wait" to "for") to "", ("look" to "for") to "", ("ask" to "for") to "",
        ("pay" to "for") to "", ("arrive" to "at") to "a", ("arrive" to "in") to "a", ("get" to "to") to "a", ("marry" to "to") to "con",
    )

    private val motionVerbs = setOf("go", "come", "return", "arrive", "get", "walk", "drive", "run", "fly", "travel")

    // ----------------------------------------------------------- output

    private class Out {
        val segments = mutableListOf<TranslationSegment>()
        var contentTotal = 0
        var contentTranslated = 0

        fun add(src: String, tgt: String, pos: PartOfSpeech, label: String, entry: DictionaryEntry? = null,
                content: Boolean = false, conf: Float = 0.9f, hint: String = tgt) {
            if (tgt.isEmpty() && src.isEmpty()) return
            if (content) { contentTotal++; contentTranslated++ }
            segments.add(TranslationSegment(src.trim(), tgt, pos, label, entry, content, conf, hint))
        }

        fun insertAt(index: Int, src: String, tgt: String, label: String) {
            segments.add(index.coerceIn(0, segments.size), TranslationSegment(src, tgt, PartOfSpeech.PRONOUN, label, null, false, 0.85f))
        }
    }

    private fun label(tense: SpanishTense, person: SpanishPerson): String {
        val t = when (tense) {
            SpanishTense.PRESENT -> "present"; SpanishTense.PRETERITE -> "past"; SpanishTense.IMPERFECT -> "imperfect"
            SpanishTense.FUTURE -> "future"; SpanishTense.CONDITIONAL -> "conditional"
        }
        val p = when (person) {
            SpanishPerson.YO -> "yo"; SpanishPerson.TU -> "tú"; SpanishPerson.EL -> "él/ella"
            SpanishPerson.NOSOTROS -> "nosotros"; SpanishPerson.ELLOS -> "ellos"
        }
        return "$t · $p"
    }

    private fun genderOf(entry: DictionaryEntry): Gender = when (entry.gender) {
        "f" -> Gender.FEMININE
        "m", "mf" -> Gender.MASCULINE
        else -> SpanishMorphology.guessGender(entry.baseTranslation)
    }

    private fun genderLabel(g: Gender, plural: Boolean) = (if (g == Gender.FEMININE) "f" else "m") + if (plural) " pl" else ""

    // ----------------------------------------------------------- parser

    private inner class Parser(val t: List<Tok>, val out: Out) {
        var i = 0
        var isQuestion = false
        /** Segment index where the question clause begins, so "¿" lands there ("Hola, ¿cómo estás?"). */
        var questionStart = -1
        /** Set right after "ser" so a following "a/an + person noun" agrees with the subject ("ella es maestra"). */
        var predicateOf: Subject? = null
        var lastSubject = Subject(SpanishPerson.EL, Gender.MASCULINE, "")

        // Per-clause state
        var cliticSlot = -1
        /** Set by a governing verb/conjunction (hope, want, when + future context): present-tense verbs of the next clause use the subjunctive. */
        var subjunctiveClause = false
        var pendingSubjunctive = false
        /** The sentence so far talks about the future or gives a command -- "when/until/before" clauses then take the subjunctive. */
        var futureContext = false
        var negatedClause = false
        /** Index of the last infinitive segment and how many non-finite verbs the chain has ("can't wait to see"). */
        var lastInfinitiveSegment = -1
        var nonFiniteVerbs = 0
        var mainVerbLemma: String? = null
        /** Last conjugated lexical verb: segment index, tense, person -- so "think" can become "creer" once a clause follows. */
        var lastFinite: Triple<Int, SpanishTense, SpanishPerson>? = null
        var gustarActive = false
        var clauseHasVerb = false

        fun peek(k: Int = 0): Tok? = t.getOrNull(i + k)
        fun word(k: Int = 0): String? = peek(k)?.takeIf { !it.punct }?.w
        fun wordAt(k: Int): String? = t.getOrNull(k)?.takeIf { !it.punct }?.w
        fun rawOf(from: Int, to: Int): String = t.subList(from, to).joinToString(" ") { it.raw }.replace(Regex("\\s+"), " ").trim()
        fun atClauseStart(): Boolean = i == 0 || t[i - 1].punct

        fun markQuestion() {
            if (!isQuestion) { isQuestion = true; questionStart = out.segments.size }
        }

        /** Moves a noun-phrase subject behind the verb in an inverted question: "¿Dónde vive tu hermano?". */
        fun postposeSubject(from: Int, to: Int) {
            if (to <= from || to > out.segments.size) return
            val moved = out.segments.subList(from, to).toList()
            repeat(to - from) { out.segments.removeAt(from) }
            out.segments.addAll(moved)
        }

        fun nouns(w: String) = dictionary.resolveEnglish(w, setOf(PartOfSpeech.NOUN))
        fun verbs(w: String) = dictionary.resolveEnglish(w, setOf(PartOfSpeech.VERB))
        fun adjectives(w: String) = dictionary.resolveEnglish(w, setOf(PartOfSpeech.ADJECTIVE))

        fun isVerbAt(k: Int): Boolean {
            val w = wordAt(k) ?: return false
            return w !in auxiliaries && verbs(w).isNotEmpty()
        }

        /** A noun that is not also a verb (so it cannot start an imperative/verb group). */
        fun isPureNounAt(k: Int): Boolean {
            val w = wordAt(k) ?: return false
            return nouns(w).isNotEmpty() && verbs(w).isEmpty()
        }

        fun looksLikeSubjectAt(k: Int): Boolean {
            val w = wordAt(k) ?: return false
            if (w in subjectPronouns) return true
            if (w in setOf("the", "my", "your", "his", "her", "our", "their", "this", "that", "these", "those")) return true
            return isPureNounAt(k)
        }

        fun run() {
            while (i < t.size) {
                val start = i
                parseClause()
                if (i == start) { // nothing consumed: pass the token through so we always make progress
                    val tok = t[i]
                    if (tok.punct) punct(tok) else unknown(tok)
                    i++
                }
            }
        }

        private fun punct(tok: Tok) {
            if (tok.raw in setOf("?", "!", "¿", "¡", ".")) return // sentence punctuation is re-added by translate()
            out.add(tok.raw, tok.raw, PartOfSpeech.OTHER, "punct")
        }

        private fun unknown(tok: Tok) {
            if (tok.raw.isEmpty()) return
            val first = tok.raw.first()
            when {
                first.isDigit() -> out.add(tok.raw, tok.raw, PartOfSpeech.OTHER, "number")
                first.isUpperCase() && i > 0 && tok.w != "i" -> { // a name: keep as typed, counts as understood
                    out.add(tok.raw, tok.raw, PartOfSpeech.OTHER, "name", content = true)
                }
                else -> {
                    out.contentTotal++
                    out.add(tok.raw, tok.raw, PartOfSpeech.OTHER, "untranslated", conf = 0f)
                }
            }
        }

        private fun resetClause() {
            cliticSlot = -1; mainVerbLemma = null; gustarActive = false; clauseHasVerb = false
            negatedClause = false; lastInfinitiveSegment = -1; nonFiniteVerbs = 0
            subjunctiveClause = pendingSubjunctive; pendingSubjunctive = false
        }

        fun conj(inf: String, tense: SpanishTense, person: SpanishPerson): String =
            if (subjunctiveClause && tense == SpanishTense.PRESENT) SpanishConjugator.presentSubjunctive(inf, person)
            else SpanishConjugator.conjugate(inf, tense, person)

        fun parseClause() {
            resetClause()
            val tok = peek() ?: return
            if (tok.punct) { punct(tok); i++; return }
            val w = tok.w
            if (matchIdiom()) return

            conjunctions[w]?.let { es ->
                val isConj = when (w) {
                    "that" -> looksLikeSubjectAt(i + 1)
                    "so" -> wordAt(i + 1)?.let { adjectives(it).isEmpty() } ?: true
                    else -> true
                }
                if (isConj) {
                    out.add(tok.raw, conjunction(w, es), PartOfSpeech.CONJUNCTION, "conjunction"); i++
                    if (w == "when" && futureContext) pendingSubjunctive = true
                    return
                }
            }

            if (w in whWords) { whQuestion(); return }

            if (w == "there" && word(1) in setOf("is", "are", "was", "were")) { thereIs(); return }

            if (w in setOf("until", "before") && futureContext && looksLikeSubjectAt(i + 1)) {
                out.add(tok.raw, if (w == "until") "hasta que" else "antes de que", PartOfSpeech.CONJUNCTION, "conjunction"); i++
                pendingSubjunctive = true
                return
            }
            // Aux inversion: "do you ...", "are you ...", "can I ...".
            // Negative command: "don't worry" -> "no te preocupes".
            if (w == "do" && word(1) == "not" && atClauseStartOrAfterPlease() && isVerbAt(i + 2)) {
                val entry = verbs(t[i + 2].w).first().first
                val command = SpanishConjugator.negativeImperativeTu(entry.baseTranslation).removePrefix("no ")
                out.add(rawOf(i, i + 2), "no", PartOfSpeech.ADVERB, "negation")
                cliticSlot = out.segments.size // object pronouns go between "no" and the verb: "no me llames"
                futureContext = true
                out.add(t[i + 2].raw, command, PartOfSpeech.VERB, "negative command · tú", entry, content = true)
                i += 3
                clauseHasVerb = true; mainVerbLemma = entry.sourceWord
                parseComplements(Subject(SpanishPerson.TU, Gender.MASCULINE, ""))
                return
            }
            if (w == "let" && word(1) == "us") { // let's ... -> vamos (a) ...
                i += 2
                clauseHasVerb = true
                val you = Subject(SpanishPerson.NOSOTROS, Gender.MASCULINE, "")
                if (word() == "go") {
                    out.add(rawOf(i - 2, i + 1), "vamos", PartOfSpeech.VERB, "let's go · ir", dictionary.sense("go", PartOfSpeech.VERB), content = true)
                    i++; mainVerbLemma = "go"
                } else {
                    out.add(t[i - 2].raw, "vamos a", PartOfSpeech.VERB, "let's · ir a", content = true)
                    cliticSlot = out.segments.size - 1
                    mainVerb(you, SpanishTense.PRESENT, finite = false)
                }
                parseComplements(you)
                return
            }
            if (w in auxiliaries && looksLikeSubjectAt(i + 1)) {
                if (atClauseStart()) markQuestion()
                invertedClause()
                return
            }

            val subject = parseSubject(inverted = false)
            if (subject != null) {
                lastSubject = subject
                val next = word()
                if (next == null || (next in conjunctions && next != "that")) return
                if (parseVerbGroup(subject, null)) parseComplements(subject)
                return
            }
            // No subject: an imperative ("call me", "please come") or a fragment ("at home", "tomorrow").
            if (atClauseStart() && isVerbAt(i) && !isPureNounAt(i)) {
                futureContext = true
                parseImperative()
                parseComplements(Subject(SpanishPerson.TU, Gender.MASCULINE, ""))
                return
            }
            parseComplements(lastSubject)
        }

        /** "think" + clause expresses an opinion: Spanish uses "creer" (creo que...), not "pensar". */
        private fun thinkAsBelieve() {
            if (mainVerbLemma != "think") return
            val (index, tense, person) = lastFinite ?: return
            val seg = out.segments.getOrNull(index) ?: return
            val creer = SpanishConjugator.conjugate("creer", tense, person)
            out.segments[index] = seg.copy(targetText = creer, hintText = creer, formLabel = "creer · opinion")
        }

        private fun atClauseStartOrAfterPlease(): Boolean = atClauseStart() || (i > 0 && t[i - 1].w == "please")

        /** "do you ...", "where does your brother live": aux already at [i]; subject follows. */
        private fun invertedClause() {
            val auxIndex = i; i++
            val subjFrom = out.segments.size
            val subject = parseSubject(inverted = true) ?: lastSubject
            val subjTo = out.segments.size
            lastSubject = subject
            val nounSubject = subject.spanish.isNotEmpty() && subjTo > subjFrom
            if (parseVerbGroup(subject, auxIndex)) {
                if (nounSubject) postposeSubject(subjFrom, subjTo)
                parseComplements(subject)
            }
        }

        private fun conjunction(w: String, es: String): String = when (w) {
            "and" -> SpanishText.conjunctionAnd(nextSpanishWord())
            "or" -> SpanishText.conjunctionOr(nextSpanishWord())
            else -> es
        }

        private fun nextSpanishWord(): String? = wordAt(i + 1)?.let { dictionary.resolveEnglish(it).firstOrNull()?.first?.baseTranslation }

        private fun matchIdiom(): Boolean {
            for ((words, spanish) in idiomTokens) {
                val n = words.size
                if (i + n > t.size) continue
                var ok = true
                for (k in 0 until n) if (t[i + k].punct || t[i + k].w != words[k]) { ok = false; break }
                if (!ok) continue
                out.add(rawOf(i, i + n), spanish, PartOfSpeech.OTHER, "phrase", content = true, conf = 0.95f)
                i += n
                return true
            }
            return false
        }

        private fun whQuestion() {
            val tok = peek()!!
            val w = tok.w
            if (atClauseStart()) markQuestion()
            val whSegment = out.segments.size
            if (w == "how" && (word(1) == "much" || word(1) == "many")) {
                out.add(rawOf(i, i + 2), if (word(1) == "many") "cuántos" else "cuánto", PartOfSpeech.PRONOUN, "question word"); i += 2
            } else {
                out.add(tok.raw, whWords.getValue(w), PartOfSpeech.PRONOUN, "question word"); i++
            }
            // "how are you" -> "cómo estás": state, so estar.
            if (w == "how" && word() in setOf("am", "is", "are", "was", "were") && (wordAt(i + 1) == null || wordAt(i + 1) in subjectPronouns)) {
                val be = peek()!!; i++
                val subject = parseSubject(inverted = true) ?: lastSubject
                val tense = if (be.w == "was" || be.w == "were") SpanishTense.IMPERFECT else SpanishTense.PRESENT
                out.add(be.raw, conj("estar", tense, subject.person), PartOfSpeech.VERB, "estar · ${label(tense, subject.person)}")
                parseComplements(subject)
                return
            }
            // "what is your name" -> "¿Cuál es tu nombre?", "where is my phone" -> "¿Dónde está mi teléfono?"
            val next = word()
            if (next != null && next in setOf("is", "are", "was", "were") && looksLikeSubjectAt(i + 1) && wordAt(i + 1) !in subjectPronouns) {
                val be = peek()!!; i++
                val verbIndex = out.segments.size
                val subject = parseSubject(inverted = true) ?: lastSubject
                val estar = w == "where" || w == "how"
                val past = be.w == "was" || be.w == "were"
                val tense = when {
                    !past -> SpanishTense.PRESENT
                    w == "how" || w == "when" -> SpanishTense.PRETERITE // "¿Cómo estuvo tu día?"
                    else -> SpanishTense.IMPERFECT
                }
                val verb = conj(if (estar) "estar" else "ser", tense, subject.person)
                out.segments.add(verbIndex, TranslationSegment(be.raw, verb, PartOfSpeech.VERB, "${if (estar) "estar" else "ser"} · ${label(tense, subject.person)}", null, false, 0.9f))
                val subjectText = out.segments.getOrNull(verbIndex + 1)?.targetText.orEmpty()
                if (w == "what" && subjectText !in setOf("esto", "eso")) {
                    out.segments[whSegment] = out.segments[whSegment].copy(targetText = "cuál", hintText = "cuál")
                }
                parseComplements(subject)
            } else if (next != null && next in auxiliaries && looksLikeSubjectAt(i + 1)) {
                invertedClause()
            } else if (next != null && next in auxiliaries) {
                // "who is coming", "what happened": the wh-word itself is the subject.
                val subject = Subject(SpanishPerson.EL, Gender.MASCULINE, "")
                if (parseVerbGroup(subject, null)) parseComplements(subject)
            } else if (next != null && isVerbAt(i)) {
                val subject = Subject(SpanishPerson.EL, Gender.MASCULINE, "")
                if (parseVerbGroup(subject, null)) parseComplements(subject)
            }
        }

        private fun thereIs() {
            val be = word(1)!!
            out.add(rawOf(i, i + 2), if (be == "is" || be == "are") "hay" else "había", PartOfSpeech.VERB, "haber · there is/are", content = true)
            i += 2
            if (word() == "not" || word() == "no") {
                out.insertAt(out.segments.size - 1, peek()!!.raw, "no", "negation"); i++
            }
            parseComplements(Subject(SpanishPerson.EL, Gender.MASCULINE, ""))
        }

        // ------------------------------------------------------ subject

        fun parseSubject(inverted: Boolean): Subject? {
            val w = word() ?: return null
            subjectPronouns[w]?.let { (s, hint) ->
                val raw = peek()!!.raw
                i++
                if (word() == "and" && word(1) == "i") { // "you and I"
                    out.add(rawOf(i - 1, i + 2), "", PartOfSpeech.PRONOUN, "subject", hint = "$hint y yo"); i += 2
                    return Subject(SpanishPerson.NOSOTROS, Gender.MASCULINE, "")
                }
                // In inverted questions Spanish drops or postposes the pronoun; dropping is always grammatical.
                val spanish = if (inverted) "" else s.spanish
                out.add(raw, spanish, PartOfSpeech.PRONOUN, "subject", hint = hint)
                return s
            }
            if (w in setOf("this", "that") && word(1) in auxiliaries) {
                out.add(peek()!!.raw, if (w == "this") "esto" else "eso", PartOfSpeech.PRONOUN, "subject"); i++
                return Subject(SpanishPerson.EL, Gender.MASCULINE, "eso")
            }
            val save = i
            val segmentsBefore = out.segments.size
            val np = parseNounPhrase(definiteIfBare = true)
            if (np == null) { i = save; return null }
            val next = word()
            val verbFollows = next == null || next in auxiliaries || isVerbAt(i)
            if (!verbFollows) { // not a subject after all -- roll back and let complements handle it
                while (out.segments.size > segmentsBefore) out.segments.removeAt(out.segments.size - 1)
                out.contentTotal -= np.contentWords; out.contentTranslated -= np.contentWords
                i = save
                return null
            }
            return Subject(if (np.plural) SpanishPerson.ELLOS else SpanishPerson.EL, np.gender, np.text, np.plural)
        }

        // --------------------------------------------------- verb group

        private fun takeNot(): Boolean {
            val w = word()
            if (w == "not" || w == "never") {
                out.add(peek()!!.raw, if (w == "never") "nunca" else "no", PartOfSpeech.ADVERB, "negation"); i++
                negatedClause = true
                return true
            }
            return false
        }

        /** Parses auxiliaries + main verb. [auxIndex] is set when the auxiliary was already consumed by question inversion. */
        fun parseVerbGroup(subject: Subject, auxIndex: Int?): Boolean {
            if (auxIndex == null && word() == "just" && wordAt(i + 1)?.let { w ->
                    verbs(w).firstOrNull()?.second?.form.let { it == EnglishForm.PAST || it == EnglishForm.PAST_OR_PARTICIPLE }
                } == true) {
                // "I just got home" -> "acabo de llegar a casa"
                val justTok = peek()!!; i++
                cliticSlot = out.segments.size
                out.add(justTok.raw, conj("acabar", SpanishTense.PRESENT, subject.person) + " de", PartOfSpeech.VERB, "acabar de · just did", content = true)
                return mainVerb(subject, SpanishTense.PRESENT, finite = false)
            }
            val aux = if (auxIndex != null) t[auxIndex] else peek()?.takeIf { !it.punct && it.w in auxiliaries + setOf("used", "let") }
            if (aux == null) {
                takeNot()
                if (!isVerbAt(i)) return false
                cliticSlot = out.segments.size
                return mainVerb(subject, SpanishTense.PRESENT, finite = true)
            }
            if (auxIndex == null) i++
            takeNot()
            cliticSlot = out.segments.size
            clauseHasVerb = true
            when (aux.w) {
                "do", "does", "did" -> {
                    val tense = if (aux.w == "did") SpanishTense.PRETERITE else SpanishTense.PRESENT
                    if (!isVerbAt(i) && word() != "like" && word() !in setOf("have", "do", "be")) { // "I do", "did you?" -> hacer
                        out.add(aux.raw, conj("hacer", tense, subject.person), PartOfSpeech.VERB, label(tense, subject.person))
                        return true
                    }
                    return mainVerb(subject, tense, finite = true, forcedTense = true)
                }
                "will", "would" -> {
                    val tense = if (aux.w == "will") SpanishTense.FUTURE else SpanishTense.CONDITIONAL
                    if (aux.w == "will") futureContext = true
                    if (aux.w == "would" && (word() == "like" || word() == "love")) { // "I would like/love (to) ..." -> "me gustaría / me encantaría"
                        val love = word() == "love"
                        val likeRaw = peek()!!.raw; i++
                        if (word() == "to") i++
                        gustar(subject, SpanishTense.CONDITIONAL, "${aux.raw} $likeRaw", likedPerson = SpanishPerson.EL, verb = if (love) "encantar" else "gustar")
                        if (isVerbAt(i)) mainVerb(subject, SpanishTense.PRESENT, finite = false)
                        return true
                    }
                    return mainVerb(subject, tense, finite = true, forcedTense = true)
                }
                "can", "could", "should", "must", "might", "may" -> {
                    if (aux.w == "might" || aux.w == "may") {
                        out.insertAt(cliticSlot, aux.raw, "tal vez", "maybe")
                        cliticSlot++
                        return mainVerb(subject, SpanishTense.PRESENT, finite = true, forcedTense = true)
                    }
                    val (verb, tense) = when (aux.w) {
                        "can" -> "poder" to SpanishTense.PRESENT
                        "could" -> "poder" to SpanishTense.CONDITIONAL
                        "should" -> "deber" to SpanishTense.CONDITIONAL
                        else -> "deber" to SpanishTense.PRESENT
                    }
                    out.add(aux.raw, conj(verb, tense, subject.person), PartOfSpeech.VERB, "$verb · ${label(tense, subject.person)}", content = true)
                    if (word() == "be") { i++; return beAfterModal(subject) }
                    return mainVerb(subject, tense, finite = false)
                }
                "am", "is", "are", "was", "were" -> return beGroup(subject, aux)
                "have", "has", "had" -> return haveGroup(subject, aux)
                "used" -> {
                    if (word() == "to") { i++; return mainVerb(subject, SpanishTense.IMPERFECT, finite = true, forcedTense = true) }
                    i--
                    return mainVerb(subject, SpanishTense.PRESENT, finite = true)
                }
                "let" -> {
                    if (word() == "us") {
                        i++
                        out.add(aux.raw, "vamos a", PartOfSpeech.VERB, "let's · ir a", content = true)
                        return mainVerb(subject, SpanishTense.PRESENT, finite = false)
                    }
                    i--
                    return mainVerb(subject, SpanishTense.PRESENT, finite = true)
                }
            }
            return false
        }

        private fun participleAt(k: Int): DictionaryEntry? {
            val w = wordAt(k) ?: return null
            return verbs(w).firstOrNull {
                it.second.form == EnglishForm.PAST_PARTICIPLE || it.second.form == EnglishForm.PAST_OR_PARTICIPLE
            }?.first
        }

        private fun haveGroup(subject: Subject, aux: Tok): Boolean {
            val past = aux.w == "had"
            if (word() == "to" || (word() == "got" && word(1) == "to")) {
                if (word() == "got") i++
                i++
                val tense = if (past) SpanishTense.PRETERITE else SpanishTense.PRESENT
                out.add("${aux.raw} to", conj("tener", tense, subject.person) + " que", PartOfSpeech.VERB, "tener que · obligation", content = true)
                return mainVerb(subject, tense, finite = false)
            }
            if (word() == "been") {
                val beenTok = peek()!!; i++
                val haber = conj("haber", if (past) SpanishTense.IMPERFECT else SpanishTense.PRESENT, subject.person)
                val ing = word()?.let { w -> verbs(w).firstOrNull { it.second.form == EnglishForm.GERUND } }
                out.add(aux.raw, haber, PartOfSpeech.VERB, "haber · perfect")
                out.add(beenTok.raw, "estado", PartOfSpeech.VERB, "participle")
                if (ing != null) {
                    val tok = peek()!!; i++
                    val parts = SpanishConjugator.parse(ing.first.baseTranslation)
                    if (parts.reflexive) { out.insertAt(cliticSlot, "", SpanishConjugator.reflexivePronoun(subject.person), "reflexive"); cliticSlot++ }
                    out.add(tok.raw, SpanishConjugator.gerund(parts.infinitive), PartOfSpeech.VERB, "gerund", ing.first, content = true)
                    mainVerbLemma = ing.first.sourceWord
                } else {
                    val adj = word()?.let { adjectives(it).firstOrNull() }
                    if (adj != null) {
                        val tok = peek()!!; i++
                        out.add(tok.raw, SpanishMorphology.agreeAdjective(adj.first.baseTranslation, subject.gender, subject.plural), PartOfSpeech.ADJECTIVE, "adjective", adj.first, content = true)
                    }
                }
                return true
            }
            val participle = participleAt(i)
            if (participle != null && wordAt(i) != "had") {
                val haber = conj("haber", if (past) SpanishTense.IMPERFECT else SpanishTense.PRESENT, subject.person)
                out.add(aux.raw, haber, PartOfSpeech.VERB, "haber · perfect")
                val tok = peek()!!; i++
                val parts = SpanishConjugator.parse(participle.baseTranslation)
                if (parts.reflexive) { out.insertAt(cliticSlot, "", SpanishConjugator.reflexivePronoun(subject.person), "reflexive"); cliticSlot++ }
                out.add(tok.raw, SpanishConjugator.pastParticiple(parts.infinitive) + if (parts.tail.isNotEmpty()) " ${parts.tail}" else "",
                    PartOfSpeech.VERB, "participle", participle, content = true, conf = 0.85f)
                mainVerbLemma = participle.sourceWord
                return true
            }
            val tense = if (past) SpanishTense.PRETERITE else SpanishTense.PRESENT
            out.add(aux.raw, conj("tener", tense, subject.person), PartOfSpeech.VERB, label(tense, subject.person),
                dictionary.sense("have", PartOfSpeech.VERB), content = true)
            mainVerbLemma = "have"
            return true
        }

        private fun beGroup(subject: Subject, aux: Tok): Boolean {
            val past = aux.w == "was" || aux.w == "were"
            val tense = if (past) SpanishTense.IMPERFECT else SpanishTense.PRESENT
            val w = word()
            if (w == null) { // "I am" at the end of what has been typed so far
                out.add(aux.raw, conj("estar", tense, subject.person), PartOfSpeech.VERB, "estar · ${label(tense, subject.person)}")
                return true
            }
            // be going to + verb -> ir a + infinitive
            if (w == "going" && word(1) == "to" && (isVerbAt(i + 2) || wordAt(i + 2) == "be")) {
                val raw = "${aux.raw} ${peek()!!.raw} to"; i += 2
                out.add(raw, conj("ir", tense, subject.person) + " a", PartOfSpeech.VERB, "ir a · near future", content = true)
                if (word() == "be") { i++; return beAfterModal(subject) }
                return mainVerb(subject, tense, finite = false)
            }
            // be + -ing -> estar + gerund
            val ing = verbs(w).firstOrNull { it.second.form == EnglishForm.GERUND }
            if (ing != null && ing.first.sourceWord in setOf("come", "go") && !(word(1) == "to" && isVerbAt(i + 2))) {
                // "they are coming tonight" -> "vienen esta noche": Spanish uses the simple present for movement plans.
                val tok = peek()!!; i++
                val inf = ing.first.baseTranslation
                out.add("${aux.raw} ${tok.raw}", conj(inf, if (past) SpanishTense.IMPERFECT else SpanishTense.PRESENT, subject.person),
                    PartOfSpeech.VERB, label(if (past) SpanishTense.IMPERFECT else SpanishTense.PRESENT, subject.person), ing.first, content = true)
                mainVerbLemma = ing.first.sourceWord
                return true
            }
            if (ing != null) {
                out.add(aux.raw, conj("estar", tense, subject.person), PartOfSpeech.VERB, "estar · ${label(tense, subject.person)}")
                val tok = peek()!!; i++
                val parts = SpanishConjugator.parse(ing.first.baseTranslation)
                if (parts.reflexive) { out.insertAt(cliticSlot, "", SpanishConjugator.reflexivePronoun(subject.person), "reflexive"); cliticSlot++ }
                val ger = SpanishConjugator.gerund(parts.infinitive) + if (parts.tail.isNotEmpty()) " ${parts.tail}" else ""
                out.add(tok.raw, ger, PartOfSpeech.VERB, "gerund", ing.first, content = true, conf = 0.9f)
                mainVerbLemma = ing.first.sourceWord
                if (ing.first.sourceWord == "go" && word() == "to" && isVerbAt(i + 1)) { // "I'm going to" with no verb lookahead earlier
                    i++; out.add("to", "a", PartOfSpeech.PREPOSITION, "preposition")
                    return mainVerb(subject, tense, finite = false)
                }
                return true
            }
            // be + N + years old -> tener + N + años
            if (w.firstOrNull()?.isDigit() == true && word(1) in setOf("years", "year")) {
                out.add(aux.raw, conj("tener", tense, subject.person), PartOfSpeech.VERB, "tener · age")
                out.add(peek()!!.raw, peek()!!.raw, PartOfSpeech.OTHER, "number"); i++
                out.add(peek()!!.raw, "años", PartOfSpeech.NOUN, "age", content = true); i++
                if (word() == "old") i++
                return true
            }
            // Degree adverb before an adjective: "very tired", "so happy", "a little tired"
            val degree = when {
                w == "a" && word(1) == "little" -> "un poco"
                w in setOf("very", "so", "really", "quite") -> "muy"
                w == "too" -> "demasiado"
                else -> null
            }
            val adjIndex = when { degree == "un poco" -> i + 2; degree != null -> i + 1; else -> i }
            val adjWord = wordAt(adjIndex)
            if (adjWord != null) {
                val personSubject = !subject.impersonal
                if (subject.impersonal && adjWord in weatherWithHacer) {
                    i = adjIndex + 1
                    val hace = conj("hacer", tense, SpanishPerson.EL)
                    out.add("${aux.raw} ${t[adjIndex].raw}", "$hace ${if (degree != null) "mucho " else ""}${weatherWithHacer.getValue(adjWord)}",
                        PartOfSpeech.VERB, "weather · hacer", content = true)
                    return true
                }
                if (personSubject && adjWord in tenerIdioms) {
                    i = adjIndex + 1
                    val (noun, g) = tenerIdioms.getValue(adjWord)
                    out.add(aux.raw, conj("tener", tense, subject.person), PartOfSpeech.VERB, "tener · ${label(tense, subject.person)}")
                    val much = if (degree == "muy" || degree == "demasiado") (if (g == Gender.FEMININE) "mucha " else "mucho ") else if (degree == "un poco") "un poco de " else ""
                    out.add(t[adjIndex].raw, much + noun, PartOfSpeech.NOUN, "tener $noun = to be $adjWord", dictionary.sense(adjWord), content = true)
                    return true
                }
                if (adjWord in setOf("fine", "okay", "ok", "well") || (adjWord == "good" && personSubject && subject.person != SpanishPerson.EL)) {
                    i = adjIndex + 1
                    out.add(aux.raw, conj("estar", tense, subject.person), PartOfSpeech.VERB, "estar · ${label(tense, subject.person)}")
                    if (degree != null) out.add("", degree, PartOfSpeech.ADVERB, "adverb")
                    out.add(t[adjIndex].raw, "bien", PartOfSpeech.ADVERB, "adverb", content = true)
                    return true
                }
                val adj = adjectives(adjWord).firstOrNull()
                val participle = if (adj == null) participleAt(adjIndex) else null
                val nounFollows = wordAt(adjIndex + 1)?.let { nouns(it).isNotEmpty() } == true
                if ((adj != null || participle != null) && !nounFollows) {
                    i = adjIndex + 1
                    val verb = if (adjWord in estarAdjectives || participle != null) "estar" else "ser"
                    out.add(aux.raw, conj(verb, tense, subject.person), PartOfSpeech.VERB, "$verb · ${label(tense, subject.person)}")
                    if (degree != null) out.add(rawOf(i - 1 - (if (degree == "un poco") 2 else 1), i - 1), degree, PartOfSpeech.ADVERB, "adverb")
                    val base = adj?.first?.baseTranslation ?: SpanishConjugator.pastParticiple(participle!!.baseTranslation)
                    val comparative = adj != null && adj.second.form == EnglishForm.COMPARATIVE && adj.first.sourceWord !in setOf("good", "bad")
                    val form = SpanishMorphology.agreeAdjective(base, subject.gender, subject.plural)
                    out.add(t[adjIndex].raw, if (comparative) "más $form" else form, PartOfSpeech.ADJECTIVE,
                        "adjective · ${genderLabel(subject.gender, subject.plural)}", adj?.first ?: participle, content = true)
                    if (comparative && word() == "than") { out.add(peek()!!.raw, "que", PartOfSpeech.CONJUNCTION, "comparison"); i++ }
                    return true
                }
            }
            // be + location / origin / lateness
            when (w) {
                "from" -> {
                    out.add(aux.raw, conj("ser", tense, subject.person), PartOfSpeech.VERB, "ser · origin")
                    return true
                }
                "late", "early" -> {
                    out.add("${aux.raw} ${peek()!!.raw}", conj("llegar", if (past) SpanishTense.PRETERITE else SpanishTense.PRESENT, subject.person) +
                        if (w == "late") " tarde" else " temprano", PartOfSpeech.VERB, "llegar ${if (w == "late") "tarde" else "temprano"}", content = true)
                    i++
                    return true
                }
                "here", "there", "home", "at", "in", "on", "with", "outside", "inside", "near", "back", "still", "not" -> {
                    out.add(aux.raw, conj("estar", tense, subject.person), PartOfSpeech.VERB, "estar · location")
                    return true
                }
            }
            // be + noun phrase -> ser
            out.add(aux.raw, conj("ser", tense, subject.person), PartOfSpeech.VERB, "ser · ${label(tense, subject.person)}")
            predicateOf = subject
            return true
        }

        private fun beAfterModal(subject: Subject): Boolean {
            val adjWord = word()
            val adj = adjWord?.let { adjectives(it).firstOrNull() }
            val verb = if (adjWord != null && (adjWord in estarAdjectives || adjWord in setOf("here", "there", "home", "at", "in", "fine", "okay"))) "estar" else "ser"
            out.add("be", verb, PartOfSpeech.VERB, "infinitive")
            if (adj != null) {
                val tok = peek()!!; i++
                out.add(tok.raw, SpanishMorphology.agreeAdjective(adj.first.baseTranslation, subject.gender, subject.plural), PartOfSpeech.ADJECTIVE,
                    "adjective", adj.first, content = true)
            }
            return true
        }

        /**
         * Main lexical verb at [i]. [finite] = conjugate it for the subject;
         * otherwise it follows a conjugated auxiliary and stays infinitive.
         * [forcedTense] = the tense came from an auxiliary (did/will/used to), not from the verb's own spelling.
         */
        private fun mainVerb(subject: Subject, tense: SpanishTense, finite: Boolean, forcedTense: Boolean = false): Boolean {
            takeNot()
            if (cliticSlot < 0) cliticSlot = out.segments.size
            val tok = peek()?.takeIf { !it.punct } ?: return true
            clauseHasVerb = true
            val w = tok.w
            if (verbs(w).firstOrNull()?.first?.sourceWord == "like") {
                val obj = word(1)
                run {
                    i++
                    if (word() == "to") i++
                    val liked = when (obj) {
                        "me" -> SpanishPerson.YO; "you" -> SpanishPerson.TU; "us" -> SpanishPerson.NOSOTROS; "them" -> SpanishPerson.ELLOS
                        else -> if (obj != null && obj !in objectPronouns && obj != "to" && likedThingIsPlural()) SpanishPerson.ELLOS else SpanishPerson.EL
                    }
                    if (obj in objectPronouns) i++
                    val effective = if (!forcedTense && verbs(w).firstOrNull()?.second?.form.let { it == EnglishForm.PAST || it == EnglishForm.PAST_OR_PARTICIPLE }) SpanishTense.PRETERITE else tense
                    gustar(subject, effective, tok.raw, liked, infinitive = !finite)
                    if (isVerbAt(i)) mainVerb(subject, SpanishTense.PRESENT, finite = false)
                    return true
                }
            }
            if (w == "be") {
                i++
                val next = word()
                val estar = next != null && (next in estarAdjectives || next in setOf("here", "there", "home", "at", "in", "on", "fine", "okay", "ok", "back", "ready") ||
                    verbs(next).any { it.second.form == EnglishForm.GERUND })
                val inf = if (estar) "estar" else "ser"
                out.add(tok.raw, if (finite) conj(inf, tense, subject.person) else inf, PartOfSpeech.VERB, if (finite) "$inf · ${label(tense, subject.person)}" else "infinitive")
                val adj = next?.let { adjectives(it).firstOrNull() }
                if (adj != null && next !in setOf("here", "there")) {
                    val adjTok = peek()!!; i++
                    out.add(adjTok.raw, SpanishMorphology.agreeAdjective(adj.first.baseTranslation, subject.gender, subject.plural), PartOfSpeech.ADJECTIVE,
                        "adjective", adj.first, content = true)
                }
                return true
            }
            val resolved = verbs(w).firstOrNull() ?: return false
            var entry = resolved.first
            if (entry.sourceWord == "get" && word(1) in setOf("home", "there", "here", "to", "back")) {
                entry = dictionary.sense("arrive", PartOfSpeech.VERB) ?: entry // "get home" -> "llegar a casa"
            }
            i++
            val ownPast = resolved.second.form == EnglishForm.PAST || resolved.second.form == EnglishForm.PAST_OR_PARTICIPLE
            val effectiveTense = if (finite && !forcedTense && ownPast) SpanishTense.PRETERITE else tense
            val parts = SpanishConjugator.parse(entry.baseTranslation)
            val form = if (finite) conj(parts.infinitive, effectiveTense, subject.person) else parts.infinitive
            if (parts.reflexive) { out.insertAt(cliticSlot, "", SpanishConjugator.reflexivePronoun(subject.person), "reflexive"); cliticSlot++ }
            val text = if (parts.tail.isEmpty()) form else "$form ${parts.tail}"
            if (!finite) { lastInfinitiveSegment = out.segments.size; nonFiniteVerbs++ }
            else lastFinite = Triple(out.segments.size, effectiveTense, subject.person)
            out.add(tok.raw, text, PartOfSpeech.VERB, if (finite) label(effectiveTense, subject.person) else "infinitive", entry, content = true, conf = 0.88f)
            mainVerbLemma = entry.sourceWord
            if (finite && effectiveTense == SpanishTense.FUTURE) futureContext = true
            // verb + to + verb  ("want to go", "need to work", "start to learn")
            if (word() == "to" && (isVerbAt(i + 1) || wordAt(i + 1) == "be") && wordAt(i + 1)?.let { isPureNounAt(i + 1) } != true) {
                val toRaw = peek()!!.raw; i++
                val prep = infinitivePreposition[parts.infinitive].orEmpty()
                if (prep.isNotEmpty()) out.add(toRaw, prep, PartOfSpeech.PREPOSITION, "preposition")
                if (word() == "be") { i++; return beAfterModal(subject) }
                return mainVerb(subject, effectiveTense, finite = false)
            }
            // go + -ing ("go swimming") -> ir a + infinitive
            if (entry.sourceWord == "go" && word()?.endsWith("ing") == true && isVerbAt(i)) {
                out.add("", "a", PartOfSpeech.PREPOSITION, "preposition")
                val ingTok = peek()!!
                val v = verbs(ingTok.w).first().first; i++
                out.add(ingTok.raw, SpanishConjugator.parse(v.baseTranslation).infinitive, PartOfSpeech.VERB, "infinitive", v, content = true)
            }
            return true
        }

        private fun likedThingIsPlural(): Boolean {
            var k = i
            while (k < t.size && !t[k].punct) {
                val w = t[k].w
                if (w in pluralDeterminers) return true
                val noun = nouns(w).firstOrNull()
                if (noun != null) return noun.second.form == EnglishForm.PLURAL || noun.first.pluralOnly
                if (isVerbAt(k)) return false
                k++
            }
            return false
        }

        /** "I like X" -> "me gusta(n) X": the English subject becomes the indirect object, the liked thing agrees with the verb. */
        private fun gustar(subject: Subject, tense: SpanishTense, raw: String, likedPerson: SpanishPerson, infinitive: Boolean = false, verb: String = "gustar") {
            val io = when (subject.person) {
                SpanishPerson.YO -> "me"; SpanishPerson.TU -> "te"; SpanishPerson.EL -> "le"
                SpanishPerson.NOSOTROS -> "nos"; SpanishPerson.ELLOS -> "les"
            }
            // "a ella le gusta": keep an explicit third-person subject as "a + X".
            val subjectSeg = out.segments.lastOrNull { it.formLabel == "subject" }
            if (subjectSeg != null && subjectSeg.targetText.isNotEmpty()) {
                val idx = out.segments.indexOf(subjectSeg)
                out.segments[idx] = subjectSeg.copy(targetText = "a ${subjectSeg.targetText}")
            }
            val form = if (infinitive) verb else conj(verb, tense, likedPerson)
            val slot = if (cliticSlot >= 0) cliticSlot else out.segments.size
            out.insertAt(slot, "", io, "indirect object")
            out.add(raw, form, PartOfSpeech.VERB, "$verb · $io", dictionary.sense("like", PartOfSpeech.VERB), content = true, hint = "$io $form")
            mainVerbLemma = "like"
            gustarActive = true
        }

        private fun parseImperative() {
            val tok = peek()!!
            val entry = verbs(tok.w).firstOrNull()?.first ?: return
            i++
            clauseHasVerb = true
            mainVerbLemma = entry.sourceWord
            var form = SpanishConjugator.imperativeTu(entry.baseTranslation)
            val obj = word()
            val objIsPronoun = obj != null && obj in objectPronouns && !(obj == "her" && wordAt(i + 1)?.let { nouns(it).isNotEmpty() } == true)
            if (objIsPronoun && !SpanishConjugator.parse(entry.baseTranslation).reflexive) {
                // Attach the object pronoun: "call me" -> "llámame", "tell her" -> "dile".
                val head = form.substringBefore(' ')
                val tail = form.substringAfter(' ', "")
                form = SpanishText.attachUnstressed(head, cliticFor(obj!!, entry.sourceWord)) + if (tail.isNotEmpty()) " $tail" else ""
                i++
                out.add("${tok.raw} ${t[i - 1].raw}", form, PartOfSpeech.VERB, "imperative · tú", entry, content = true)
                return
            }
            out.add(tok.raw, form, PartOfSpeech.VERB, "imperative · tú", entry, content = true)
        }

        private fun cliticFor(obj: String, verbLemma: String?): String {
            val indirect = verbLemma != null && verbLemma in indirectObjectVerbs
            return when (obj) {
                "me" -> "me"; "you" -> "te"; "us" -> "nos"
                "him" -> if (indirect) "le" else "lo"
                "her" -> if (indirect) "le" else "la"
                "it" -> "lo"
                "them" -> if (indirect) "les" else "los"
                else -> ""
            }
        }

        // --------------------------------------------------- complements

        fun parseComplements(subject: Subject) {
            var guard = 0
            while (i < t.size && guard++ < 64) {
                val tok = peek()!!
                if (tok.punct) {
                    if (tok.raw == ",") { out.add(",", ",", PartOfSpeech.OTHER, "punct"); i++ }
                    return
                }
                val w = tok.w
                if (matchIdiom()) continue
                if (w == "so" && word(1) == "much") { out.add(rawOf(i, i + 2), "mucho", PartOfSpeech.ADVERB, "adverb", content = true); i += 2; continue }
                if (w == "so" && word(1)?.let { adjectives(it).isNotEmpty() } == true) { out.add(tok.raw, "tan", PartOfSpeech.ADVERB, "adverb"); i++; continue }
                if (w in conjunctions && w != "that") return
                if (w == "that" && looksLikeSubjectAt(i + 1)) {
                    thinkAsBelieve()
                    if (mainVerbLemma in subjunctiveVerbs || (negatedClause && mainVerbLemma in setOf("think", "believe"))) pendingSubjunctive = true
                    return
                }
                if (w in setOf("until", "before") && futureContext && looksLikeSubjectAt(i + 1)) return
                val startsClause = (w in subjectPronouns && w != "it" && w != "you" && (word(1) in auxiliaries || isVerbAt(i + 1))) ||
                    ((w == "you" || w == "it") && (word(1) in auxiliaries || (isVerbAt(i + 1) && !isPureNounAt(i + 1) && clauseHasVerb && word(1) !in setOf("too", "so", "very"))))
                if (startsClause) {
                    if (mainVerbLemma in clauseTakingVerbs) { // "I think you're right" -> "creo que tienes razón"
                        thinkAsBelieve()
                        out.add("", "que", PartOfSpeech.CONJUNCTION, "conjunction")
                        if (mainVerbLemma in subjunctiveVerbs || (negatedClause && mainVerbLemma in setOf("think", "believe"))) pendingSubjunctive = true
                    }
                    return
                }

                if (w in whWords) {
                    val es = if (mainVerbLemma in indirectQuestionVerbs) whWords.getValue(w) else relativeWh.getValue(w)
                    out.add(tok.raw, es, PartOfSpeech.PRONOUN, "question word"); i++
                    return // a new (sub)clause follows
                }

                // Object pronoun -> clitic before the finite verb ("I love you" -> "te amo").
                val herIsPossessive = w == "her" && wordAt(i + 1)?.let { nouns(it).isNotEmpty() || adjectives(it).isNotEmpty() } == true
                // "I want you to come" -> "quiero que vengas": object + to + verb becomes a subjunctive clause.
                if (w in objectPronouns && !herIsPossessive && mainVerbLemma in subjunctiveVerbs && word(1) == "to" && isVerbAt(i + 2)) {
                    val person = when (w) {
                        "me" -> SpanishPerson.YO; "you" -> SpanishPerson.TU; "us" -> SpanishPerson.NOSOTROS; "them" -> SpanishPerson.ELLOS
                        else -> SpanishPerson.EL
                    }
                    out.add("", "que", PartOfSpeech.CONJUNCTION, "conjunction")
                    i += 2
                    val saved = subjunctiveClause
                    subjunctiveClause = true
                    cliticSlot = out.segments.size
                    mainVerb(Subject(person, if (w == "her") Gender.FEMININE else Gender.MASCULINE, ""), SpanishTense.PRESENT, finite = true, forcedTense = true)
                    subjunctiveClause = saved
                    continue
                }
                if (w in objectPronouns && !herIsPossessive) {
                    i++
                    if (gustarActive) continue
                    val clitic = cliticFor(w, mainVerbLemma)
                    if (nonFiniteVerbs >= 2 && lastInfinitiveSegment in out.segments.indices) { // "no puedo esperar a verte"
                        val seg = out.segments[lastInfinitiveSegment]
                        val head = seg.targetText.substringBefore(' ')
                        val tail = seg.targetText.substringAfter(' ', "")
                        val attached = SpanishText.attachUnstressed(head, clitic) + if (tail.isNotEmpty()) " $tail" else ""
                        out.segments[lastInfinitiveSegment] = seg.copy(targetText = attached, hintText = attached)
                    } else if (cliticSlot >= 0) { out.insertAt(cliticSlot, tok.raw, clitic, "object pronoun"); cliticSlot++ }
                    else out.add(tok.raw, clitic, PartOfSpeech.PRONOUN, "object pronoun")
                    continue
                }
                if (w == "not") { out.add(tok.raw, "no", PartOfSpeech.ADVERB, "negation"); negatedClause = true; i++; continue }
                if (w in setOf("anything", "something", "nothing", "everything", "anyone", "someone", "nobody", "everyone")) {
                    val es = when (w) {
                        "anything" -> if (negatedClause) "nada" else "algo"
                        "anyone" -> if (negatedClause) "a nadie" else "a alguien"
                        "something" -> "algo"; "nothing" -> "nada"; "everything" -> "todo"
                        "someone" -> "a alguien"; "nobody" -> "a nadie"; else -> "a todos"
                    }
                    out.add(tok.raw, es, PartOfSpeech.PRONOUN, "pronoun", content = true); i++; continue
                }
                if (w == "too" && (i + 1 >= t.size || t[i + 1].punct)) { out.add(tok.raw, "también", PartOfSpeech.ADVERB, "adverb", content = true); i++; continue }
                if (w == "a" && word(1) == "lot" && (word(2) == null || word(2) != "of")) { out.add(rawOf(i, i + 2), "mucho", PartOfSpeech.ADVERB, "adverb", content = true); i += 2; continue }
                if (w == "home" && mainVerbLemma in motionVerbs) {
                    out.add(tok.raw, "a casa", PartOfSpeech.NOUN, "noun · f", dictionary.sense("home"), content = true); i++; continue
                }
                if (w == "last" && word(1) == "night") { out.add(rawOf(i, i + 2), "anoche", PartOfSpeech.ADVERB, "adverb", content = true); i += 2; continue }
                if (w in setOf("last", "next", "this", "every") && word(1) in timeNouns && dictionary.sense(word(1)!!, PartOfSpeech.NOUN) != null) { timeExpression(); continue }

                val prep = prepositions[w]
                if (prep != null) {
                    if (w == "to" && isVerbAt(i + 1) && !isPureNounAt(i + 1)) { // purpose: "to learn" -> "para aprender"
                        out.add(tok.raw, "para", PartOfSpeech.PREPOSITION, "purpose"); i++
                        mainVerb(subject, SpanishTense.PRESENT, finite = false)
                    } else {
                        prepositionalPhrase(w, prep)
                    }
                    continue
                }

                val adv = dictionary.resolveEnglish(w, setOf(PartOfSpeech.ADVERB)).firstOrNull()
                if (adv != null && !isPureNounAt(i)) {
                    i++
                    out.add(tok.raw, adv.first.baseTranslation, PartOfSpeech.ADVERB, "adverb", adv.first, content = true)
                    continue
                }
                val save = i
                val npIndex = out.segments.size
                val np = parseNounPhrase(definiteIfBare = gustarActive)
                if (np != null && i > save) {
                    val seg = out.segments.getOrNull(npIndex)
                    val human = seg?.entry?.sourceWord?.let { it in humanNouns || seg.entry.gender == "mf" } == true
                    if (human && clauseHasVerb && !gustarActive && mainVerbLemma != null && mainVerbLemma !in setOf("have", "be")) { // personal "a": "amo a mi familia"
                        if (seg!!.targetText.startsWith("el ")) out.segments[npIndex] = seg.copy(targetText = seg.targetText.removePrefix("el "), hintText = seg.hintText)
                        out.segments.add(npIndex, TranslationSegment("", if (seg.targetText.startsWith("el ")) "al" else "a", PartOfSpeech.PREPOSITION, "personal a", null, false, 0.9f))
                    }
                    continue
                }
                i = save
                if (!clauseHasVerb && isVerbAt(i)) { // "please call me"
                    parseImperative(); continue
                }
                val adj = adjectives(w).firstOrNull()
                if (adj != null) {
                    i++
                    out.add(tok.raw, SpanishMorphology.agreeAdjective(adj.first.baseTranslation, subject.gender, subject.plural), PartOfSpeech.ADJECTIVE,
                        "adjective", adj.first, content = true)
                    continue
                }
                if (isVerbAt(i)) { mainVerb(subject, SpanishTense.PRESENT, finite = word(-1) != "and"); continue }
                if (w in auxiliaries || w in subjectPronouns) return
                unknown(tok); i++
            }
        }

        private fun timeExpression() {
            val det = word()!!
            val nounTok = t[i + 1]
            val noun = dictionary.sense(nounTok.w, PartOfSpeech.NOUN)!!
            val g = genderOf(noun)
            val es = noun.baseTranslation
            val text = when (det) {
                "last" -> "${SpanishMorphology.definiteArticle(es, g, false)} $es ${SpanishMorphology.agreeAdjective("pasado", g, false)}"
                "next" -> "${SpanishMorphology.definiteArticle(es, g, false)} ${SpanishMorphology.agreeAdjective("próximo", g, false)} $es"
                "this" -> "${SpanishMorphology.demonstrative(true, g, false)} $es"
                else -> "${SpanishMorphology.agreeAdjective("todo", g, true)} ${SpanishMorphology.definiteArticle(es, g, true)} ${SpanishMorphology.plural(es)}"
            }
            out.add(rawOf(i, i + 2), text, PartOfSpeech.NOUN, "time expression", noun, content = true)
            i += 2
        }

        private fun prepositionalPhrase(w: String, es: String) {
            val prepTok = peek()!!
            i++
            val next = word()
            if (next != null && next in objectPronouns && !(next == "her" && wordAt(i + 1)?.let { nouns(it).isNotEmpty() } == true)) {
                val es = verbPrepositions[mainVerbLemma to w]?.takeIf { it.isNotEmpty() } ?: es
                val text = when {
                    w == "with" && next == "me" -> "conmigo"
                    w == "with" && next == "you" -> "contigo"
                    else -> "$es " + when (next) {
                        "me" -> "mí"; "you" -> "ti"; "him" -> "él"; "her" -> "ella"; "it" -> "eso"; "us" -> "nosotros"; else -> "ellos"
                    }
                }
                out.add("${prepTok.raw} ${peek()!!.raw}", text, PartOfSpeech.PREPOSITION, "preposition + pronoun")
                i++
                return
            }
            if (next == "home" && w in setOf("at", "in", "to")) {
                out.add("${prepTok.raw} ${peek()!!.raw}", if (w == "to") "a casa" else "en casa", PartOfSpeech.NOUN, "phrase", dictionary.sense("home"), content = true)
                i++
                return
            }
            // preposition + -ing -> preposition + infinitive ("after eating" -> "después de comer")
            if (next != null && next.endsWith("ing")) {
                val v = verbs(next).firstOrNull { it.second.form == EnglishForm.GERUND }
                if (v != null) {
                    out.add(prepTok.raw, if (w == "for") "por" else es, PartOfSpeech.PREPOSITION, "preposition")
                    val vt = peek()!!; i++
                    val parts = SpanishConjugator.parse(v.first.baseTranslation)
                    out.add(vt.raw, if (parts.reflexive) parts.infinitive + "se" else parts.infinitive, PartOfSpeech.VERB, "infinitive", v.first, content = true)
                    return
                }
            }
            val verbPrep = verbPrepositions[mainVerbLemma to w]
            if (verbPrep == "") { // "listen to music" -> "escuchar música": the Spanish verb takes a direct object
                parseNounPhrase(definiteIfBare = false)
                return
            }
            val prepIndex = out.segments.size
            out.add(prepTok.raw, verbPrep ?: es, PartOfSpeech.PREPOSITION, "preposition")
            val np = parseNounPhrase(definiteIfBare = next in institutionalNouns)
            if (np != null && (es == "a" || es == "de")) {
                val after = out.segments.getOrNull(prepIndex + 1)
                if (after != null && after.targetText.startsWith("el ")) { // a + el -> al, de + el -> del
                    out.segments[prepIndex] = out.segments[prepIndex].copy(targetText = if (es == "a") "al" else "del")
                    out.segments[prepIndex + 1] = after.copy(targetText = after.targetText.removePrefix("el "))
                }
            }
        }

        // ---------------------------------------------------- noun phrase

        inner class NP(val text: String, val gender: Gender, val plural: Boolean, val contentWords: Int)

        fun parseNounPhrase(definiteIfBare: Boolean): NP? {
            val start = i
            var det: String? = null
            when {
                word() == "a" && word(1) == "lot" && word(2) == "of" -> { det = "lot"; i += 3 }
                word() == "all" && word(1) == "the" -> { det = "all"; i += 2 }
                word() == "all" && word(1) != null && nouns(word(1)!!).isNotEmpty() -> { det = "all"; i += 1 }
                word() in determiners -> { det = word(); i++ }
            }
            val adjectivesFound = mutableListOf<Pair<Tok, DictionaryEntry>>()
            val modifiers = mutableListOf<DictionaryEntry>()
            var head: Pair<Tok, Pair<DictionaryEntry, EnglishAnalysis>>? = null
            var degree: String? = null
            while (i < t.size && !t[i].punct) {
                val w = t[i].w
                if (w == "very" || w == "really") { degree = "muy"; i++; continue }
                val noun = nouns(w).firstOrNull()
                val adj = adjectives(w).firstOrNull()
                val nextTok = t.getOrNull(i + 1)
                val nextIsNoun = nextTok != null && !nextTok.punct && nouns(nextTok.w).isNotEmpty()
                val nextIsAdj = nextTok != null && !nextTok.punct && adjectives(nextTok.w).isNotEmpty()
                if (adj != null && (nextIsNoun || nextIsAdj) && (noun == null || adjectivesFound.isNotEmpty() || det != null)) {
                    adjectivesFound.add(t[i] to adj.first); i++; continue
                }
                if (noun != null) {
                    if (nextIsNoun && noun.second.form == EnglishForm.BASE && verbs(nextTok!!.w).isEmpty()) {
                        modifiers.add(noun.first); i++; continue // compound noun: "phone number"
                    }
                    head = t[i] to noun; i++
                }
                break
            }
            if (head == null) {
                if (modifiers.isNotEmpty()) {
                    val last = modifiers.removeAt(modifiers.size - 1)
                    head = t[i - 1] to (last to EnglishAnalysis(last.sourceWord, EnglishForm.BASE))
                } else {
                    i = start
                    if (det in setOf("this", "that") && adjectivesFound.isEmpty()) {
                        val tok = peek()!!; i++
                        out.add(tok.raw, if (det == "this") "esto" else "eso", PartOfSpeech.PRONOUN, "pronoun")
                        return NP(if (det == "this") "esto" else "eso", Gender.MASCULINE, false, 0)
                    }
                    if (det != null && det in numberWords) {
                        val tok = peek()!!; i++
                        val entry = dictionary.sense(det)
                        out.add(tok.raw, entry?.baseTranslation ?: det, PartOfSpeech.OTHER, "number", entry, content = true)
                        return NP(entry?.baseTranslation ?: det, Gender.MASCULINE, det != "one", 1)
                    }
                    return null
                }
            }
            val (_, headRes) = head!!
            val (entry, analysis) = headRes
            val predicate = predicateOf
            predicateOf = null
            // "she is a teacher" -> "ella es maestra": person nouns agree with the subject, and professions take no article after ser.
            val personNoun = entry.gender == "mf"
            val gender = if (personNoun && predicate != null) predicate.gender else genderOf(entry)
            if (personNoun && predicate != null && (det == "a" || det == "an") && adjectivesFound.isEmpty()) det = "none"
            val plural = analysis.form == EnglishForm.PLURAL || entry.pluralOnly || det in pluralDeterminers ||
                (det == "all" && analysis.form == EnglishForm.PLURAL)
            val es = if (personNoun && gender == Gender.FEMININE) feminineNoun(entry.baseTranslation) else entry.baseTranslation
            val nounForm = if (plural && !entry.pluralOnly) SpanishMorphology.plural(es) else es
            var nounText = nounForm
            for (m in modifiers.asReversed()) nounText += " de " + m.baseTranslation

            val pre = mutableListOf<String>()
            val post = mutableListOf<String>()
            for ((tok, a) in adjectivesFound) {
                if (tok.w in preNominalAdjectives) pre.add(SpanishMorphology.apocopate(a.baseTranslation, gender, plural))
                else post.add((if (degree != null) "$degree " else "") + SpanishMorphology.agreeAdjective(a.baseTranslation, gender, plural))
            }
            val determiner = determinerFor(det, nounForm, gender, plural, definiteIfBare)
            val text = (listOf(determiner).filter { it.isNotEmpty() } + pre + nounText + post).joinToString(" ")
            val contentWords = 1 + adjectivesFound.size + modifiers.size
            out.add(rawOf(start, i), text, PartOfSpeech.NOUN, "noun · ${genderLabel(gender, plural)}", entry, content = true, conf = 0.9f)
            out.contentTotal += contentWords - 1; out.contentTranslated += contentWords - 1
            return NP(text, gender, plural, contentWords)
        }

        private val meals = setOf("cena", "almuerzo", "desayuno", "comida")

        private fun feminineNoun(es: String): String = when {
            es == "jefe" -> "jefa"
            es.endsWith("o") -> es.dropLast(1) + "a"
            es.endsWith("or") -> es + "a"
            else -> es
        }

        private fun determinerFor(det: String?, noun: String, g: Gender, plural: Boolean, definiteIfBare: Boolean): String = when {
            noun.firstOrNull()?.isUpperCase() == true && (det == null || det == "the") -> "" // México, España: no article
            det == null && noun in meals -> SpanishMorphology.definiteArticle(noun, g, plural)
            else -> determinerWord(det, noun, g, plural, definiteIfBare)
        }

        private fun determinerWord(det: String?, noun: String, g: Gender, plural: Boolean, definiteIfBare: Boolean): String = when (det) {
            "none" -> ""
            null -> if (definiteIfBare) SpanishMorphology.definiteArticle(noun, g, plural) else ""
            "the" -> SpanishMorphology.definiteArticle(noun, g, plural)
            "a", "an" -> SpanishMorphology.indefiniteArticle(noun, g, plural)
            "this", "these" -> SpanishMorphology.demonstrative(true, g, plural)
            "that", "those" -> SpanishMorphology.demonstrative(false, g, plural)
            "my" -> SpanishMorphology.possessive("mi", g, plural)
            "your" -> SpanishMorphology.possessive("tu", g, plural)
            "his", "her", "its", "their" -> SpanishMorphology.possessive("su", g, plural)
            "our" -> SpanishMorphology.possessive("nuestro", g, plural)
            "some" -> if (plural) SpanishMorphology.agreeAdjective("alguno", g, true) else "un poco de"
            "any" -> ""
            "every", "each" -> "cada"
            "no" -> SpanishMorphology.apocopate("ninguno", g, false)
            "all" -> "${SpanishMorphology.agreeAdjective("todo", g, plural)} ${SpanishMorphology.definiteArticle(noun, g, plural)}"
            "many", "lot" -> SpanishMorphology.agreeAdjective("mucho", g, plural)
            "much" -> SpanishMorphology.agreeAdjective("mucho", g, false)
            "few" -> SpanishMorphology.agreeAdjective("poco", g, true)
            "several" -> SpanishMorphology.agreeAdjective("vario", g, true)
            "another", "other" -> SpanishMorphology.agreeAdjective("otro", g, plural)
            "one" -> SpanishMorphology.apocopate("uno", g, false)
            else -> dictionary.sense(det)?.baseTranslation ?: det
        }
    }

    // ----------------------------------------------------------- public

    /** Translates one sentence (or the fragment typed so far). */
    fun translate(sentence: String): TranslationResult {
        val tokens = tokenize(sentence)
        val out = Out()
        val parser = Parser(tokens, out)
        parser.run()
        val trimmed = sentence.trimEnd()
        val question = parser.isQuestion || trimmed.endsWith("?")
        val exclamation = trimmed.endsWith("!")
        val questionAt = if (parser.isQuestion) parser.questionStart.coerceAtLeast(0) else 0
        val lead = assemble(out.segments.subList(0, questionAt.coerceAtMost(out.segments.size)))
        val rest = assemble(out.segments.subList(questionAt.coerceAtMost(out.segments.size), out.segments.size))
        val body = assemble(out.segments)
        val text = when {
            body.isEmpty() -> ""
            question && lead.isNotEmpty() -> SpanishText.capitalizeFirst(lead) + " ¿" + rest + "?"
            question -> "¿" + SpanishText.capitalizeFirst(body) + "?"
            exclamation -> "¡" + SpanishText.capitalizeFirst(body) + "!"
            else -> SpanishText.capitalizeFirst(body) + if (trimmed.endsWith(".")) "." else ""
        }
        val coverage = if (out.contentTotal <= 0) (if (body.isEmpty()) 0f else 1f) else (out.contentTranslated.toFloat() / out.contentTotal).coerceIn(0f, 1f)
        return TranslationResult(text, out.segments.toList(), coverage, out.contentTotal, question)
    }

    private fun assemble(segments: List<TranslationSegment>): String {
        val sb = StringBuilder()
        for (s in segments) {
            val tgt = s.targetText
            if (tgt.isEmpty()) continue
            val isPunct = tgt.length == 1 && !tgt[0].isLetterOrDigit()
            if (!isPunct && sb.isNotEmpty() && !sb.endsWith(" ")) sb.append(' ')
            sb.append(tgt)
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }
}
