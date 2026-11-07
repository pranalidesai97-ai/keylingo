package com.keylingo.app.keyboard

/**
 * Thin seam between [KeyboardViewModel] and Android's InputConnection, so
 * the ViewModel stays plain, testable Kotlin. Only text near the cursor is
 * ever read, and only to compute suggestions/hints -- it is never stored.
 */
interface KeyboardTextConnection {
    /** Commit literal text at the cursor (replacing any selection) -- this IS the user's real message. */
    fun commitText(text: String)

    /** Delete [charCount] UTF-16 chars before the cursor (callers pass whole graphemes / whole words). */
    fun deleteCharactersBackward(charCount: Int)

    /** Backspace semantics: delete the selection if there is one, else one grapheme, else send a DEL key event (for empty fields / custom editors). */
    fun deleteBackward(graphemeLength: Int)

    /** Perform the field's declared IME action (Send/Search/Done...) or insert a newline. */
    fun performEnterAction()

    /** Up to [maxChars] characters before the cursor ("" when unavailable). */
    fun textBeforeCursor(maxChars: Int): String

    /** Up to [maxChars] characters after the cursor. */
    fun textAfterCursor(maxChars: Int): String

    /** Group several edits so the app sees them atomically (no flicker, one undo step). */
    /** True while the user has text selected (an "empty-looking" field may still hold selected text, e.g. a browser URL). */
    fun hasSelection(): Boolean = false

    fun beginBatchEdit() {}
    fun endBatchEdit() {}
}
