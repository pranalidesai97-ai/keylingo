package com.keylingo.app.keyboard

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.keylingo.app.data.autocomplete.EnglishWordSeeder
import com.keylingo.app.data.dictionary.DictionarySeeder
import com.keylingo.app.data.dictionary.PhraseChunkSeeder
import com.keylingo.app.data.settings.SettingsDataStore
import com.keylingo.app.domain.repository.ContextPredictionProvider
import com.keylingo.app.domain.repository.LanguagePack
import com.keylingo.app.domain.repository.LearnedVocabularyProvider
import com.keylingo.app.domain.usecase.GenerateAutocompleteSuggestionsUseCase
import com.keylingo.app.domain.usecase.GenerateHintsUseCase
import com.keylingo.app.keyboard.ui.KeyboardScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The actual KeyLingo keyboard. Renders a Compose UI (hint bar + keys) and
 * forwards every keystroke straight to the focused app's InputConnection --
 * the typed message is never altered. The same text is separately read
 * back (read-only) to compute learning hints.
 *
 * ### Why this class implements four owner interfaces
 *
 * `InputMethodService` shows its UI inside an internal `Dialog` (the
 * "soft input window"). That Dialog has its own real `Window` / `DecorView`,
 * completely separate from any Activity. Jetpack Compose creates one
 * `Recomposer` per *window* the first time a `ComposeView` attaches, and it
 * resolves that window's `LifecycleOwner` by walking **upward** from the
 * window's DecorView (`view.parent`, `view.parent.parent`, ...), stopping at
 * the first tagged view it finds via `getTag()`.
 *
 * Tagging only the `ComposeView` we return from [onCreateInputView] is not
 * enough: our `ComposeView` sits several levels *below* the Dialog's
 * DecorView (Android wraps it inside internal decor containers, one of
 * which is the `android:id/parentPanel` LinearLayout you'll see in the
 * crash trace). An upward-only search that starts at the DecorView can
 * never see a tag placed on a descendant further down the tree -- hence
 * `IllegalStateException: ViewTreeLifecycleOwner not found from
 * android.widget.LinearLayout{... android:id/parentPanel}`.
 *
 * The fix is to tag the Dialog's **actual DecorView** with our
 * `LifecycleOwner` / `SavedStateRegistryOwner` / `ViewModelStoreOwner` (and,
 * for completeness, `OnBackPressedDispatcherOwner`), in addition to tagging
 * the `ComposeView` itself. This is exactly what `ComponentActivity` does
 * for you automatically for a normal Activity window; a Service-hosted
 * Dialog window has nobody doing that, so we do it ourselves.
 */
