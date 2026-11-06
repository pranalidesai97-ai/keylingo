package com.keylingo.app.data.language

import com.keylingo.app.data.lexicon.Lexicon

/**
 * Decides whether the sentence being typed is English or Spanish, so
 * autocomplete, learning and hints use the right language and never mix
 * the two. Deterministic evidence counting:
 *
 * - Spanish-only characters (á é í ó ú ñ ü ¿ ¡) are strong Spanish evidence;
 * - each word known to exactly one of the two lexicons counts for that
 *   language; words in both ("no", "me", "hospital", "come") are ignored;
 * - English is the default when evidence is absent or tied, because
 *   KeyLingo's primary audience types English.
 */
class TypingLanguageDetector(private val english: Lexicon, private val spanish: Lexicon) {

    private val wordRegex = Regex("[\\p{L}']+")
    private val spanishChars = Regex("[áéíóúñü¿¡]", RegexOption.IGNORE_CASE)

    fun detect(sentence: String): String {
        var es = 0.0
        var en = 0.0
        if (spanishChars.containsMatchIn(sentence)) es += 2.5
        val words = wordRegex.findAll(sentence).map { it.value }.toList()
        for (w in words) {
            if (w.length < 2) continue
            val inEn = english.contains(w)
            val inEs = spanish.findByKey(w).isNotEmpty()
            when {
                inEn && !inEs -> en += 1.0
                inEs && !inEn -> es += 1.0
            }
        }
        return if (es > en && es >= 2.0) "es" else "en"
    }
}
