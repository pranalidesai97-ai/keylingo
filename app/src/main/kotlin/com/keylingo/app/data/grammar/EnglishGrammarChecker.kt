package com.keylingo.app.data.grammar

import com.keylingo.app.data.dictionary.BundledDictionary
import com.keylingo.app.data.language.EnglishForm
import com.keylingo.app.data.language.EnglishMorphology
import com.keylingo.app.data.lexicon.Lexicon
import com.keylingo.app.domain.model.GrammarIssue
import com.keylingo.app.domain.model.GrammarIssueType
import com.keylingo.app.domain.model.GrammarSuggestion
import com.keylingo.app.domain.model.PartOfSpeech

/**
 * Conservative, deterministic English grammar suggestions. Every rule is
 * narrow, explains itself, and has explicit exceptions for valid English,
 * because a false alarm while someone types a message is worse than a
 * missed one. It never changes text: it returns a [GrammarSuggestion] that
 * the user may apply.
 *
 * Rules: repeated words; subject-verb agreement (pronoun and
 * determiner+noun subjects, incl. be/have/do and their negative
 * contractions); present tense with a past-time marker (yesterday, last
 * week, ... ago); a/an; missing "the" before common place nouns and "a"
 * before a profession after "I am"; could/should/would of; their/there is;
 * your welcome; comparative + "then"; "its" + verb/determiner; sentence
 * capitalisation (only for finished sentences); spacing around , . ! ? ;.
 *
 * Only completed words are checked: callers pass [complete] = false while
 * the last word is still being typed, and that word is ignored.
 */
class EnglishGrammarChecker(private val lexicon: Lexicon, private val dictionary: BundledDictionary) {

    private data class Tok(val text: String, val lower: String, val start: Int, val end: Int, val isWord: Boolean)
    private data class Edit(val start: Int, val end: Int, val replacement: String, val type: GrammarIssueType, val message: String)

    private val tokenRegex = Regex("[A-Za-z]+(?:['’][A-Za-z]+)?|\\d+(?:[.,]\\d+)*|[^\\sA-Za-z\\d]")

    // ---------------------------------------------------------------- word lists

    private enum class Person { FIRST_SINGULAR, SECOND, THIRD_SINGULAR, PLURAL }

