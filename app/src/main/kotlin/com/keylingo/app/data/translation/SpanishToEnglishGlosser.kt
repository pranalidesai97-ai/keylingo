package com.keylingo.app.data.translation

import com.keylingo.app.data.dictionary.BundledDictionary
import com.keylingo.app.data.dictionary.SpanishFormMatch
import com.keylingo.app.data.language.EnglishMorphology
import com.keylingo.app.data.language.spanish.SpanishConjugator
import com.keylingo.app.data.language.spanish.SpanishPerson
import com.keylingo.app.data.language.spanish.SpanishTense
import com.keylingo.app.data.language.spanish.SpanishText
import com.keylingo.app.data.language.spanish.VerbFormKind
import com.keylingo.app.domain.model.PartOfSpeech

/** An English gloss for one Spanish word the user typed, e.g. "trabajé" -> "I worked" (past · yo). */
data class SpanishGloss(
    val spanish: String,
    /** The correctly accented spelling, which may differ from what was typed ("tambien" -> "también"). */
    val canonicalSpanish: String,
    val english: String,
    val label: String,
    val match: SpanishFormMatch?
)

/**
 * Spanish -> English word-level assistance. Uses the dictionary's reverse
 * index of generated forms, so it understands conjugations ("fuimos" ->
 * "we went"), plurals and agreement, and restores missing accents. A
 * small closed-class table covers Spanish function words the dictionary
 * (an English-headword resource) does not list.
 */
class SpanishToEnglishGlosser(private val dictionary: BundledDictionary) {

    private val functionWords: Map<String, Pair<String, String>> = mapOf(
        "el" to ("the" to "el"), "la" to ("the" to "la"), "los" to ("the" to "los"), "las" to ("the" to "las"),
        "un" to ("a" to "un"), "una" to ("a" to "una"), "unos" to ("some" to "unos"), "unas" to ("some" to "unas"),
        "de" to ("of / from" to "de"), "del" to ("of the" to "del"), "al" to ("to the" to "al"), "a" to ("to" to "a"),
        "en" to ("in / at" to "en"), "con" to ("with" to "con"), "por" to ("for / by" to "por"), "para" to ("for" to "para"),
        "y" to ("and" to "y"), "e" to ("and" to "e"), "o" to ("or" to "o"), "pero" to ("but" to "pero"),
        "que" to ("that" to "que"), "porque" to ("because" to "porque"), "si" to ("if" to "si"), "sí" to ("yes" to "sí"),
        "no" to ("no / not" to "no"), "muy" to ("very" to "muy"), "más" to ("more" to "más"), "mas" to ("more" to "más"),
        "me" to ("me" to "me"), "te" to ("you" to "te"), "se" to ("himself / herself" to "se"), "nos" to ("us" to "nos"),
        "lo" to ("it / him" to "lo"), "le" to ("to him / her" to "le"), "les" to ("to them" to "les"),
        "mi" to ("my" to "mi"), "mis" to ("my" to "mis"), "tu" to ("your" to "tu"), "tus" to ("your" to "tus"),
        "su" to ("his / her / their" to "su"), "sus" to ("his / her / their" to "sus"),
        "este" to ("this" to "este"), "esta" to ("this" to "esta"), "esto" to ("this" to "esto"),
        "ese" to ("that" to "ese"), "esa" to ("that" to "esa"), "eso" to ("that" to "eso"),
        "hay" to ("there is / are" to "hay"), "qué" to ("what" to "qué"), "cómo" to ("how" to "cómo"), "como" to ("like / as" to "como"),
        "dónde" to ("where" to "dónde"), "donde" to ("where" to "donde"), "cuándo" to ("when" to "cuándo"), "cuando" to ("when" to "cuando"),
        "quién" to ("who" to "quién"), "quien" to ("who" to "quien"), "también" to ("also" to "también"), "aquí" to ("here" to "aquí"),
        "ya" to ("already" to "ya"), "bien" to ("well / fine" to "bien"), "hola" to ("hello" to "hola"), "adiós" to ("goodbye" to "adiós"),
        "gracias" to ("thank you" to "gracias"), "sin" to ("without" to "sin"), "sobre" to ("about / on" to "sobre"),
    )

