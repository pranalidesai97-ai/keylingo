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
}
