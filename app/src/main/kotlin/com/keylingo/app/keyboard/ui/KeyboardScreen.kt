package com.keylingo.app.keyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.keylingo.app.data.settings.DarkModeOption
import com.keylingo.app.keyboard.KeyAction
import com.keylingo.app.keyboard.KeyDefinition
import com.keylingo.app.keyboard.KeyboardLayoutProvider
import com.keylingo.app.keyboard.KeyboardViewModel
import com.keylingo.app.keyboard.ShiftState
import com.keylingo.app.ui.theme.KeyLingoTheme

/**
 * Root composable rendered inside the InputMethodService's ComposeView.
 * Layout, top to bottom: suggestion bar (autocomplete row + learning hint
 * row), then the active keyboard rows (letters / symbols / numbers / emoji
 * depending on mode).
 */
@Composable
fun KeyboardScreen(viewModel: KeyboardViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    KeyLingoTheme(darkTheme = isDarkTheme(uiState.settings.darkMode)) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxWidth()) {
                val onAutocompleteSelected = remember(viewModel) { viewModel::onAutocompleteSelected }
                val onKeyAction = remember(viewModel) { viewModel::onKeyAction }

                HintBar(
                    autocompleteSuggestions = uiState.autocompleteSuggestions,
                    hints = uiState.hints,
                    hintSize = uiState.settings.hintSize,
                    onAutocompleteSelected = onAutocompleteSelected
                )
                val keyRows = remember(uiState.mode, uiState.settings.showNumberRow) {
                    KeyboardLayoutProvider.rowsFor(uiState.mode, uiState.settings.showNumberRow)
                }
                KeyRows(
                    rows = keyRows,
                    shiftState = uiState.shiftState,
                    onKeyAction = onKeyAction
                )
            }
        }
    }
}

/**
 * Fixed total height for the key-rows area, regardless of which mode is
 * active. Sized to the tallest mode (LETTERS with the number row on: 5
 * rows at the previous fixed-52dp-per-row baseline = 260dp).
 *
 * Root cause this fixes: rows used to each get a fixed `height(52.dp)`,
 * so total height was `52.dp * rowCount` -- and row count genuinely
 * differs by mode (LETTERS: 4 or 5 depending on the number-row setting;
 * SYMBOLS_1/SYMBOLS_2/EMOJI: always 4), so the keyboard visibly changed
 * size switching modes. Now every mode's rows share this same fixed
 * total height via `Modifier.weight(1f)` each, so N rows always sum to
 * exactly [KEY_ROWS_HEIGHT] -- a 4-row mode's rows are proportionally
 * taller, a 5-row mode's rows are the original 52dp, but the *total*
 * footprint never changes.
 */
private val KEY_ROWS_HEIGHT = 260.dp

@Composable
private fun KeyRows(
    rows: List<List<KeyDefinition>>,
    shiftState: ShiftState,
    onKeyAction: (KeyAction) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(KEY_ROWS_HEIGHT)
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                row.forEach { keyDef ->
                    KeyboardKey(
                        action = keyDef.action,
                        weight = keyDef.weight,
                        shiftState = shiftState,
                        onClick = onKeyAction,
                        modifier = Modifier.weight(keyDef.weight)
                    )
                }
            }
        }
    }
}


@Composable
private fun isDarkTheme(mode: DarkModeOption): Boolean = when (mode) {
    DarkModeOption.DARK -> true
    DarkModeOption.LIGHT -> false
    DarkModeOption.SYSTEM -> isSystemInDarkTheme()
}
