package com.keylingo.app.domain.repository

/**
 * Describes one installable learning language (e.g. Spanish today; French,
 * German, Japanese later) -- i.e. the language being *taught*. What the
 * user is *typing* (the source language) is a separate concern, owned by
 * [SourceLanguageRules]; the two used to be conflated here (stopWords used
 * to live on this interface even though it filters the typed language,
 * not the taught one) -- see [SourceLanguageRules] for where that moved.
 * Adding a new language pack means implementing this interface and
 * pointing it at a new dictionary asset -- no changes to the keyboard,
 * hint bar, or settings UI are required.
 */
interface LanguagePack {

    /** ISO 639-1 code of the language being learned, e.g. "es". */
    val languageCode: String

    /** Human-readable display name shown in Settings, e.g. "Spanish". */
    val displayName: String

    /** Path of the bundled dictionary asset under assets/, e.g. "dictionary/en_es.json". */
    val dictionaryAssetPath: String

    /** Path of the bundled phrase-chunk asset under assets/, e.g. "dictionary/chunks_en_es.json". */
    val phraseChunkAssetPath: String
}
