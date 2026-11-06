package com.keylingo.app.domain.model

/**
 * Which conjugated surface form a translation should use. Nouns,
 * adjectives, adverbs and pronouns only ever have [BASE]; verbs can be
 * resolved in any of the three depending on the grammar pattern the
 * [com.keylingo.app.domain.repository.HintProvider] detected around them
 * (e.g. "going to ___" implies [INFINITIVE], "am/is/are ___ing" implies
 * [GERUND]).
 */
enum class GrammaticalForm {
    /** Dictionary base form: for verbs, the present-tense "yo" form (e.g. "trabajo"). */
    BASE,

    /** Infinitive form (e.g. "trabajar"), used after "to", "going to", "want to", etc. */
    INFINITIVE,

    /** Gerund/progressive form (e.g. "trabajando"), used after "am/is/are". */
    GERUND;

    /** Short label suitable for display under a hint chip, e.g. "infinitive". */
    fun displayLabel(): String = when (this) {
        BASE -> "verb"
        INFINITIVE -> "infinitive"
        GERUND -> "gerund"
    }
}
