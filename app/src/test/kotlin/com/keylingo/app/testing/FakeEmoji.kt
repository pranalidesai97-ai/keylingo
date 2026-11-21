package com.keylingo.app.testing

import com.keylingo.app.data.emoji.EmojiCatalog
import com.keylingo.app.data.emoji.EmojiUsageStore
import com.keylingo.app.data.emoji.EmojiUse
import com.keylingo.app.domain.model.EmojiCategory
import com.keylingo.app.domain.repository.EmojiProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** The real bundled emoji catalogue, as a phone with Unicode Emoji [maxVersion] support would show it. */
fun realEmojiCatalog(maxVersion: Double = 15.1): EmojiCatalog =
    EmojiCatalog.parse(TestData.assetLines(EmojiCatalog.ASSET).asSequence()) { _, version -> version <= maxVersion }

/** Real catalogue + in-memory usage with the same ranking as the DataStore-backed store. */
class TestEmojiProvider(
    private val catalog: EmojiCatalog = realEmojiCatalog(),
    private val clock: () -> Long = { System.nanoTime() },
) : EmojiProvider {
    val uses = MutableStateFlow<List<EmojiUse>>(emptyList())
    override suspend fun categories(): Map<EmojiCategory, List<String>> = catalog.byCategory
    override suspend fun suggestionsFor(word: String, languageCode: String, limit: Int) = catalog.suggestionsFor(word, languageCode, limit)
    override suspend fun search(query: String, limit: Int) = catalog.search(query, limit)
    override val usage: Flow<List<String>> = uses.map { list -> list.map { it.emoji } }
    override suspend fun recordUse(emoji: String) { uses.value = EmojiUsageStore.recordInto(uses.value, emoji, clock()) }
}
