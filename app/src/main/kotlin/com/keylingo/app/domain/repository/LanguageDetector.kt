package com.keylingo.app.domain.repository

/** Decides which language ("en" or "es") the current sentence is being typed in. */
interface LanguageDetector {
    suspend fun detect(sentence: String): String

    /** As [detect], biased towards Spanish when [preferSpanish]: one Spanish word is enough, but English text stays English. */
    suspend fun detect(sentence: String, preferSpanish: Boolean): String = detect(sentence)
}
