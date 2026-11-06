package com.keylingo.app.domain.model

enum class GrammarIssueType { REPEATED_WORD, SUBJECT_VERB_AGREEMENT, VERB_TENSE, ARTICLE, MISSING_ARTICLE, WORD_CHOICE, CAPITALIZATION, PUNCTUATION, SPELLING }

/** One detected problem: what was written, what to write instead, and a short explanation. */
data class GrammarIssue(val type: GrammarIssueType, val message: String, val original: String, val replacement: String)

/**
 * A suggested correction of the sentence being typed. It is only ever
 * offered: the text changes only if the user taps the suggestion.
 */
data class GrammarSuggestion(val original: String, val corrected: String, val issues: List<GrammarIssue>)
