package com.keylingo.app.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo

/** How the Enter key should behave/label itself for the current field. */
enum class EnterKeyAction { NEWLINE, SEND, SEARCH, GO, NEXT, DONE, PREVIOUS }

/**
 * What KeyLingo may do in the field currently being edited, derived only
 * from the field's declared [EditorInfo] input type and IME options --
 * never from its contents.
 *
 * Privacy rules (non-negotiable):
 * - password fields (any variation) and fields requesting
 *   [EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING] ("incognito") get no
 *   suggestions that use personal data, and nothing typed is ever recorded;
 * - password fields additionally get no suggestions or hints at all, so
 *   their text is never analysed.
 *
 * Correctness rules: e-mail/URL/username/number fields get no autocorrect
 * and no automatic capitals; number/phone/date fields open the symbols
 * layer.
 *
 * Language assistance (translation, word hints, grammar suggestions) is
 * read-only: it never changes the text unless the user taps a chip, and
 * nothing is stored. So it is available in every text field except
 * passwords and e-mail addresses -- including search and address bars
 * (`textUri`, `textFilter`, web inputs) and fields that declare no input
 * type. Apps describe "the same kind of field" very differently: a browser
 * address bar is `textUri` + actionGo, a search box may be `textFilter`, a
 * web input `textWebEditText`, a custom view TYPE_NULL. Earlier this flag
 * required "prose" (no URI/filter variation, text class only), which is why
 * Chrome and YouTube search fields got word suggestions but no translation.
 * Text that is clearly a web address or e-mail is filtered by content in
 * [TextContext.containsWebAddressOrEmail].
 */
data class InputFieldPolicy(
    val showSuggestions: Boolean,
    val allowLearning: Boolean,
    val allowAutoCorrect: Boolean,
    val allowLearningHints: Boolean,
    val autoCapitalize: Boolean,
    val capitalizeEveryWord: Boolean,
    val capitalizeAll: Boolean,
    val startInSymbols: Boolean,
    val multiLine: Boolean,
    val enterAction: EnterKeyAction,
    /** The raw IME action to perform on Enter (EditorInfo.IME_ACTION_*), or null to insert a newline. */
    val editorAction: Int?
) {
    companion object {
        /** Used before any field is bound (and in tests): a normal multi-line text field. */
        val DEFAULT = from(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, EditorInfo.IME_ACTION_UNSPECIFIED)

        fun from(info: EditorInfo?): InputFieldPolicy =
            if (info == null) DEFAULT else from(info.inputType, info.imeOptions)

        fun from(inputType: Int, imeOptions: Int): InputFieldPolicy {
            val inputClass = inputType and InputType.TYPE_MASK_CLASS
            val variation = inputType and InputType.TYPE_MASK_VARIATION
            val flags = inputType and InputType.TYPE_MASK_FLAGS

            val isText = inputClass == InputType.TYPE_CLASS_TEXT
            val isPassword = (isText && (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
                (inputClass == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
            val isEmailAddress = isText && (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS)
            // Address/search bars: not rewritten, capitalised or learned from, but still assisted.
            val isAddressLike = isEmailAddress || isText && (variation == InputType.TYPE_TEXT_VARIATION_URI ||
                variation == InputType.TYPE_TEXT_VARIATION_FILTER)
            // TYPE_NULL: the app declared no content type (custom views); treated as plain text for read-only help.
            val noDeclaredType = inputType == InputType.TYPE_NULL
            val incognito = imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0
            val noSuggestionsFlag = flags and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0
            val numeric = inputClass == InputType.TYPE_CLASS_NUMBER || inputClass == InputType.TYPE_CLASS_PHONE ||
                inputClass == InputType.TYPE_CLASS_DATETIME

            val hasCapFlags = flags and (InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_CAP_WORDS or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS) != 0

            val action = imeOptions and EditorInfo.IME_MASK_ACTION
            val multiLine = flags and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0
            val noEnterAction = imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
            val editorAction = if (!multiLine && !noEnterAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) action else null
            val enter = when (editorAction) {
                EditorInfo.IME_ACTION_SEND -> EnterKeyAction.SEND
                EditorInfo.IME_ACTION_SEARCH -> EnterKeyAction.SEARCH
                EditorInfo.IME_ACTION_GO -> EnterKeyAction.GO
                EditorInfo.IME_ACTION_NEXT -> EnterKeyAction.NEXT
                EditorInfo.IME_ACTION_DONE -> EnterKeyAction.DONE
                EditorInfo.IME_ACTION_PREVIOUS -> EnterKeyAction.PREVIOUS
                else -> EnterKeyAction.NEWLINE
            }

            val prose = isText && !isPassword && !isAddressLike
            return InputFieldPolicy(
                showSuggestions = !isPassword && !numeric,
                allowLearning = !isPassword && !incognito && !numeric && !isAddressLike,
                allowAutoCorrect = prose && !noSuggestionsFlag && variation != InputType.TYPE_TEXT_VARIATION_PERSON_NAME,
                allowLearningHints = (isText || noDeclaredType) && !isPassword && !isEmailAddress,
                // Respect explicit capitalisation flags; plain prose fields without flags still get sentence caps.
                autoCapitalize = prose && (hasCapFlags || variation == InputType.TYPE_TEXT_VARIATION_NORMAL || variation == InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE ||
                    variation == InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE || variation == InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT),
                capitalizeEveryWord = prose && (flags and InputType.TYPE_TEXT_FLAG_CAP_WORDS != 0 || variation == InputType.TYPE_TEXT_VARIATION_PERSON_NAME),
                capitalizeAll = prose && flags and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS != 0,
                startInSymbols = numeric,
                multiLine = multiLine,
                enterAction = enter,
                editorAction = editorAction
            )
        }
    }
}
