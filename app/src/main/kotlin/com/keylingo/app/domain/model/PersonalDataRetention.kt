package com.keylingo.app.domain.model

/**
 * How long KeyLingo keeps personal typing history that never proved useful.
 * Everything stays on the device; this only bounds how much incidental
 * history (typos typed once, one-off word pairs) accumulates over time.
 * Learned words (3+ natural uses or 1 accepted suggestion) are kept until
 * the user deletes them.
 */
object PersonalDataRetention {
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** A word typed once or twice, never accepted, and unused this long is forgotten. */
    const val UNLEARNED_WORD_TTL_MS = 30 * DAY_MS

    /** A word pair/triple seen only once and unused this long is forgotten. */
    const val SINGLE_USE_SEQUENCE_TTL_MS = 60 * DAY_MS

    /** Upper bounds on stored sequences; the least recently used are dropped first. */
    const val MAX_BIGRAMS = 20_000
    const val MAX_TRIGRAMS = 30_000
}
