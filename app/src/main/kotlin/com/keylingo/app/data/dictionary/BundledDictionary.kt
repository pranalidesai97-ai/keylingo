package com.keylingo.app.data.dictionary

import com.keylingo.app.data.language.EnglishAnalysis
import com.keylingo.app.data.language.EnglishForm
import com.keylingo.app.data.language.EnglishMorphology
import com.keylingo.app.data.language.spanish.Gender
import com.keylingo.app.data.language.spanish.SpanishConjugator
import com.keylingo.app.data.language.spanish.SpanishMorphology
import com.keylingo.app.data.language.spanish.SpanishPerson
import com.keylingo.app.data.language.spanish.SpanishTense
import com.keylingo.app.data.language.spanish.SpanishText
import com.keylingo.app.data.language.spanish.VerbFormKind
import com.keylingo.app.domain.model.CefrLevel
import com.keylingo.app.domain.model.DictionaryEntry
import com.keylingo.app.domain.model.PartOfSpeech

/** How a Spanish surface form relates to a dictionary entry, for Spanish -> English glosses. */
data class SpanishFormMatch(
    val entry: DictionaryEntry,
    val surface: String,
    val tense: SpanishTense?,
    val person: SpanishPerson?,
    val kind: VerbFormKind?,
    val plural: Boolean
)

/**
 * The bundled English -> Spanish dictionary, held in memory (a few thousand
 * rows -- well under 1 MB). Entries are lemma-based and POS-aware, so
 * "love" (verb: amar) and "love" (noun: amor) coexist; callers choose the
 * sense from grammatical context via [sense].
 *
 * The reverse index maps every generated Spanish surface form (conjugated
 * verbs, plurals, feminine adjectives), accent-folded, back to its entry --
 * that is what makes Spanish -> English glosses and accent restoration work
 * for words the user types in Spanish.
 */
class BundledDictionary(val entries: List<DictionaryEntry>) {

    private val byLemma: Map<String, List<DictionaryEntry>> = entries.groupBy { it.sourceWord }
    private val reverse: Map<String, List<SpanishFormMatch>> = buildReverseIndex(entries)

    val size: Int get() = entries.size

    fun senses(lemma: String): List<DictionaryEntry> = byLemma[lemma.lowercase()].orEmpty()

    /** The sense of [lemma] with part of speech [pos], or -- when [pos] is null -- the first listed sense. */
    fun sense(lemma: String, pos: PartOfSpeech? = null): DictionaryEntry? {
        val all = senses(lemma)
        return if (pos == null) all.firstOrNull() else all.firstOrNull { it.partOfSpeech == pos }
    }

    /**
     * Resolves an English surface word (any inflection) to (entry, analysis)
     * pairs, most plausible first, optionally restricted to [allowed] parts of speech.
     */
    fun resolveEnglish(word: String, allowed: Set<PartOfSpeech>? = null): List<Pair<DictionaryEntry, EnglishAnalysis>> {
        val out = mutableListOf<Pair<DictionaryEntry, EnglishAnalysis>>()
        for (analysis in EnglishMorphology.analyze(word)) {
            for (entry in senses(analysis.lemma)) {
                if (allowed != null && entry.partOfSpeech !in allowed) continue
                if (!formFits(entry.partOfSpeech, analysis.form)) continue
                if (out.none { it.first.id == entry.id }) out.add(entry to analysis)
            }
        }
        return out
    }

    private fun formFits(pos: PartOfSpeech, form: EnglishForm): Boolean = when (form) {
        EnglishForm.BASE -> true
        EnglishForm.PLURAL -> pos == PartOfSpeech.NOUN
        EnglishForm.PAST_PARTICIPLE -> pos == PartOfSpeech.VERB || pos == PartOfSpeech.ADJECTIVE
        EnglishForm.THIRD_PERSON, EnglishForm.PAST, EnglishForm.PAST_OR_PARTICIPLE, EnglishForm.GERUND -> pos == PartOfSpeech.VERB
        EnglishForm.COMPARATIVE, EnglishForm.SUPERLATIVE -> pos == PartOfSpeech.ADJECTIVE || pos == PartOfSpeech.ADVERB
    }

    /** Spanish surface form (any accents or none) -> matching entries. */
    fun lookupSpanish(word: String): List<SpanishFormMatch> = reverse[SpanishText.fold(word)].orEmpty()

