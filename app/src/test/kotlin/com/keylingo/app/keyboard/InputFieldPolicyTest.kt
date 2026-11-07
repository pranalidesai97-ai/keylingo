package com.keylingo.app.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InputFieldPolicyTest {
    private fun policy(type: Int, options: Int = EditorInfo.IME_ACTION_UNSPECIFIED) = InputFieldPolicy.from(type, options)

    @Test fun passwordsAreNeverAnalysedOrLearned() {
        for (variation in listOf(InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) {
            val p = policy(InputType.TYPE_CLASS_TEXT or variation)
            assertFalse(p.showSuggestions); assertFalse(p.allowLearning); assertFalse(p.allowAutoCorrect); assertFalse(p.allowLearningHints)
        }
        val pin = policy(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        assertFalse(pin.showSuggestions); assertFalse(pin.allowLearning)
    }

    @Test fun incognitoDisablesLearningOnly() {
        val p = policy(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
        assertFalse(p.allowLearning)
        assertTrue(p.showSuggestions)
    }

    @Test fun addressesAreNotCorrectedOrCapitalised() {
        val email = policy(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        assertFalse(email.allowAutoCorrect); assertFalse(email.autoCapitalize); assertFalse(email.allowLearning)
        val url = policy(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        assertFalse(url.allowAutoCorrect)
    }

    @Test fun messagesGetFullAssistance() {
        val p = policy(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, EditorInfo.IME_ACTION_SEND)
        assertTrue(p.showSuggestions && p.allowLearning && p.allowAutoCorrect && p.autoCapitalize && p.allowLearningHints)
        assertEquals(EnterKeyAction.SEND, p.enterAction)
        assertEquals(EditorInfo.IME_ACTION_SEND, p.editorAction)
    }

    @Test fun multiLineAndNoEnterActionInsertNewlines() {
        assertNull(policy(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE, EditorInfo.IME_ACTION_SEND).editorAction)
        assertNull(policy(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_ENTER_ACTION).editorAction)
    }

    @Test fun nameFieldsCapitaliseEveryWordWithoutAutocorrect() {
        val p = policy(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PERSON_NAME)
        assertTrue(p.capitalizeEveryWord); assertFalse(p.allowAutoCorrect)
    }

    @Test fun numericFieldsStartInSymbols() {
        assertTrue(policy(InputType.TYPE_CLASS_PHONE).startInSymbols)
        assertTrue(policy(InputType.TYPE_CLASS_NUMBER).startInSymbols)
        assertFalse(policy(InputType.TYPE_CLASS_TEXT).startInSymbols)
    }

    // ---- Cross-app field configurations (regression: no translation in Chrome / YouTube search fields)

    private class Field(val name: String, val type: Int, val options: Int)

    private val T = InputType.TYPE_CLASS_TEXT
    private val searchOptions = EditorInfo.IME_ACTION_SEARCH
    private val goOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN

    /** Ordinary text fields where suggestions, translation and grammar help must all be available. */
    private val assistedFields = listOf(
        Field("chat message (WhatsApp-style)", T or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE, EditorInfo.IME_ACTION_SEND),
        Field("browser address/search bar (textUri + actionGo)", T or InputType.TYPE_TEXT_VARIATION_URI, goOptions),
        Field("browser address bar, incognito", T or InputType.TYPE_TEXT_VARIATION_URI, goOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING),
        Field("web page search input", T or InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT, searchOptions),
        Field("web input with spellcheck=false", T or InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, searchOptions),
        Field("app SearchView with its own suggestions", T or InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, searchOptions),
        Field("filter-style search box", T or InputType.TYPE_TEXT_VARIATION_FILTER, searchOptions),
        Field("plain single-line text", T, EditorInfo.IME_ACTION_DONE),
        Field("long message", T or InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE or InputType.TYPE_TEXT_FLAG_MULTI_LINE, EditorInfo.IME_ACTION_UNSPECIFIED),
        Field("email subject", T or InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT, EditorInfo.IME_ACTION_NEXT),
        Field("person name", T or InputType.TYPE_TEXT_VARIATION_PERSON_NAME, EditorInfo.IME_ACTION_NEXT),
        Field("custom view without an input type (TYPE_NULL)", InputType.TYPE_NULL, EditorInfo.IME_ACTION_UNSPECIFIED),
    )

    /** Fields where nothing typed may be analysed for language help. */
    private val restrictedFields = listOf(
        Field("password", T or InputType.TYPE_TEXT_VARIATION_PASSWORD, EditorInfo.IME_ACTION_DONE),
        Field("visible password", T or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, EditorInfo.IME_ACTION_DONE),
        Field("web password", T or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, EditorInfo.IME_ACTION_GO),
        Field("numeric PIN", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD, EditorInfo.IME_ACTION_DONE),
        Field("email address", T or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, EditorInfo.IME_ACTION_NEXT),
        Field("web email address", T or InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS, EditorInfo.IME_ACTION_NEXT),
        Field("number", InputType.TYPE_CLASS_NUMBER, EditorInfo.IME_ACTION_DONE),
        Field("phone", InputType.TYPE_CLASS_PHONE, EditorInfo.IME_ACTION_DONE),
        Field("date/time", InputType.TYPE_CLASS_DATETIME, EditorInfo.IME_ACTION_DONE),
    )

    @Test fun languageAssistanceIsAvailableInOrdinaryTextFieldsOfEveryApp() {
        val missing = assistedFields.filterNot { policy(it.type, it.options).allowLearningHints }.map { it.name }
        assertEquals("no translation/grammar assistance in: $missing", emptyList<String>(), missing)
        val noWords = assistedFields.filterNot { policy(it.type, it.options).showSuggestions }.map { it.name }
        assertEquals("no word suggestions in: $noWords", emptyList<String>(), noWords)
    }

    @Test fun restrictedFieldsGetNoLanguageAssistance() {
        for (f in restrictedFields) {
            val p = policy(f.type, f.options)
            assertFalse("assistance in ${f.name}", p.allowLearningHints)
            assertFalse("learning in ${f.name}", p.allowLearning)
            assertFalse("autocorrect in ${f.name}", p.allowAutoCorrect)
        }
        for (f in restrictedFields.take(4)) assertFalse("suggestions in ${f.name}", policy(f.type, f.options).showSuggestions)
    }

    @Test fun searchAndAddressFieldsKeepTheirActionAndAreNotAutocorrected() {
        val omnibox = policy(T or InputType.TYPE_TEXT_VARIATION_URI, goOptions)
        assertEquals(EnterKeyAction.GO, omnibox.enterAction)
        assertEquals(EditorInfo.IME_ACTION_GO, omnibox.editorAction)
        assertFalse(omnibox.allowAutoCorrect)  // never rewrite what is typed into an address bar
        assertFalse(omnibox.autoCapitalize)
        assertFalse(omnibox.allowLearning)     // unchanged: search/address text is not learned
        val filter = policy(T or InputType.TYPE_TEXT_VARIATION_FILTER, searchOptions)
        assertEquals(EnterKeyAction.SEARCH, filter.enterAction)
        assertEquals(EditorInfo.IME_ACTION_SEARCH, filter.editorAction)
        assertFalse(filter.allowAutoCorrect)
        val webSearch = policy(T or InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT, searchOptions)
        assertEquals(EditorInfo.IME_ACTION_SEARCH, webSearch.editorAction)
        val spellcheckOff = policy(T or InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, searchOptions)
        assertFalse(spellcheckOff.allowAutoCorrect)
    }

    @Test fun learningStaysOffWhereItWasOff() {
        // The fix widens read-only assistance only; what may be stored is unchanged.
        assertFalse(policy(T or InputType.TYPE_TEXT_VARIATION_URI, goOptions).allowLearning)
        assertFalse(policy(T or InputType.TYPE_TEXT_VARIATION_FILTER, searchOptions).allowLearning)
        assertFalse(policy(T, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING).allowLearning)
    }
}
