package com.keylingo.app.keyboard.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.text.InputType
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.keylingo.app.data.language.SpanishLanguagePack
import com.keylingo.app.data.settings.HintSizeOption
import com.keylingo.app.data.settings.KeyLingoSettings
import com.keylingo.app.domain.usecase.GenerateAutocompleteSuggestionsUseCase
import com.keylingo.app.domain.usecase.GenerateHintsUseCase
import com.keylingo.app.keyboard.InputFieldPolicy
import com.keylingo.app.keyboard.KeyAction
import com.keylingo.app.keyboard.KeyboardViewModel
import com.keylingo.app.testing.FakeContextPrediction
import com.keylingo.app.testing.FakeEditor
import com.keylingo.app.testing.FakeLearnedVocabulary
import com.keylingo.app.testing.realAutocompleteProvider
import com.keylingo.app.testing.realHintProvider
import com.keylingo.app.testing.realLanguageDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Height of the suggestion strip on the real KeyboardScreen:
 * - fields that can show language help reserve the word row (40 dp) and the learning row (46 dp);
 * - other fields (e-mail, password, numbers) or learning + grammar turned off get only the word row;
 * - within a field the height never changes while typing, whatever chips appear.
 * Previously the strip was always 92 dp, so fields without language help kept an empty row.
 * Also renders PNGs to build/keyboard-screenshots/ for review (JVM renders, not device screenshots).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class StripLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val outDir = File(listOf(File("build"), File("app/build")).first { it.isDirectory }, "keyboard-screenshots").apply { mkdirs() }

    private val keys = 260.dp
    private val chat = InputFieldPolicy.from(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
        InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE, EditorInfo.IME_ACTION_SEND)
    private val omnibox = InputFieldPolicy.from(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI, EditorInfo.IME_ACTION_GO)
    private val email = InputFieldPolicy.from(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, EditorInfo.IME_ACTION_NEXT)
    private val password = InputFieldPolicy.from(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, EditorInfo.IME_ACTION_DONE)

    private class Kb(val vm: KeyboardViewModel, val editor: FakeEditor)

    private fun show(policy: InputFieldPolicy, settings: KeyLingoSettings = KeyLingoSettings()): Kb {
        val learned = FakeLearnedVocabulary(); val ctx = FakeContextPrediction()
        val editor = FakeEditor("")
        val vm = KeyboardViewModel(
            GenerateHintsUseCase(realHintProvider()), GenerateAutocompleteSuggestionsUseCase(realAutocompleteProvider(), learned, ctx),
            learned, ctx, realLanguageDetector(), MutableStateFlow(settings), SpanishLanguagePack(), editor,
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined) // off the paused main looper; debounces run in real time
        )
        vm.onStartInput(policy, restarting = false)
        compose.setContent { Box(Modifier.width(411.dp).testTag("kb")) { KeyboardScreen(vm) } }
        settle()
        return Kb(vm, editor)
    }

    private fun settle() { compose.waitForIdle(); Thread.sleep(350); compose.waitForIdle(); Thread.sleep(150); compose.waitForIdle() }

    private fun Kb.type(text: String) {
        for (c in text) vm.onKeyAction(if (c == ' ') KeyAction.Space else KeyAction.Character(c.lowercase(), c.uppercase()))
        settle()
    }

    private fun height(): Dp = compose.onNodeWithTag("kb").getBoundsInRoot().let { it.bottom - it.top }

    private fun snapshot(name: String) {
        val root = compose.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        File(outDir, "strip-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun assertDp(expected: Dp, actual: Dp) = assertEquals(expected.value, actual.value, 0.6f)

    @Test fun assistedFieldReservesBothRowsAndNeverMovesWhileTyping() {
        val kb = show(chat)
        val empty = height()
        assertDp(WORD_ROW_HEIGHT + LEARNING_ROW_HEIGHT + keys, empty)
        kb.type("I am drinking water")
        assertNotNull(kb.vm.uiState.value.assistance.sentence)
        snapshot("chat-translation")
        assertDp(empty, height())
    }

    @Test fun browserAddressBarIsAssistedWithTheSameLayout() {
        val kb = show(omnibox)
        kb.type("She is working today")
        assertEquals("Ella está trabajando hoy", kb.vm.uiState.value.assistance.sentence?.translatedText)
        snapshot("address-bar-translation")
        assertDp(WORD_ROW_HEIGHT + LEARNING_ROW_HEIGHT + keys, height())
    }

    @Test fun fieldsWithoutLanguageHelpGetOnlyTheWordRow() {
        val kb = show(email)
        kb.type("maria")
        snapshot("email-words-only")
        assertDp(WORD_ROW_HEIGHT + keys, height())
    }

    @Test fun passwordFieldsGetOnlyTheWordRow() {
        show(password)
        assertDp(WORD_ROW_HEIGHT + keys, height())
    }

    @Test fun learningRowAndGrammarOffGivesOneRow() {
        val kb = show(chat, KeyLingoSettings(learningEnabled = false, grammarEnabled = false))
        kb.type("hello the")
        snapshot("chat-learning-off")
        assertDp(WORD_ROW_HEIGHT + keys, height())
    }

    @Test fun largeHintSizeStillFitsTheRows() {
        val kb = show(chat, KeyLingoSettings(hintSize = HintSizeOption.LARGE))
        kb.type("Yesterday I go to the market. ")
        snapshot("chat-large-grammar")
        assertDp(WORD_ROW_HEIGHT + LEARNING_ROW_HEIGHT + keys, height())
    }
}
