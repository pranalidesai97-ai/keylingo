package com.keylingo.app.data.bundled

import com.keylingo.app.data.dictionary.BundledDictionary
import com.keylingo.app.data.grammar.EnglishGrammarChecker
import com.keylingo.app.data.language.TypingLanguageDetector
import com.keylingo.app.data.lexicon.Lexicon
import com.keylingo.app.data.lexicon.LexiconWord
import com.keylingo.app.data.translation.EnglishToSpanishTranslator
import com.keylingo.app.data.translation.Idiom
import com.keylingo.app.data.translation.SpanishToEnglishGlosser
import com.keylingo.app.data.translation.SpanishToEnglishTranslator
import com.keylingo.app.domain.model.PartOfSpeech

/** Cold-start next-word lists: previous word (lowercase) -> likely next words, best first. */
class SeedNextWords(private val table: Map<String, List<String>>) {
    fun after(previousWord: String): List<String> = table[previousWord.lowercase()].orEmpty()
    val size: Int get() = table.size

    companion object {
        fun parseTsv(lines: Sequence<String>): SeedNextWords {
            val map = LinkedHashMap<String, MutableList<String>>()
            for (line in lines) {
                if (line.isBlank() || line.startsWith("#")) continue
                val cols = line.split('\t').map { it.trim() }.filter { it.isNotEmpty() }
                if (cols.size < 2) continue
                val list = map.getOrPut(cols[0].lowercase()) { mutableListOf() }
                for (w in cols.drop(1)) if (w !in list) list.add(w)
            }
            return SeedNextWords(map)
        }
    }
}

/**
 * All bundled, read-only language data, parsed once into memory. User data
 * (learned words, personal n-grams) is NOT here -- it lives in Room
 * databases that never mix with these assets.
 */
