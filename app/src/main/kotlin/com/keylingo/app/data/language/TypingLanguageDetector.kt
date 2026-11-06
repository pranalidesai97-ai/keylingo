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

    fun detect(sentence: String): String {
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
        return if (es > en && es >= 2.0) "es" else "en"
    }

    private companion object {
        const val COMMON_ENGLISH_RANK = 15_000
        const val VERY_COMMON_ENGLISH_RANK = 3_000
        const val COMMON_SPANISH_RANK = 200
    }
}
