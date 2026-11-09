package com.keylingo.app.keyboard

import android.os.Looper
import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.test.core.app.ApplicationProvider
import com.keylingo.app.data.settings.SettingsDataStore
import com.keylingo.app.data.settings.TypingLanguageOption
import com.keylingo.app.domain.model.StarterPhrases
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

/**
 * The real keyboard service (Hilt injection, real DataStore settings, real
 * lifecycle callbacks) on Robolectric, not just KeyboardViewModel with hand-made
 * inputs. Covers the typing-language flow that failed on a phone: fresh
 * launches, settings that load before or after the field opens, live
 * switching, Learning vs Typing language, reopening and recreating the keyboard.
 * Empty field: English phrases on top and Spanish phrases below, whatever the setting.
 * Robolectric has no app on the other end of the InputConnection, so the field
 * is empty; typing and deleting are covered in KeyboardViewModelTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeyLingoInputMethodServiceTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val store = SettingsDataStore(context)
    private val chatField = EditorInfo().apply {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE
        imeOptions = EditorInfo.IME_ACTION_SEND
    }

    @After fun resetSettings() = runBlocking {
        store.setTypingLanguage(TypingLanguageOption.AUTO); store.setTargetLanguage("es")
    }

    /** Empty field: English phrases on top, Spanish phrases in the learning row -- in every typing-language mode. */
    private fun bothRows(service: KeyLingoInputMethodService): Boolean = vm(service).uiState.value.let {
        it.starterPhrases == StarterPhrases.ENGLISH && it.learningStarterPhrases == StarterPhrases.SPANISH
    }

    private fun vm(service: KeyLingoInputMethodService): KeyboardViewModel =
        KeyLingoInputMethodService::class.java.getDeclaredField("keyboardViewModel").apply { isAccessible = true }.get(service) as KeyboardViewModel

    /** Runs the main looper (DataStore delivers on it) until [condition] holds, at most ~3 s. */
    private fun waitUntil(condition: () -> Boolean): Boolean {
        repeat(60) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return true
            Thread.sleep(50)
        }
        return condition()
    }

    private fun start(): Pair<ServiceController<KeyLingoInputMethodService>, KeyLingoInputMethodService> {
        val controller = Robolectric.buildService(KeyLingoInputMethodService::class.java).create()
        return controller to controller.get()
    }

    @Test fun freshLaunchInSpanishModeShowsSpanishPhrasesWhicheverLoadsFirst() {
        runBlocking { store.setTypingLanguage(TypingLanguageOption.SPANISH) }
        // (a) The field opens before the saved settings have been read.
        val (c1, s1) = start()
        s1.onStartInputView(chatField, false)
        assertTrue("Spanish mode after settings load", waitUntil { vm(s1).uiState.value.typingLanguage == "es" }) // space bar reads "Español"
        assertTrue("English top row + Spanish bottom row", bothRows(s1))
        assertTrue(vm(s1).uiState.value.suggestions.isEmpty())
        c1.destroy()
        // (b) Settings are already loaded when the field opens.
        val (c2, s2) = start()
        waitUntil { vm(s2).uiState.value.settings.typingLanguage == TypingLanguageOption.SPANISH }
        s2.onStartInputView(chatField, false)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(bothRows(s2))
        assertEquals("es", vm(s2).uiState.value.typingLanguage)
        c2.destroy()
    }

    @Test fun changingTheTypingLanguageUpdatesThePhrasesAndSpaceBarImmediately() {
        runBlocking { store.setTypingLanguage(TypingLanguageOption.ENGLISH) }
        val (controller, service) = start()
        service.onStartInputView(chatField, false)
        assertTrue(waitUntil { bothRows(service) })
        assertEquals("en", vm(service).uiState.value.typingLanguage)

        runBlocking { store.setTypingLanguage(TypingLanguageOption.SPANISH) }
        assertTrue("space bar switched to Spanish", waitUntil { vm(service).uiState.value.typingLanguage == "es" })
        assertTrue("rows unchanged: no contamination", bothRows(service))

        runBlocking { store.setTypingLanguage(TypingLanguageOption.ENGLISH) }
        assertTrue("switched back to English", waitUntil { vm(service).uiState.value.typingLanguage == "en" })
        assertTrue(bothRows(service))
        controller.destroy()
    }

    @Test fun translateIntoSpanishDoesNotChangeTheTypingLanguage() {
        runBlocking { store.setTypingLanguage(TypingLanguageOption.ENGLISH); store.setTargetLanguage("es") }
        val (controller, service) = start()
        service.onStartInputView(chatField, false)
        assertTrue(waitUntil { vm(service).uiState.value.settings.typingLanguage == TypingLanguageOption.ENGLISH })
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("Learning/translation language must not switch typing to Spanish", "en", vm(service).uiState.value.typingLanguage)
        assertTrue(bothRows(service))
        controller.destroy()
    }

    @Test fun reopeningAndRecreatingTheKeyboardKeepsTheRightPhrases() {
        runBlocking { store.setTypingLanguage(TypingLanguageOption.SPANISH) }
        repeat(25) { cycle ->
            val (controller, service) = start()
            service.onStartInputView(chatField, false)
            assertTrue("cycle $cycle", waitUntil { vm(service).uiState.value.typingLanguage == "es" && bothRows(service) })
            // Hide and show again (same field), then switch to another field.
            service.onFinishInputView(false)
            service.onStartInputView(chatField, true)
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue("cycle $cycle after reopen", bothRows(service))
            service.onFinishInput()
            service.onStartInputView(chatField, false)
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue("cycle $cycle new field", bothRows(service))
            controller.destroy()
        }
    }

    @Test fun passwordFieldsShowNoPhrasesInTheRealService() {
        runBlocking { store.setTypingLanguage(TypingLanguageOption.SPANISH) }
        val (controller, service) = start()
        waitUntil { vm(service).uiState.value.settings.typingLanguage == TypingLanguageOption.SPANISH }
        service.onStartInputView(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }, false)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(vm(service).uiState.value.starterPhrases.isEmpty())
        assertTrue(vm(service).uiState.value.learningStarterPhrases.isEmpty())
        assertTrue(vm(service).uiState.value.suggestions.isEmpty())
        controller.destroy()
    }
}