class BundledLanguageData(
    val englishLexicon: Lexicon,
    val spanishLexicon: Lexicon,
    val dictionary: BundledDictionary,
    val idioms: List<Idiom>,
    val englishNextWords: SeedNextWords,
    val spanishNextWords: SeedNextWords,
    /** Curated English misspelling -> correction (lowercase keys), see lexicon/en_misspellings.tsv. */
    val englishMisspellings: Map<String, String> = emptyMap()
) {
    val translator = EnglishToSpanishTranslator(dictionary, idioms)
    /** Spanish -> English sentences; English casing ("Spanish", "Monday") comes from the lexicon. */
    val spanishTranslator = SpanishToEnglishTranslator(dictionary) { w -> englishLexicon.find(w)?.display ?: w }
    val glosser = SpanishToEnglishGlosser(dictionary)
    val languageDetector = TypingLanguageDetector(englishLexicon, spanishLexicon)
    val grammarChecker = EnglishGrammarChecker(englishLexicon, dictionary)

    fun lexiconFor(languageCode: String): Lexicon = if (languageCode == "es") spanishLexicon else englishLexicon
    fun nextWordsFor(languageCode: String): SeedNextWords = if (languageCode == "es") spanishNextWords else englishNextWords

    companion object {
        const val ENGLISH_LEXICON = "lexicon/en_words.tsv"
        const val DICTIONARY = "dictionary/en_es.tsv"
        const val IDIOMS = "dictionary/idioms_en_es.tsv"
        const val ES_EN_EXTRA = "dictionary/es_en_extra.tsv"
        const val EN_NEXT_WORDS = "prediction/en_next_words.tsv"
        const val ES_NEXT_WORDS = "prediction/es_next_words.tsv"
        const val EN_MISSPELLINGS = "lexicon/en_misspellings.tsv"

        /** Spanish function words with approximate frequency ranks (they are not dictionary headwords). */
        private val spanishCore = listOf(
            "de", "la", "que", "el", "en", "y", "a", "los", "se", "del", "las", "un", "por", "con", "no", "una", "su", "para",
            "es", "al", "lo", "como", "más", "pero", "sus", "le", "ya", "o", "este", "sí", "porque", "esta", "entre", "cuando",
            "muy", "sin", "sobre", "también", "me", "hasta", "hay", "donde", "quien", "desde", "todo", "nos", "durante", "todos",
            "uno", "les", "ni", "contra", "otros", "ese", "eso", "ante", "ellos", "e", "esto", "mí", "antes", "algunos", "qué",
            "unos", "yo", "otro", "otras", "otra", "él", "tanto", "esa", "estos", "mucho", "quienes", "nada", "muchos", "cual",
            "poco", "ella", "estar", "estas", "algunas", "algo", "nosotros", "mi", "mis", "tú", "te", "ti", "tu", "tus", "ellas",
            "nosotras", "os", "mío", "mía", "tuyo", "tuya", "suyo", "suya", "nuestro", "nuestra", "nuestros", "nuestras", "esos",
            "esas", "estoy", "estás", "está", "estamos", "están", "cómo", "dónde", "cuándo", "quién", "cuál", "hola", "adiós",
            "gracias", "bueno", "buena", "buenos", "buenas", "días", "noches", "tardes", "vale", "claro", "aquí", "allí", "ahora",
            "hoy", "mañana", "ayer", "bien", "mal", "siempre", "nunca", "conmigo", "contigo", "usted", "ustedes", "favor",
        )

        /**
         * Builds everything from raw asset text. [read] returns the lines of a
         * bundled asset; Android and unit tests supply different readers.
         */
        fun load(read: (String) -> Sequence<String>): BundledLanguageData {
            val english = Lexicon.parseTsv(read(ENGLISH_LEXICON), foldAccents = false)
            val dictionary = BundledDictionary.parseTsv(
                read(DICTIONARY), englishRank = { english.find(it)?.rank ?: 50_000 }, reverseOnlyLines = read(ES_EN_EXTRA)
            )
            val idioms = read(IDIOMS).filter { it.isNotBlank() && !it.startsWith("#") }.mapNotNull { line ->
                val cols = line.split('\t')
                if (cols.size >= 2) Idiom(cols[0].trim(), cols[1].trim()) else null
            }.toList()
            return BundledLanguageData(
                englishLexicon = english,
                spanishLexicon = buildSpanishLexicon(dictionary),
                dictionary = dictionary,
                idioms = idioms,
                englishNextWords = SeedNextWords.parseTsv(read(EN_NEXT_WORDS)),
                spanishNextWords = SeedNextWords.parseTsv(read(ES_NEXT_WORDS)),
                englishMisspellings = read(EN_MISSPELLINGS).filter { it.isNotBlank() && !it.startsWith("#") }
                    .mapNotNull { line -> line.split('\t').takeIf { it.size >= 2 }?.let { it[0].trim().lowercase() to it[1].trim() } }
                    .toMap()
            )
        }

        /**
         * Spanish typing vocabulary generated from the dictionary (every
         * conjugated form, plural and agreement form) plus core function
         * words. Rank approximates frequency via the English lemma's rank,
         * with finite verb forms slightly behind their lemma so common nouns
         * aren't buried under 30 forms of one verb.
         */
        private fun buildSpanishLexicon(dictionary: BundledDictionary): Lexicon {
            val words = ArrayList<LexiconWord>(30_000)
            spanishCore.forEachIndexed { index, w -> words.add(LexiconWord(w, index + 1, true)) }
            for (matches in dictionary.allSpanishForms()) {
                for (m in matches) {
                    if (m.surface.contains(' ')) continue
                    val base = m.entry.frequencyRank.coerceAtMost(40_000) + 150
                    val penalty = when {
                        m.entry.partOfSpeech != PartOfSpeech.VERB -> 0
                        m.kind == null -> 0
                        m.tense == null -> 50
                        m.tense.ordinal <= 1 -> 100 + m.tense.ordinal * 200
                        else -> 600 + m.tense.ordinal * 300
                    }
                    words.add(LexiconWord(m.surface, base + penalty, true))
                }
            }
            return Lexicon(words, foldAccents = true)
        }
    }
}
