package com.keylingo.app.data.language

import com.keylingo.app.domain.repository.LanguagePack
import javax.inject.Inject
import javax.inject.Singleton

/**
 * English -> Spanish learning pack. Adding a second language later means
 * writing another small class like this one plus a new asset file --
 * nothing else in the app needs to change. Note this describes the
 * language being *taught*; stopwords and other source-typing concerns
 * moved to [com.keylingo.app.data.language.EnglishSourceLanguageRules].
 */
@Singleton
class SpanishLanguagePack @Inject constructor() : LanguagePack {

    override val languageCode: String = "es"
    override val displayName: String = "Spanish"
    override val dictionaryAssetPath: String = "dictionary/en_es.json"
    override val phraseChunkAssetPath: String = "dictionary/chunks_en_es.json"
}