    private val pronounFor = mapOf(
        SpanishPerson.YO to "I", SpanishPerson.TU to "you", SpanishPerson.EL to "he/she", SpanishPerson.NOSOTROS to "we", SpanishPerson.ELLOS to "they",
    )

    fun gloss(word: String): SpanishGloss? {
        val lower = word.lowercase()
        // Exact function-word spelling first ("si" vs "sí" differ in meaning), then accent-insensitive.
        functionWords[lower]?.let { (en, canonical) -> return SpanishGloss(word, canonical, en, "function word", null) }
        val matches = dictionary.lookupSpanish(lower)
        // A reflexive verb form typed without its pronoun ("casas" from casarse) is an unlikely reading.
        fun reflexive(m: SpanishFormMatch) = m.entry.partOfSpeech == PartOfSpeech.VERB && SpanishConjugator.parse(m.entry.baseTranslation).reflexive
        val plausible = matches.filterNot { reflexive(it) }.ifEmpty { matches }
        val best = plausible.sortedWith(compareBy({ if (it.surface == lower) 0 else 1 }, { it.entry.frequencyRank })).firstOrNull()
        if (best == null) {
            functionWords.entries.firstOrNull { SpanishText.fold(it.key) == SpanishText.fold(lower) }?.let { (_, v) ->
                return SpanishGloss(word, v.second, v.first, "function word", null)
            }
            return null
        }
        val (english, label) = describe(best)
        // Genuinely ambiguous forms (fui/fue/fuimos = ser AND ir) show both readings, most frequent first.
        val alternative = plausible.asSequence()
            .filter { it.surface == best.surface && it.entry.baseTranslation != best.entry.baseTranslation }
            .sortedBy { it.entry.frequencyRank }
            .map { describe(it).first }
            .firstOrNull { it != english }
        return SpanishGloss(word, best.surface, if (alternative != null) "$english / $alternative" else english, label, best)
    }

    private fun describe(m: SpanishFormMatch): Pair<String, String> {
        val e = m.entry
        val lemma = e.sourceWord
        return when (e.partOfSpeech) {
            PartOfSpeech.VERB -> verbGloss(lemma, m)
            PartOfSpeech.NOUN -> (if (m.plural && !e.pluralOnly) pluralize(lemma) else lemma) to ("noun · ${e.gender ?: "m"}" + if (m.plural) " pl" else "")
            PartOfSpeech.ADJECTIVE -> lemma to "adjective"
            PartOfSpeech.ADVERB -> lemma to "adverb"
            else -> lemma to e.partOfSpeech.name.lowercase()
        }
    }

    private fun pluralize(noun: String): String = EnglishMorphology.thirdPerson(noun).let {
        if (noun.endsWith("o")) noun + "s" else it
    }

    private fun verbGloss(lemma: String, m: SpanishFormMatch): Pair<String, String> {
        val person = m.person
        return when (m.kind) {
            VerbFormKind.INFINITIVE, null -> "to $lemma" to "infinitive"
            VerbFormKind.GERUND -> EnglishMorphology.gerund(lemma) to "gerund"
            VerbFormKind.PARTICIPLE -> EnglishMorphology.pastTense(lemma) to "participle"
            VerbFormKind.FINITE -> {
                val subject = pronounFor[person] ?: ""
                val verb = when (m.tense) {
                    SpanishTense.PRESENT -> if (person == SpanishPerson.EL) EnglishMorphology.thirdPerson(lemma) else if (lemma == "be") beForm(person) else lemma
                    SpanishTense.PRETERITE -> if (lemma == "be") (if (person == SpanishPerson.YO || person == SpanishPerson.EL) "was" else "were") else EnglishMorphology.pastTense(lemma)
                    SpanishTense.IMPERFECT -> "used to $lemma"
                    SpanishTense.FUTURE -> "will $lemma"
                    SpanishTense.CONDITIONAL -> "would $lemma"
                    null -> lemma
                }
                "$subject $verb".trim() to "${m.tense?.name?.lowercase() ?: ""} · ${person?.name?.lowercase() ?: ""}"
            }
        }
    }

    private fun beForm(person: SpanishPerson?) = when (person) {
        SpanishPerson.YO -> "am"; SpanishPerson.EL -> "is"; else -> "are"
    }
}
