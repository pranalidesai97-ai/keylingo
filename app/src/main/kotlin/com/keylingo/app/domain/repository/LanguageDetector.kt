package com.keylingo.app.domain.repository

/** Decides which language ("en" or "es") the current sentence is being typed in. */
interface LanguageDetector {
    suspend fun detect(sentence: String): String
}
