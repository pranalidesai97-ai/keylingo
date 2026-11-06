package com.keylingo.app.keyboard

/**
 * Thin seam between the presentation layer ([KeyboardViewModel]) and the
 * Android [android.view.inputmethod.InputConnection] the IME service
 * holds. Keeping this as an interface means the ViewModel is plain,
 * testable Kotlin with no Android Service dependency.
 */
interface KeyboardTextConnection {
    /** Commit literal text at the cursor -- this IS the user's real message. */
    fun commitText(text: String)

    /** Delete exactly one character before the cursor (or the selection, if any). */
    fun deleteOneCharacterBackward()

    /**
     * Delete [charCount] characters immediately before the cursor, without
     * touching the rest of the field. Used to remove the in-progress word
     * before committing an autocomplete replacement -- distinct from
     * [deleteOneCharacterBackward], which also special-cases an active
     * selection (not relevant here: autocomplete selection happens while
     * actively typing, never with a text selection active).
     */
    fun deleteCharactersBackward(charCount: Int)

    /** Perform the field's declared IME action (Next/Done/Search/...) or insert a newline. */
    fun performEnterAction()

    /** Read the full current text of the field being edited, for hint analysis only. */
    fun currentFieldText(): String
}
