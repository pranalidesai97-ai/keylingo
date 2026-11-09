package com.keylingo.app.keyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.keylingo.app.data.settings.DarkModeOption
import com.keylingo.app.keyboard.KeyAction
import com.keylingo.app.keyboard.KeyDefinition
import com.keylingo.app.keyboard.KeyboardLayoutProvider
import com.keylingo.app.keyboard.KeyboardMode
import com.keylingo.app.keyboard.KeyboardUiState
import com.keylingo.app.keyboard.KeyboardViewModel
import com.keylingo.app.ui.theme.KeyLingoTheme
import com.keylingo.app.ui.theme.KeyboardThemes
import com.keylingo.app.ui.theme.LocalKeyboardColors
import com.keylingo.app.ui.theme.toBrush
import androidx.compose.runtime.CompositionLocalProvider
import kotlin.math.roundToInt

/**
 * Fixed total height of the key area in every mode (letters with/without
 * the number row, symbols, emoji): rows share it by weight, so switching
 * modes never resizes the keyboard.
 */
private val KEY_ROWS_HEIGHT = 260.dp

private data class AccentPopupState(val options: List<String>, val anchor: Rect, val selected: Int)

/**
 * Root composable of the IME window: suggestion/learning strip, key rows,
 * and an overlay for the long-press accent popup (drawn inside this layout
 * rather than in a separate window, which IMEs handle unreliably).
 */
@Composable
fun KeyboardScreen(viewModel: KeyboardViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var popup by remember { mutableStateOf<AccentPopupState?>(null) }
    val popupController = remember {
        AccentPopupController(
            show = { options, anchor -> popup = AccentPopupState(options, anchor, 0) },
            select = { index -> popup = popup?.copy(selected = index) },
            dismiss = { popup = null }
        )
    }

    val feedback = rememberKeyFeedback(uiState.settings.hapticsEnabled, uiState.settings.soundEnabled)
    val settings = uiState.settings
    val systemDark = isSystemInDarkTheme()
    val dark = KeyboardThemes.useDark(settings.themePreset, settings.darkMode == DarkModeOption.SYSTEM, settings.darkMode == DarkModeOption.DARK, systemDark)
    val palette = remember(settings.themePreset, settings.customAccentArgb, dark) {
        KeyboardThemes.palette(settings.themePreset, settings.customAccentArgb, dark)
    }

    CompositionLocalProvider(LocalKeyboardColors provides palette) { KeyLingoTheme(darkTheme = dark) {
        Surface(color = palette.background) {
            // One gradient behind the strip and the keys, so it reads as a single surface.
            val backgroundBrush = remember(palette) { palette.backgroundGradient.toBrush() }
            Box(modifier = Modifier.fillMaxWidth().background(backgroundBrush)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val onSuggestion = remember(viewModel) { viewModel::onAutocompleteSelected }
                    val onGrammar = remember(viewModel) { viewModel::onGrammarSuggestionApplied }
                    val onPhrase = remember(viewModel) { viewModel::onStarterPhraseSelected }
                    val onKeyAction = remember(viewModel) { viewModel::onKeyAction }
                    HintBar(
                        suggestions = uiState.suggestions,
                        autoCorrection = uiState.autoCorrection,
                        assistance = uiState.assistance,
                        hintSize = uiState.settings.hintSize,
                        onSuggestionSelected = onSuggestion,
                        showLearningRow = uiState.showLearningRow,
                        onGrammarApplied = onGrammar,
                        starterPhrases = uiState.starterPhrases,
                        onStarterPhrase = onPhrase,
                        learningStarterPhrases = uiState.learningStarterPhrases
                    )
                    val rows = remember(uiState.mode, uiState.settings.showNumberRow) {
                        KeyboardLayoutProvider.rowsFor(uiState.mode, uiState.settings.showNumberRow)
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(KEY_ROWS_HEIGHT)
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        if (uiState.mode == KeyboardMode.EMOJI) {
                            EmojiGrid(onEmoji = { onKeyAction(KeyAction.Symbol(it)) }, modifier = Modifier.weight(4f))
                        }
                        rows.forEach { row ->
                            KeyRow(row, uiState, onKeyAction, popupController, feedback, Modifier.weight(1f))
                        }
                    }
                }
                popup?.let { AccentPopupOverlay(it) }
            }
        }
    } }
}

@Composable
private fun KeyRow(
    row: List<KeyDefinition>,
    uiState: KeyboardUiState,
    onKeyAction: (KeyAction) -> Unit,
    popupController: AccentPopupController,
    feedback: KeyFeedback,
    modifier: Modifier
) {
    val spaceLabel = if (uiState.typingLanguage == "es") "Español" else "English"
    Row(modifier = modifier.fillMaxWidth()) {
        row.forEach { keyDef ->
            KeyboardKey(
                action = keyDef.action,
                shiftState = uiState.shiftState,
                onClick = onKeyAction,
                modifier = Modifier.weight(keyDef.weight),
                enterAction = uiState.policy.enterAction,
                spaceLabel = spaceLabel,
                feedback = feedback,
                accentPopup = popupController
            )
        }
    }
}

@Composable
private fun EmojiGrid(onEmoji: (String) -> Unit, modifier: Modifier) {
    LazyVerticalGrid(columns = GridCells.Fixed(8), modifier = modifier.fillMaxWidth()) {
        items(KeyboardLayoutProvider.EMOJI) { emoji ->
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clickable { onEmoji(emoji) },
                contentAlignment = Alignment.Center
            ) { Text(emoji, fontSize = 24.sp, color = LocalKeyboardColors.current.keyText) }
        }
    }
}

@Composable
private fun AccentPopupOverlay(state: AccentPopupState) {
    val colors = LocalKeyboardColors.current
    val density = LocalDensity.current
    val cellWidth = with(density) { state.anchor.width.toDp() }
    val cellHeight = with(density) { (state.anchor.height * 0.9f).toDp() }
    val offset = IntOffset(state.anchor.left.roundToInt(), (state.anchor.top - state.anchor.height * 0.95f).roundToInt().coerceAtLeast(0))
    Row(
        modifier = Modifier
            .offset { offset }
            .zIndex(10f)
            .shadow(6.dp, RoundedCornerShape(10.dp))
            .background(colors.popup, RoundedCornerShape(10.dp))
            .padding(2.dp)
    ) {
        state.options.forEachIndexed { index, option ->
            val selected = index == state.selected
            Box(
                modifier = Modifier
                    .size(width = cellWidth, height = cellHeight)
                    .background(if (selected) colors.accent else colors.popup, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(option, fontSize = 20.sp, color = if (selected) colors.onAccent else colors.keyText)
            }
        }
    }
}