@AndroidEntryPoint
class KeyLingoInputMethodService :
    InputMethodService(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner,
    OnBackPressedDispatcherOwner,
    KeyboardTextConnection {

    @Inject lateinit var generateHintsUseCase: GenerateHintsUseCase
    @Inject lateinit var generateAutocompleteSuggestionsUseCase: GenerateAutocompleteSuggestionsUseCase
    @Inject lateinit var learnedVocabularyProvider: LearnedVocabularyProvider
    @Inject lateinit var contextPredictionProvider: ContextPredictionProvider
    @Inject lateinit var settingsDataStore: SettingsDataStore
    @Inject lateinit var languagePack: LanguagePack
    @Inject lateinit var dictionarySeeder: DictionarySeeder
    @Inject lateinit var phraseChunkSeeder: PhraseChunkSeeder
    @Inject lateinit var englishWordSeeder: EnglishWordSeeder

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private var serviceScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var keyboardViewModel: KeyboardViewModel

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()

    // Not wired to the system back gesture (the platform already dismisses
    // the IME window on back without our involvement); this exists purely
    // so that a composable using BackHandler{} would not crash for lack of
    // an OnBackPressedDispatcherOwner. Safe to leave unwired.
    override val onBackPressedDispatcher: OnBackPressedDispatcher = OnBackPressedDispatcher()

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        keyboardViewModel = KeyboardViewModel(
            generateHintsUseCase = generateHintsUseCase,
            generateAutocompleteSuggestionsUseCase = generateAutocompleteSuggestionsUseCase,
            learnedVocabularyProvider = learnedVocabularyProvider,
            contextPredictionProvider = contextPredictionProvider,
            settingsDataStore = settingsDataStore,
            languagePack = languagePack,
            textConnection = this,
            scope = serviceScope
        )

        // Offline dictionary + phrase-chunk + autocomplete-word-list seeding:
        // all idempotent, so this is cheap on every subsequent call once the
        // data is already in Room. englishWordSeeder is intentionally not
        // passed a LanguagePack -- autocomplete is English-only regardless
        // of which language is being taught, unlike the other two seeders.
        serviceScope.launch {
            dictionarySeeder.seedIfNeeded(languagePack)
            phraseChunkSeeder.seedIfNeeded(languagePack)
            englishWordSeeder.seedIfNeeded()
        }
    }

    override fun onCreateInputView(): View {
        // THE FIX: propagate our owners onto the soft-input Dialog's real
        // DecorView. This is the view Compose's window-recomposer lookup is
        // actually anchored to -- not the View we return below.
        attachTreeOwnersToDialogDecorView()

        val composeView = ComposeView(this).apply {
            // Defense in depth: also tag the ComposeView itself so the
            // owners are found immediately regardless of exactly how deep
            // in the decor hierarchy the framework parents this view.
            setViewTreeLifecycleOwner(this@KeyLingoInputMethodService)
            setViewTreeSavedStateRegistryOwner(this@KeyLingoInputMethodService)
            setViewTreeViewModelStoreOwner(this@KeyLingoInputMethodService)
            setViewTreeOnBackPressedDispatcherOwner(this@KeyLingoInputMethodService)

            // Do NOT rely on the default ViewCompositionStrategy
            // (DisposeOnViewTreeLifecycleDestroyed). That strategy assumes
            // an Activity/Fragment-shaped host lifecycle. Our Lifecycle is
            // driven manually by this Service, so dispose the composition
            // when the view detaches from the window instead.
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)

            setContent {
                KeyboardScreen(viewModel = keyboardViewModel)
            }
        }

        // The window may attach this view before onStartInputView() /
        // onWindowShown() run, so make sure we're already at least STARTED
        // before Compose gets a chance to observe our Lifecycle.
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)

        return composeView
    }

    /**
     * Tags the DecorView of the IME's internal soft-input Dialog window
     * with this Service as the Lifecycle / SavedStateRegistry / ViewModelStore
     * / OnBackPressedDispatcher owner. Must run before the ComposeView we
     * return from [onCreateInputView] gets attached to that window.
     */
    private fun attachTreeOwnersToDialogDecorView() {
        val decorView = window?.window?.decorView ?: return
        decorView.setViewTreeLifecycleOwner(this)
        decorView.setViewTreeSavedStateRegistryOwner(this)
        decorView.setViewTreeViewModelStoreOwner(this)
        decorView.setViewTreeOnBackPressedDispatcherOwner(this)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        keyboardViewModel.onInputFieldContentChanged()
    }

    override fun onWindowShown() {
        super.onWindowShown()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ---- KeyboardTextConnection: the only place that touches InputConnection ----

    override fun commitText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    override fun deleteOneCharacterBackward() {
        val connection = currentInputConnection ?: return
        val selectedText = connection.getSelectedText(0)
        if (!selectedText.isNullOrEmpty()) {
            connection.commitText("", 1)
        } else {
            connection.deleteSurroundingText(1, 0)
        }
    }

    override fun deleteCharactersBackward(charCount: Int) {
        if (charCount <= 0) return
        currentInputConnection?.deleteSurroundingText(charCount, 0)
    }

    override fun performEnterAction() {
        val connection = currentInputConnection ?: return
        val editorInfo = currentInputEditorInfo
        val action = editorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)

        val isMultiline = editorInfo?.inputType?.and(EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE) != 0
        if (!isMultiline && action != null &&
            action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED
        ) {
            connection.performEditorAction(action)
        } else {
            connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }

    override fun currentFieldText(): String {
        val connection = currentInputConnection ?: return ""
        val extracted = connection.getExtractedText(ExtractedTextRequest(), 0)
        if (extracted?.text != null) return extracted.text.toString()

        // Fallback for editors that don't support extracted text requests.
        val before = connection.getTextBeforeCursor(1000, 0) ?: ""
        val after = connection.getTextAfterCursor(1000, 0) ?: ""
        return "$before$after"
    }
}