    private val subjectPronouns = mapOf(
        "i" to Person.FIRST_SINGULAR, "you" to Person.SECOND, "he" to Person.THIRD_SINGULAR, "she" to Person.THIRD_SINGULAR,
        "it" to Person.THIRD_SINGULAR, "we" to Person.PLURAL, "they" to Person.PLURAL,
    )
    private val determiners = setOf("the", "my", "your", "his", "her", "our", "their", "this", "that", "these", "those", "a", "an", "every", "each")
    private val pluralDeterminers = setOf("these", "those")
    private val clauseStarters = setOf("and", "but", "or", "so", "because", "when", "while", "although", "though", "since", "until", "before", "after", "if", "then")
    /** After these, a bare verb is correct ("does she go", "can he come", "to go", "let her go"). */
    private val auxiliaryContext = setOf("do", "does", "did", "don't", "doesn't", "didn't", "can", "could", "will", "would", "shall", "should", "may", "might", "must",
        "to", "let", "make", "made", "help", "helps", "see", "saw", "watch", "hear", "heard", "can't", "won't", "wouldn't", "shouldn't", "couldn't")
    private val frequencyAdverbs = setOf("always", "never", "usually", "often", "sometimes", "also", "really", "still", "just", "only", "rarely",
        "seldom", "generally", "normally", "already", "even", "probably", "actually", "barely", "hardly")
    private val pastMarkers = setOf("yesterday", "ago")
    private val pastMarkerNouns = setOf("night", "week", "month", "year", "weekend", "time", "summer", "winter", "spring", "fall", "autumn",
        "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
    private val futureMarkers = setOf("tomorrow", "will", "gonna", "won't", "'ll", "next", "tonight", "soon", "later")

    /** Repeats that are normal English or informal emphasis -- never flagged. */
    private val allowedRepeats = setOf("had", "that", "is", "very", "so", "really", "no", "bye", "ha", "haha", "ok", "okay", "yes", "yeah", "la", "now",
        "well", "too", "many", "much", "more", "far", "again", "knock", "there", "please", "hey", "hi", "oh", "go", "wow", "blah", "yay", "hmm")

    /** Countable place nouns that need an article after to/at/in/from ("go to office" -> "go to the office"). */
    private val placeNounsNeedingArticle = setOf("office", "store", "shop", "market", "supermarket", "bank", "park", "airport", "station", "hotel",
        "restaurant", "gym", "library", "museum", "beach", "mall", "cinema", "theater", "theatre", "pharmacy", "stadium", "zoo", "doctor", "dentist")
    /** Nouns that are idiomatic without an article ("go to school", "at home", "in bed"). */
    private val bareNounsAllowed = setOf("home", "school", "work", "church", "bed", "college", "class", "university", "hospital", "prison", "jail",
        "town", "sea", "lunch", "dinner", "breakfast", "time", "night", "noon", "midnight", "court", "camp", "practice")
    /** Job/role nouns: "I am teacher" -> "I am a teacher". */
    private val professionNouns = setOf("teacher", "student", "doctor", "nurse", "engineer", "lawyer", "writer", "singer", "driver", "waiter", "chef",
        "manager", "programmer", "developer", "designer", "artist", "farmer", "pilot", "dentist", "professor", "scientist", "journalist", "actor")

    // ---------------------------------------------------------------- helpers

    private fun tokenize(s: String): List<Tok> = tokenRegex.findAll(s).map {
        val t = it.value
        Tok(t, t.lowercase().replace('’', '\''), it.range.first, it.range.last + 1, t.first().isLetterOrDigit())
    }.toList()

    private fun verbLemma(word: String): String? =
        dictionary.resolveEnglish(word, setOf(PartOfSpeech.VERB)).firstOrNull { it.second.form == EnglishForm.BASE || it.second.form == EnglishForm.THIRD_PERSON }
            ?.first?.sourceWord?.takeIf { lemma -> word == lemma || word == EnglishMorphology.thirdPerson(lemma) }

    private fun isNoun(word: String) = dictionary.resolveEnglish(word, setOf(PartOfSpeech.NOUN)).isNotEmpty()
    /** English plurality from English morphology only (the dictionary's pluralOnly flag describes the Spanish noun, e.g. noticias). */
    private val singularEndingInS = setOf("news", "physics", "mathematics", "economics", "politics", "series", "species", "means", "bus", "gas", "class", "glass", "boss")
    private fun isPluralNoun(word: String) = word !in singularEndingInS &&
        dictionary.resolveEnglish(word, setOf(PartOfSpeech.NOUN)).any { it.second.form == EnglishForm.PLURAL && it.second.lemma != word }
    private fun isAdjective(word: String) = dictionary.resolveEnglish(word, setOf(PartOfSpeech.ADJECTIVE)).isNotEmpty()
    private fun isKnown(word: String) = lexicon.contains(word)

    private fun matchCase(replacement: String, original: String): String = when {
        original.length > 1 && original.all { !it.isLetter() || it.isUpperCase() } -> replacement.uppercase()
        original.first().isUpperCase() -> replacement.replaceFirstChar { it.titlecase() }
        else -> replacement
    }

    // ---------------------------------------------------------------- entry point

    fun check(sentence: String, complete: Boolean): GrammarSuggestion? {
        if (sentence.isBlank()) return null
        var toks = tokenize(sentence)
        // Ignore the word still being typed.
        if (!complete && toks.isNotEmpty() && toks.last().isWord && toks.last().end == sentence.length) toks = toks.dropLast(1)
        if (toks.count { it.isWord } < 2) return null

        val edits = mutableListOf<Edit>()
        repeatedWords(toks, edits)
        val tenseEdited = pastTense(toks, edits)
        agreement(toks, edits, skip = tenseEdited)
        articles(toks, edits)
        confusables(toks, edits)
        if (complete) capitalisation(toks, sentence, edits)
        spacing(sentence, edits)

        val accepted = mutableListOf<Edit>()
        for (e in edits.sortedBy { it.start }) {
            if (accepted.none { it.start < e.end && e.start < it.end }) accepted.add(e)
        }
        if (accepted.isEmpty()) return null
        val sb = StringBuilder(sentence)
        for (e in accepted.sortedByDescending { it.start }) sb.replace(e.start, e.end, e.replacement)
        val corrected = sb.toString().replace(Regex(" {2,}"), " ")
        if (corrected == sentence) return null
        return GrammarSuggestion(
            original = sentence,
            corrected = corrected,
            issues = accepted.map { GrammarIssue(it.type, it.message, sentence.substring(it.start, it.end), it.replacement) }
        )
    }

    // ---------------------------------------------------------------- rules

    private fun repeatedWords(toks: List<Tok>, edits: MutableList<Edit>) {
        for (i in 1 until toks.size) {
            val a = toks[i - 1]; val b = toks[i]
            if (!a.isWord || !b.isWord || a.lower != b.lower) continue
            if (a.lower in allowedRepeats || i - 1 == 0) continue
            // Delete the second occurrence together with the space before it.
            edits += Edit(a.end, b.end, "", GrammarIssueType.REPEATED_WORD, "Repeated word “${b.text}”")
        }
    }

    private data class Subject(val person: Person, val endIndex: Int, val text: String)

    /** Subject starting at token [i] if it is a clause start, else null. */
    private fun subjectAt(toks: List<Tok>, i: Int): Subject? {
        val t = toks.getOrNull(i) ?: return null
        if (!t.isWord) return null
        val prev = toks.getOrNull(i - 1)
        val atClauseStart = prev == null || !prev.isWord || prev.lower in clauseStarters ||
            prev.lower in pastMarkers || prev.lower in setOf("today", "now", "sometimes", "usually", "often", "also", "then") ||
            (i >= 2 && toks[i - 2].lower == "last" && prev.lower in pastMarkerNouns)
        if (!atClauseStart || prev?.lower == "that") return null
        subjectPronouns[t.lower]?.let { return Subject(it, i, t.lower) }
        if (t.lower in determiners) {
            var k = i + 1
            while (k < toks.size && toks[k].isWord && isAdjective(toks[k].lower) && !isNoun(toks[k].lower)) k++
            val noun = toks.getOrNull(k)?.takeIf { it.isWord && isNoun(it.lower) } ?: return null
            // Compound noun ("the bus stop is"): the next word is a noun too and the one after is a verb -> not our verb slot.
            val plural = t.lower in pluralDeterminers || isPluralNoun(noun.lower)
            return Subject(if (plural) Person.PLURAL else Person.THIRD_SINGULAR, k, noun.lower)
        }
        return null
    }

    /** Index of the verb following a subject (skipping frequency adverbs), or -1. */
    private fun verbIndexAfter(toks: List<Tok>, subjectEnd: Int): Int {
        var j = subjectEnd + 1
        while (j < toks.size && toks[j].isWord && toks[j].lower in frequencyAdverbs) j++
        val t = toks.getOrNull(j) ?: return -1
        if (!t.isWord) return -1
        // After a noun subject, a word followed by another finite verb is part of the subject, not the verb:
        // "the bus stop is near", "the phone case looks nice".
        val next = toks.getOrNull(j + 1)
        if (subjectPronouns[toks[subjectEnd].lower] == null && next != null && next.isWord && isFiniteVerb(next.lower)) return -1
        return j
    }

    private val finiteAuxiliaries = setOf("is", "are", "was", "were", "am", "has", "have", "had", "does", "do", "did", "can", "will", "would",
        "should", "could", "must", "might", "may", "doesn't", "don't", "didn't", "isn't", "aren't", "wasn't", "weren't", "won't", "can't")
    private fun isFiniteVerb(word: String): Boolean = word in finiteAuxiliaries ||
        dictionary.resolveEnglish(word, setOf(PartOfSpeech.VERB)).any { it.second.form == EnglishForm.THIRD_PERSON || it.second.form == EnglishForm.PAST || it.second.form == EnglishForm.PAST_OR_PARTICIPLE }

    private fun hasPastMarker(toks: List<Tok>): Boolean {
        for (i in toks.indices) {
            val w = toks[i].lower
            if (w in pastMarkers) return true
            if (w == "last" && toks.getOrNull(i + 1)?.lower in pastMarkerNouns) return true
        }
        return false
    }

    private fun hasFutureMarker(toks: List<Tok>) = toks.any { it.lower in futureMarkers || it.lower.endsWith("'ll") }

    /** Present-tense verb in a sentence that talks about the past. Returns token indices edited. */
    private fun pastTense(toks: List<Tok>, edits: MutableList<Edit>): Set<Int> {
        if (!hasPastMarker(toks) || hasFutureMarker(toks)) return emptySet()
        val edited = mutableSetOf<Int>()
        for (i in toks.indices) {
            val subject = subjectAt(toks, i) ?: continue
            val j = verbIndexAfter(toks, subject.endIndex)
            if (j < 0) continue
            val v = toks[j]
            val past = when (v.lower) {
                "am", "is" -> "was"
                "are" -> if (subject.person == Person.THIRD_SINGULAR || subject.person == Person.FIRST_SINGULAR) "was" else "were"
                "don't", "doesn't" -> "didn't"
                "do", "does" -> if (toks.getOrNull(j + 1)?.lower == "not") "did" else null
                "have", "has" -> if (toks.getOrNull(j + 1)?.let { n -> n.isWord && participleOf(n.lower) } == true) null else "had"
                else -> verbLemma(v.lower)?.let { EnglishMorphology.pastTense(it) }
            } ?: continue
            if (toks.getOrNull(j - 1)?.lower in auxiliaryContext) continue
            edits += Edit(v.start, v.end, matchCase(past, v.text), GrammarIssueType.VERB_TENSE,
                "Past tense: the sentence talks about the past, so use “$past”")
            edited += j
        }
        return edited
    }

    private fun participleOf(word: String) =
        dictionary.resolveEnglish(word, setOf(PartOfSpeech.VERB)).any { it.second.form == EnglishForm.PAST_PARTICIPLE || it.second.form == EnglishForm.PAST_OR_PARTICIPLE }

    private fun agreement(toks: List<Tok>, edits: MutableList<Edit>, skip: Set<Int>) {
        for (i in toks.indices) {
            val subject = subjectAt(toks, i) ?: continue
            // "If it were", "as if he were": subjunctive is correct.
            val subjunctive = toks.getOrNull(i - 1)?.lower in setOf("if", "wish") || (i >= 2 && toks[i - 2].lower == "as" && toks[i - 1].lower == "if")
            val j = verbIndexAfter(toks, subject.endIndex)
            if (j < 0 || j in skip) continue
            val v = toks[j]
            if (toks.getOrNull(j - 1)?.lower in auxiliaryContext) continue
            val p = subject.person
            val fixed: String = when (v.lower) {
                "am" -> when (p) { Person.FIRST_SINGULAR -> null; Person.THIRD_SINGULAR -> "is"; else -> "are" }
                "is" -> when (p) { Person.FIRST_SINGULAR -> "am"; Person.THIRD_SINGULAR -> null; else -> "are" }
                "are" -> when (p) { Person.FIRST_SINGULAR -> "am"; Person.THIRD_SINGULAR -> "is"; else -> null }
                "was" -> if (p == Person.SECOND || p == Person.PLURAL) "were" else null
                "were" -> if ((p == Person.FIRST_SINGULAR || p == Person.THIRD_SINGULAR) && !subjunctive) "was" else null
                "has" -> if (p == Person.THIRD_SINGULAR) null else "have"
                "have" -> if (p == Person.THIRD_SINGULAR) "has" else null
                "does" -> if (p == Person.THIRD_SINGULAR) null else "do"
                "do" -> if (p == Person.THIRD_SINGULAR) "does" else null
                "doesn't" -> if (p == Person.THIRD_SINGULAR) null else "don't"
                "don't" -> if (p == Person.THIRD_SINGULAR) "doesn't" else null
                else -> {
                    val lemma = verbLemma(v.lower) ?: continue
                    val third = EnglishMorphology.thirdPerson(lemma)
                    when {
                        lemma == third -> null
                        p == Person.THIRD_SINGULAR && v.lower == lemma -> third
                        p != Person.THIRD_SINGULAR && v.lower == third -> lemma
                        else -> null
                    }
                }
            } ?: continue
            edits += Edit(v.start, v.end, matchCase(fixed, v.text), GrammarIssueType.SUBJECT_VERB_AGREEMENT,
                "Subject–verb agreement: “${subject.text}” takes “$fixed”")
        }
    }

    private fun startsWithVowelSound(word: String): Boolean {
        val w = word.lowercase()
        if (w.isEmpty() || w.all { it.isUpperCase() || it.isDigit() }) return false
        val anExceptions = listOf("hour", "honest", "honor", "honour", "heir")
        val aExceptions = listOf("uni", "use", "usu", "uti", "eu", "one", "once", "ur", "ufo", "ewe", "uk", "us ")
        if (anExceptions.any { w.startsWith(it) }) return true
        if (aExceptions.any { w.startsWith(it.trim()) }) return false
        return w[0] in "aeiou"
    }

    private fun articles(toks: List<Tok>, edits: MutableList<Edit>) {
        for (i in 0 until toks.size - 1) {
            val a = toks[i]; val next = toks[i + 1]
            if (!next.isWord) continue
            // a / an
            if (a.lower == "a" || a.lower == "an") {
                if (!isKnown(next.lower)) continue
                val want = if (startsWithVowelSound(next.lower)) "an" else "a"
                if (want != a.lower) edits += Edit(a.start, a.end, matchCase(want, a.text), GrammarIssueType.ARTICLE,
                    if (want == "an") "Use “an” before a vowel sound" else "Use “a” before a consonant sound")
                continue
            }
            // to/at/in/from + bare countable place noun
            if (a.lower in setOf("to", "at", "in", "from", "into") && next.lower in placeNounsNeedingArticle && next.lower !in bareNounsAllowed) {
                edits += Edit(next.start, next.start, "the ", GrammarIssueType.MISSING_ARTICLE, "Add “the” before “${next.text}”")
                continue
            }
            // I am / he is / she was + bare profession
            if ((a.lower in setOf("am", "is", "was", "i'm", "he's", "she's")) && next.lower in professionNouns) {
                val art = if (startsWithVowelSound(next.lower)) "an" else "a"
                edits += Edit(next.start, next.start, "$art ", GrammarIssueType.MISSING_ARTICLE, "Add “$art” before a job title")
            }
        }
    }

    private fun confusables(toks: List<Tok>, edits: MutableList<Edit>) {
        for (i in 0 until toks.size - 1) {
            val a = toks[i]; val b = toks[i + 1]
            if (!a.isWord || !b.isWord) continue
            when {
                a.lower in setOf("could", "should", "would", "must", "might") && b.lower == "of" ->
                    edits += Edit(b.start, b.end, matchCase("have", b.text), GrammarIssueType.WORD_CHOICE, "“${a.lower} have”, not “${a.lower} of”")
                a.lower == "their" && b.lower in setOf("is", "are", "was", "were") ->
                    edits += Edit(a.start, a.end, matchCase("there", a.text), GrammarIssueType.WORD_CHOICE, "“There is/are” states that something exists")
                a.lower == "your" && b.lower in setOf("welcome", "right", "wrong", "going", "not") ->
                    edits += Edit(a.start, a.end, matchCase("you're", a.text), GrammarIssueType.WORD_CHOICE, "“You're” = you are")
                a.lower == "its" && b.lower in setOf("a", "an", "the", "so", "very", "not", "been", "going", "time", "ok", "okay", "fine", "late", "too", "really") ->
                    edits += Edit(a.start, a.end, matchCase("it's", a.text), GrammarIssueType.WORD_CHOICE, "“It's” = it is")
                b.lower == "then" && (a.lower in setOf("more", "less", "better", "worse", "rather", "other") ||
                    (a.lower.endsWith("er") && isAdjective(a.lower.dropLast(2)) || a.lower.endsWith("ier"))) && a.lower !in setOf("never", "ever", "over", "after", "under") ->
                    edits += Edit(b.start, b.end, matchCase("than", b.text), GrammarIssueType.WORD_CHOICE, "Comparisons use “than”")
            }
        }
    }

    private fun capitalisation(toks: List<Tok>, sentence: String, edits: MutableList<Edit>) {
        val first = toks.firstOrNull { it.isWord } ?: return
        if (first.text.first().isLowerCase() && sentence.trimEnd().lastOrNull() in setOf('.', '!', '?')) {
            edits += Edit(first.start, first.start + 1, first.text.first().uppercase(), GrammarIssueType.CAPITALIZATION, "Start the sentence with a capital letter")
        }
        for (t in toks) if (t.isWord && (t.text == "i" || t.text.startsWith("i'"))) {
            edits += Edit(t.start, t.start + 1, "I", GrammarIssueType.CAPITALIZATION, "“I” is always capitalised")
        }
    }

    private fun spacing(sentence: String, edits: MutableList<Edit>) {
        Regex(" +([,.!?;:])").findAll(sentence).forEach {
            if (it.range.last + 1 < sentence.length && sentence[it.range.last + 1] == '.') return@forEach // ellipsis " ..."
            edits += Edit(it.range.first, it.range.last + 1, it.groupValues[1], GrammarIssueType.PUNCTUATION, "No space before “${it.groupValues[1]}”")
        }
        Regex("([,;])([A-Za-z])").findAll(sentence).forEach {
            edits += Edit(it.range.first, it.range.first + 1, it.groupValues[1] + " ", GrammarIssueType.PUNCTUATION, "Add a space after “${it.groupValues[1]}”")
        }
    }
}
