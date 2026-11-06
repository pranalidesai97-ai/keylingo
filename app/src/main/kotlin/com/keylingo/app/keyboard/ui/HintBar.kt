package com.keylingo.app.keyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keylingo.app.data.settings.HintSizeOption
import com.keylingo.app.domain.model.AutocompleteSuggestion
import com.keylingo.app.domain.model.HintWord

/**
 * Fixed height of a single suggestion row (autocomplete OR hints), sized
 * to comfortably fit a chip at [HintSizeOption.LARGE] (17sp text + 6dp
 * vertical padding on the chip + 4dp vertical padding on the row) with a
 * safety margin. This value is intentionally NOT derived from content or
 * from [HintSizeOption] -- see [HintBar]'s KDoc for why.
 */
private val SUGGESTION_ROW_HEIGHT = 46.dp

/**
 * Renders the two suggestion rows above the keyboard:
 * - Autocomplete (top row): tappable English word completions for the
 *   word currently being typed. Tapping one replaces that word via
 *   [onAutocompleteSelected].
 * - Learning hints (bottom row): translations of the typed text. Purely
 *   a visual aid -- never written back into the message, not tappable.
 *
 * ### Fixed-height layout, by design
 *
 * This composable's total height is a **constant** `SUGGESTION_ROW_HEIGHT * 2`,
 * regardless of whether 0, 1, or 2 rows have content. Both row slots are
 * always present in the composition -- an empty row is still measured and
 * laid out, just with no chips inside it -- rather than being
 * conditionally included/excluded or wrapped in a height-animating
 * `AnimatedVisibility`. That's a deliberate reversal of the previous
 * implementation, which wrapped the whole bar in `AnimatedVisibility` and
 * only rendered a row `if (list.isNotEmpty())`: both of those made this
 * composable's *measured height* vary between 0 (empty), one row's height
 * (only one list populated), and two rows' height (both populated) --
 * which is exactly why the keyboard grid below it in `KeyboardScreen`
 * visibly shifted position depending on suggestion state. The keyboard's
 * key geometry must never move; only the *content* of this fixed-size
 * strip may change.
 *
 * A consequence: the enter/exit fade+slide animation this bar previously
 * had is gone, since animating a row's height is exactly the thing this
 * fix removes. Content still simply appears/disappears; if a purely
 * cosmetic content-level fade is wanted later, it would need to animate
 * *opacity only*, inside the already-fixed-size row, never the row's own
 * size.
 */
@Composable
fun HintBar(
    autocompleteSuggestions: List<AutocompleteSuggestion>,
    hints: List<HintWord>,
    hintSize: HintSizeOption,
    onAutocompleteSelected: (AutocompleteSuggestion) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(SUGGESTION_ROW_HEIGHT * 2)
            .background(MaterialTheme.colorScheme.background)
    ) {
        SuggestionRow {
            autocompleteSuggestions.forEach { suggestion ->
                AutocompleteChip(
                    suggestion = suggestion,
                    hintSize = hintSize,
                    onClick = onAutocompleteSelected
                )
            }
        }
        SuggestionRow {
            hints.forEach { hint ->
                HintChip(hint = hint, hintSize = hintSize)
            }
        }
    }
}

/**
 * One fixed-height row slot. Always occupies exactly [SUGGESTION_ROW_HEIGHT]
 * whether [content] adds zero, one, or many chips -- horizontal scrolling
 * handles overflow width, the fixed height is never affected by it.
 */
@Composable
private fun SuggestionRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SUGGESTION_ROW_HEIGHT)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
private fun AutocompleteChip(
    suggestion: AutocompleteSuggestion,
    hintSize: HintSizeOption,
    onClick: (AutocompleteSuggestion) -> Unit
) {
    val fontSize = fontSizeFor(hintSize)
    Row(
        modifier = Modifier
            .fillMaxHeight()
            .clickable { onClick(suggestion) }
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = suggestion.word,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = fontSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HintChip(hint: HintWord, hintSize: HintSizeOption) {
    val fontSize = fontSizeFor(hintSize)
    Row(
        modifier = Modifier
            .fillMaxHeight()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = hint.translatedWord,
            color = MaterialTheme.colorScheme.primary,
            fontSize = fontSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun fontSizeFor(hintSize: HintSizeOption) = when (hintSize) {
    HintSizeOption.SMALL -> 12.sp
    HintSizeOption.MEDIUM -> 14.sp
    HintSizeOption.LARGE -> 17.sp
}
