package com.keylingo.app.data.language

import com.keylingo.app.data.lexicon.Lexicon

/**
 * Decides whether the sentence being typed is English or Spanish, so
 * autocomplete, learning and hints use the right language and never mix
 * the two. Deterministic evidence counting:
 *
 * - Spanish-only characters (á é í ó ú ñ ü ¿ ¡) add one point of Spanish evidence;
 * - a word counts as English only if it is reasonably common English
 *   (rank <= [COMMON_ENGLISH_RANK]) -- the English list also contains rare
 *   words such as "yo" or "comer" that are everyday Spanish;
 * - a word counts as Spanish if it is in the Spanish lexicon; when it is
 *   also common English, it counts only if it is far more common in Spanish
 *   (a top-[COMMON_SPANISH_RANK] Spanish word that is not top-[VERY_COMMON_ENGLISH_RANK] English);
 * - English is the default when evidence is absent or tied, because
 *   KeyLingo's primary audience types English.
 */
class TypingLanguageDetector(private val english: Lexicon, private val spanish: Lexicon) {

    private val wordRegex = Regex("[\\p{L}']+")
    private val spanishChars = Regex("[áéíóúñü¿¡]", RegexOption.IGNORE_CASE)

    /**
     * With [preferSpanish] (the user chose Spanish as typing language) a single piece of Spanish evidence
     * is enough when it outweighs the English evidence -- text with no Spanish evidence, or as much English
     * as Spanish, such as an English sentence, is still English. The setting is a preference, never a reason to treat English as Spanish.
     */
    fun detect(sentence: String, preferSpanish: Boolean = false): String {
        var es = 0.0
        var en = 0.0
        if (spanishChars.containsMatchIn(sentence)) es += 1.0 // the accented word itself also counts below
        val words = wordRegex.findAll(sentence).map { it.value }.toList()
        for (w in words) {
            if (w.length < 2) continue
            val englishRank = english.find(w)?.takeIf { it.suggestable }?.rank
            val spanishRank = spanish.findByKey(w).minOfOrNull { it.rank }
            val inEn = englishRank != null && englishRank <= COMMON_ENGLISH_RANK
            val inEs = spanishRank != null
            when {
                inEn && !inEs -> en += 1.0
                inEs && !inEn -> es += 1.0
                inEn && inEs && spanishRank!! <= COMMON_SPANISH_RANK && englishRank!! > VERY_COMMON_ENGLISH_RANK -> es += 1.0
            }
        }
        // Spanish mode, word still being typed: "tambi" starts only Spanish words, so it is Spanish evidence.
        // A prefix shared with any common English word ("hel", "ho") is not, so English typing stays English.
        if (preferSpanish && words.isNotEmpty() && sentence.lastOrNull()?.isLetter() == true) {
            val last = words.last()
            if (english.find(last) == null && spanish.findByKey(last).isEmpty() && last.length >= 2 &&
                english.prefixMatches(last, 3).none { it.rank <= COMMON_ENGLISH_RANK } && spanish.prefixMatches(last, 1).isNotEmpty()
            ) es += 1.0
        }
        // Spanish mode needs only one piece of Spanish evidence, but still more than the English evidence:
        // "Thank yo" (yo is also Spanish) or "Good mor" is English being typed, not Spanish.
        return if (preferSpanish) { if (es > en) "es" else "en" }
        else if (es > en && es >= 2.0) "es" else "en"
    }

    private companion object {
        const val COMMON_ENGLISH_RANK = 15_000
        const val VERY_COMMON_ENGLISH_RANK = 3_000
        const val COMMON_SPANISH_RANK = 200
    }
}
