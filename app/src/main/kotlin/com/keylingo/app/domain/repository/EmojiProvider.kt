package com.keylingo.app.domain.repository

import com.keylingo.app.domain.model.EmojiCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Offline emoji catalogue, keyword search, smart suggestions and the user's
 * recently/frequently used emoji. Everything stays on the device.
 */
interface EmojiProvider {
    /** Emoji per panel category (without [EmojiCategory.RECENT]), only those the device can draw. Empty if unavailable. */
    suspend fun categories(): Map<EmojiCategory, List<String>>

    /** Emoji for a word just typed ("happy" -> 😊 😍 🥳), in [languageCode]; empty when the word is not emoji-related. */
    suspend fun suggestionsFor(word: String, languageCode: String, limit: Int = 6): List<String>

    /** Emoji whose English or Spanish keywords start with every word of [query]. */
    suspend fun search(query: String, limit: Int = 60): List<String>

    /** Used emoji, most frequently used first (then most recent). */
    val usage: Flow<List<String>>

    suspend fun recordUse(emoji: String)

    companion object {
        /** No emoji data: the keyboard keeps its static emoji list. Used by tests and previews. */
        val NONE: EmojiProvider = object : EmojiProvider {
            override suspend fun categories(): Map<EmojiCategory, List<String>> = emptyMap()
            override suspend fun suggestionsFor(word: String, languageCode: String, limit: Int): List<String> = emptyList()
            override suspend fun search(query: String, limit: Int): List<String> = emptyList()
            override val usage: Flow<List<String>> = emptyFlow()
            override suspend fun recordUse(emoji: String) {}
        }
    }
}
