package com.keylingo.app.domain.model

/** Emoji panel tabs, in display order. [icon] is the tab's own emoji; [code] matches assets/emoji/emoji.tsv. */
enum class EmojiCategory(val code: String, val icon: String, val label: String) {
    RECENT("recent", "🕘", "Recently used"),
    SMILEYS("smileys", "😀", "Smileys"),
    PEOPLE("people", "👋", "People"),
    ANIMALS("animals", "🐻", "Animals"),
    FOOD("food", "🍔", "Food"),
    TRAVEL("travel", "✈️", "Travel"),
    ACTIVITIES("activities", "⚽", "Activities"),
    OBJECTS("objects", "💡", "Objects"),
    SYMBOLS("symbols", "💕", "Symbols"),
    FLAGS("flags", "🏳️", "Flags");

    companion object {
        fun fromCode(code: String): EmojiCategory? = entries.firstOrNull { it.code == code }
    }
}
