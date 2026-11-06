package com.keylingo.app.domain.model

/** One learned word-sequence relationship, at the domain layer. */
data class Bigram(
    val previousWord: String,
    val nextWord: String,
    val count: Int
)
