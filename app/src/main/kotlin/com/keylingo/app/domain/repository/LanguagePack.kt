package com.keylingo.app.domain.repository

/**
 * Describes the language being *taught* (Spanish today). What the user
 * types is analysed by the source-language components; bundled data paths
 * are owned by the data layer's asset loader.
 */
interface LanguagePack {

    /** ISO 639-1 code of the language being learned, e.g. "es". */
    val languageCode: String

    /** Human-readable display name shown in Settings, e.g. "Spanish". */
    val displayName: String
}
