package com.keylingo.app.domain.model

/**
 * Coarse part-of-speech tagging used purely to decide which words are
 * worth surfacing as learning hints (verbs/nouns/adjectives/pronouns are
 * "meaningful"; articles/prepositions/conjunctions are usually skipped).
 */
enum class PartOfSpeech {
    NOUN,
    VERB,
    ADJECTIVE,
    ADVERB,
    PRONOUN,
    ARTICLE,
    PREPOSITION,
    CONJUNCTION,
    OTHER;

    companion object {
        fun fromCode(code: String): PartOfSpeech = when (code.lowercase()) {
            "noun", "n" -> NOUN
            "verb", "v" -> VERB
            "adj", "adjective" -> ADJECTIVE
            "adv", "adverb" -> ADVERB
            "pron", "pronoun" -> PRONOUN
            "art", "article" -> ARTICLE
            "prep", "preposition" -> PREPOSITION
            "conj", "conjunction" -> CONJUNCTION
            else -> OTHER
        }
    }
}
