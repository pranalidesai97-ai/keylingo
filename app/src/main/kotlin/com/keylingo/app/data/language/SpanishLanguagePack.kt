package com.keylingo.app.data.language

import com.keylingo.app.domain.repository.LanguagePack
import javax.inject.Inject
import javax.inject.Singleton

/** English -> Spanish learning pack (the language being taught). */
@Singleton
class SpanishLanguagePack @Inject constructor() : LanguagePack {
    override val languageCode: String = "es"
    override val displayName: String = "Spanish"
}
