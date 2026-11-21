package com.keylingo.app.data.emoji

import android.content.Context
import android.graphics.Paint
import android.os.Build
import android.util.Log
import com.keylingo.app.data.AppDispatchers
import com.keylingo.app.domain.model.EmojiCategory
import com.keylingo.app.domain.repository.EmojiProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Serves the bundled emoji catalogue (assets/emoji/emoji.tsv), loaded once on a
 * background thread, plus the on-device usage history. Only emoji the phone's
 * font can draw are offered, so nothing shows as an empty box on older Android.
 */
@Singleton
class BundledEmojiProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val usageStore: EmojiUsageStore,
    private val dispatchers: AppDispatchers,
) : EmojiProvider {

    private val mutex = Mutex()
    @Volatile private var cached: EmojiCatalog? = null
    @Volatile private var failed = false

    /** Loaded once; concurrent callers wait for the same load. A failed load is not retried (logged, no emoji data). */
    private suspend fun catalog(): EmojiCatalog? {
        cached?.let { return it }
        if (failed) return null
        return mutex.withLock {
            cached ?: if (failed) null else withContext(dispatchers.compute) { load() }.also { if (it == null) failed = true else cached = it }
        }
    }

    private fun load(): EmojiCatalog? = runCatching {
        val start = System.nanoTime()
        val check = glyphCheck()
        val catalog = context.assets.open(EmojiCatalog.ASSET).bufferedReader(Charsets.UTF_8).useLines { lines ->
            EmojiCatalog.parse(lines.toList().asSequence(), check)
        }
        Log.i(TAG, "Emoji catalogue loaded in ${(System.nanoTime() - start) / 1_000_000} ms (${catalog.entries.size} emoji)")
        catalog
    }.onFailure { Log.e(TAG, "Failed to load emoji data", it) }.getOrNull()

    override suspend fun categories(): Map<EmojiCategory, List<String>> = catalog()?.byCategory.orEmpty()

    override suspend fun suggestionsFor(word: String, languageCode: String, limit: Int): List<String> =
        catalog()?.let { c -> withContext(dispatchers.compute) { c.suggestionsFor(word, languageCode, limit) } }.orEmpty()

    override suspend fun search(query: String, limit: Int): List<String> =
        catalog()?.let { c -> withContext(dispatchers.compute) { c.search(query, limit) } }.orEmpty()

    override val usage: Flow<List<String>> = usageStore.usage.map { uses -> uses.map { it.emoji } }

    override suspend fun recordUse(emoji: String) = usageStore.record(emoji)

    companion object {
        private const val TAG = "KeyLingoEmoji"

        /** Newest Unicode emoji version each Android release draws with its system font. */
        fun maxEmojiVersion(sdk: Int): Double = when {
            sdk >= 36 -> 16.0
            sdk >= 35 -> 15.1
            sdk >= 34 -> 15.0
            sdk >= 33 -> 14.0
            sdk >= 31 -> 13.1
            sdk >= 30 -> 13.0
            sdk >= 29 -> 12.0
            sdk >= 28 -> 11.0
            else -> 5.0
        }

        /**
         * The Android release's emoji version, refined by Paint.hasGlyph (API 23+) when that check works
         * on this device (some vendor fonts add or lack emoji). If hasGlyph cannot even confirm 😀, it is
         * ignored and the version rule alone decides.
         */
        fun glyphCheck(sdk: Int = Build.VERSION.SDK_INT): (String, Double) -> Boolean {
            val max = maxEmojiVersion(sdk)
            val paint = Paint()
            val reliable = runCatching { paint.hasGlyph("😀") }.getOrDefault(false)
            return { emoji, version ->
                if (reliable) runCatching { paint.hasGlyph(emoji) }.getOrDefault(version <= max) else version <= max
            }
        }
    }
}
