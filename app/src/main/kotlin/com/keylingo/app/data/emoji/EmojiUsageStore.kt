package com.keylingo.app.data.emoji

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** One used emoji: how often and when it was last used. */
data class EmojiUse(val emoji: String, val count: Int, val lastUsedAt: Long)

/**
 * Recently / frequently used emoji, on this device only (its own DataStore file,
 * separate from the settings; app backup is disabled, so it never leaves the phone).
 * Ordering: most used first, then most recent. At most [MAX_ENTRIES] emoji are kept.
 */
@Singleton
class EmojiUsageStore @Inject constructor(@ApplicationContext private val context: Context) {

    val usage: Flow<List<EmojiUse>> = context.emojiDataStore.data.map { decode(it[KEY].orEmpty()) }

    suspend fun record(emoji: String, now: Long = System.currentTimeMillis()) {
        if (emoji.isBlank()) return
        context.emojiDataStore.edit { prefs -> prefs[KEY] = encode(recordInto(decode(prefs[KEY].orEmpty()), emoji, now)) }
    }

    suspend fun clear() { context.emojiDataStore.edit { it.remove(KEY) } }

    companion object {
        const val MAX_ENTRIES = 40
        private val KEY = stringPreferencesKey("emoji_usage")
        private const val FIELD = '\u001F'
        private const val RECORD = '\u001E'

        /** Most used first, then most recent. */
        fun ranked(uses: List<EmojiUse>): List<EmojiUse> =
            uses.sortedWith(compareByDescending<EmojiUse> { it.count }.thenByDescending { it.lastUsedAt })

        internal fun recordInto(uses: List<EmojiUse>, emoji: String, now: Long): List<EmojiUse> {
            val existing = uses.firstOrNull { it.emoji == emoji }
            // Over the cap, drop the least used (oldest first among equals) -- never the one just used.
            val others = ranked(uses.filter { it.emoji != emoji }).take(MAX_ENTRIES - 1)
            return ranked(others + EmojiUse(emoji, (existing?.count ?: 0) + 1, now))
        }

        internal fun encode(uses: List<EmojiUse>): String =
            uses.joinToString(RECORD.toString()) { "${it.emoji}$FIELD${it.count}$FIELD${it.lastUsedAt}" }

        internal fun decode(raw: String): List<EmojiUse> = if (raw.isEmpty()) emptyList() else
            ranked(raw.split(RECORD).mapNotNull { r ->
                val f = r.split(FIELD)
                if (f.size == 3) EmojiUse(f[0], f[1].toIntOrNull() ?: return@mapNotNull null, f[2].toLongOrNull() ?: 0L) else null
            })
    }
}

private val Context.emojiDataStore by preferencesDataStore(name = "keylingo_emoji_usage")
