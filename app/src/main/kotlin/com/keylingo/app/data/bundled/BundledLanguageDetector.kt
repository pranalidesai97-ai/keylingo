package com.keylingo.app.data.bundled

import com.keylingo.app.domain.repository.LanguageDetector
import com.keylingo.app.data.AppDispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BundledLanguageDetector @Inject constructor(
    private val source: BundledDataSupplier,
    private val dispatchers: AppDispatchers
) : LanguageDetector {
    override suspend fun detect(sentence: String): String {
        val data = source.get() ?: return "en"
        return withContext(dispatchers.compute) { data.languageDetector.detect(sentence) }
    }
}