    /** Every distinct Spanish surface form with the entry it came from -- feeds the Spanish typing lexicon. */
    fun allSpanishForms(): Collection<List<SpanishFormMatch>> = reverse.values

    companion object {

        /** CEFR band approximated from English frequency rank (documented heuristic; replace with curated levels when available). */
        fun cefrForRank(rank: Int): CefrLevel = when {
            rank <= 800 -> CefrLevel.A1
            rank <= 2000 -> CefrLevel.A2
            rank <= 5000 -> CefrLevel.B1
            rank <= 12000 -> CefrLevel.B2
            else -> CefrLevel.C1
        }

        /**
         * Parses `en_es.tsv`: english lemma, spanish, pos, gender. [englishRank]
         * supplies the frequency rank of the English lemma (from the lexicon).
         */
        fun parseTsv(lines: Sequence<String>, englishRank: (String) -> Int, targetLanguage: String = "es"): BundledDictionary {
            val entries = mutableListOf<DictionaryEntry>()
            var id = 1L
            for (line in lines) {
                if (line.isBlank() || line.startsWith("#")) continue
                val cols = line.split('\t')
                if (cols.size < 3) continue
                val en = cols[0].trim().lowercase()
                val es = cols[1].trim()
                if (en.isEmpty() || es.isEmpty()) continue
                val pos = PartOfSpeech.fromCode(cols[2].trim())
                val genderField = cols.getOrNull(3)?.trim().orEmpty()
                val rank = englishRank(en)
                entries.add(
                    DictionaryEntry(
                        id = id++,
                        sourceWord = en,
                        baseTranslation = es,
                        infinitiveTranslation = if (pos == PartOfSpeech.VERB) es else null,
                        gerundTranslation = if (pos == PartOfSpeech.VERB) SpanishConjugator.gerund(es) else null,
                        sourceLanguage = "en",
                        targetLanguage = targetLanguage,
                        partOfSpeech = pos,
                        frequencyRank = rank,
                        cefrLevel = cefrForRank(rank),
                        gender = if (pos == PartOfSpeech.NOUN) genderField.removeSuffix("p").ifEmpty { "m" } else null,
                        pluralOnly = genderField.endsWith("p")
                    )
                )
            }
            return BundledDictionary(entries)
        }

        private fun buildReverseIndex(entries: List<DictionaryEntry>): Map<String, List<SpanishFormMatch>> {
            val index = HashMap<String, MutableList<SpanishFormMatch>>(entries.size * 12)
            fun put(surface: String, match: SpanishFormMatch) {
                val list = index.getOrPut(SpanishText.fold(surface)) { mutableListOf() }
                if (list.none { it.entry.id == match.entry.id && it.surface == match.surface }) list.add(match)
            }
            for (e in entries) {
                val es = e.baseTranslation
                when (e.partOfSpeech) {
                    PartOfSpeech.VERB -> {
                        val parts = SpanishConjugator.parse(es)
                        if (parts.tail.isNotEmpty()) { put(es, SpanishFormMatch(e, es, null, null, VerbFormKind.INFINITIVE, false)); continue }
                        put(es, SpanishFormMatch(e, es, null, null, VerbFormKind.INFINITIVE, false))
                        for ((form, features) in SpanishConjugator.allForms(es)) {
                            put(form, SpanishFormMatch(e, form, features.tense, features.person, features.kind, features.person?.isPlural == true))
                        }
                    }
                    PartOfSpeech.NOUN -> {
                        put(es, SpanishFormMatch(e, es, null, null, null, e.pluralOnly))
                        if (!e.pluralOnly && !es.contains(' ')) put(SpanishMorphology.plural(es), SpanishFormMatch(e, SpanishMorphology.plural(es), null, null, null, true))
                    }
                    PartOfSpeech.ADJECTIVE -> {
                        if (es.contains(' ')) { put(es, SpanishFormMatch(e, es, null, null, null, false)); continue }
                        for (g in Gender.entries) for (pl in listOf(false, true)) {
                            val f = SpanishMorphology.agreeAdjective(es, g, pl)
                            put(f, SpanishFormMatch(e, f, null, null, null, pl))
                        }
                    }
                    else -> put(es, SpanishFormMatch(e, es, null, null, null, false))
                }
            }
            return index
        }
    }
}
