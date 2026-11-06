package com.keylingo.app.domain.model

/** Where a suggestion came from -- drives ranking, UI styling and what accepting it teaches the learner model. */
enum class SuggestionKind {
    /** Completes the word being typed ("hel" -> "hello"). */
    COMPLETION,
    /** Fixes a probable typo or missing accent/apostrophe ("teh" -> "the", "tambien" -> "también", "dont" -> "don't"). */
    CORRECTION,
    /** Predicts the next word after a completed one ("I am" -> "going"). */
    PREDICTION,
    /** Exactly what the user typed, offered so they can keep it when it is not a known word. */
    LITERAL
}

/**
 * A single suggestion-strip candidate. [frequencyRank] follows the app-wide
 * convention (lower = more common, 0 = not from the bundled list).
 */
data class AutocompleteSuggestion(
    val word: String,
    val frequencyRank: Int,
    val kind: SuggestionKind = SuggestionKind.COMPLETION,
    val languageCode: String = "en"
)

/**
 * The full answer for one suggestion refresh. [autoCorrection], when set,
 * is the candidate that will replace the typed word if the user presses
 * space/punctuation next; it is also the first item in [suggestions] so the
 * user can see it coming (and avoid it).
 */
data class SuggestionResult(
    val suggestions: List<AutocompleteSuggestion>,
    val autoCorrection: AutocompleteSuggestion? = null
) {
    companion object {
        val EMPTY = SuggestionResult(emptyList())
    }
}
