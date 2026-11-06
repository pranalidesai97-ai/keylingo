package com.keylingo.app.data

/**
 * Shared "is this a real word worth recording, not a typo/garbage string"
 * check, used by both [com.keylingo.app.data.learning.RoomLearnedVocabularyProvider]
 * (personal vocabulary) and [com.keylingo.app.data.context.RoomContextPredictionProvider]
 * (bigram sequences) -- both need identical eligibility rules for the raw
 * words they persist, so this lives in one place rather than being
 * copy-pasted into each.
 *
 * Rejects anything too short, too long, containing digits/punctuation
 * (beyond a bare apostrophe, for contractions), or with 4+ repeated
 * consecutive characters (catches keyboard-mashing like "aaaaaa" and
 * elongation like "hellooooo"). Deterministic, no ML.
 */
object WordEligibility {

    fun isEligibleWord(word: String): Boolean {
        if (word.length < 2 || word.length > 30) return false
        if (!word.all { it.isLetter() || it == '\'' }) return false
        if (hasExcessiveRepeatedCharacters(word)) return false
        return true
    }

    private fun hasExcessiveRepeatedCharacters(word: String): Boolean {
        var run = 1
        for (i in 1 until word.length) {
            run = if (word[i] == word[i - 1]) run + 1 else 1
            if (run >= 4) return true
        }
        return false
    }
}
