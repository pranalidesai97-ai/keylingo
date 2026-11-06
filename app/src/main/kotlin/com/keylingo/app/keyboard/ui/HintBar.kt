package com.keylingo.app.keyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keylingo.app.data.settings.HintSizeOption
import com.keylingo.app.domain.model.AutocompleteSuggestion
import com.keylingo.app.domain.model.HintWord
import com.keylingo.app.domain.model.LearningAssistance
import com.keylingo.app.domain.model.SuggestionKind

/**
 * Fixed height of one strip row, sized for a two-line hint chip at
 * [HintSizeOption.LARGE]. Intentionally independent of content so the key
 * grid below never moves (see [HintBar]).
 */
private val SUGGESTION_ROW_HEIGHT = 46.dp

/**
 * The two strips above the keys:
 * - **Suggestions** (top): completions, corrections and next-word
 *   predictions. The pending autocorrection is highlighted so the user
 *   sees what Space will do; the literal typed word is shown in quotes so
 *   it can always be kept.
 * - **Learning** (bottom): the sentence translation (when trustworthy)
 *   followed by word hints with their grammar note. Read-only: hints never
 *   write into the message.
 *
 * Total height is constant (`SUGGESTION_ROW_HEIGHT * 2`) whatever the
 * content -- both rows are always laid out -- so key geometry never shifts.
 */
@Composable
fun HintBar(
    suggestions: List<AutocompleteSuggestion>,
    autoCorrection: AutocompleteSuggestion?,
    assistance: LearningAssistance,
    hintSize: HintSizeOption,
    onSuggestionSelected: (AutocompleteSuggestion) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(SUGGESTION_ROW_HEIGHT * 2)
            .background(MaterialTheme.colorScheme.background)
    ) {
        SuggestionRow {
            suggestions.forEach { suggestion ->
                SuggestionChip(
                    suggestion = suggestion,
                    highlighted = autoCorrection != null && suggestion.word == autoCorrection.word && suggestion.kind != SuggestionKind.LITERAL,
                    hintSize = hintSize,
                    onClick = onSuggestionSelected
                )
            }
        }
        SuggestionRow {
            assistance.sentence?.let { sentence ->
                SentenceChip(text = sentence.translatedText, typingLanguage = assistance.typingLanguage, hintSize = hintSize)
            }
            assistance.hints.forEach { hint -> HintChip(hint = hint, hintSize = hintSize) }
        }
    }
}

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
private fun SuggestionChip(
    suggestion: AutocompleteSuggestion,
    highlighted: Boolean,
    hintSize: HintSizeOption,
    onClick: (AutocompleteSuggestion) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val label = if (suggestion.kind == SuggestionKind.LITERAL) "“${suggestion.word}”" else suggestion.word
    Row(
        modifier = Modifier
            .fillMaxHeight()
            .widthIn(min = 56.dp)
            .background(if (highlighted) colors.primaryContainer else colors.surfaceVariant, RoundedCornerShape(14.dp))
            .clickable { onClick(suggestion) }
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .semantics { contentDescription = if (highlighted) "Autocorrect to ${suggestion.word}" else "Suggestion ${suggestion.word}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            color = if (highlighted) colors.onPrimaryContainer else colors.onSurfaceVariant,
            fontSize = fontSizeFor(hintSize),
            fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SentenceChip(text: String, typingLanguage: String, hintSize: HintSizeOption) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxHeight()
            .background(colors.primary, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .semantics { contentDescription = "Translation: $text" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (typingLanguage == "en") "ES" else "EN",
            color = colors.onPrimary.copy(alpha = 0.75f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 6.dp)
        )
        Text(text = text, color = colors.onPrimary, fontSize = fontSizeFor(hintSize), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun HintChip(hint: HintWord, hintSize: HintSizeOption) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .background(colors.surfaceVariant, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .semantics { contentDescription = "${hint.originalWord}: ${hint.translatedWord}, ${hint.formLabel}" },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = hint.translatedWord,
            color = colors.primary,
            fontSize = fontSizeFor(hintSize),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${hint.originalWord} · ${hint.formLabel}",
            color = colors.onSurfaceVariant,
            fontSize = 10.sp,
            lineHeight = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun fontSizeFor(hintSize: HintSizeOption) = when (hintSize) {
    HintSizeOption.SMALL -> 12.sp
    HintSizeOption.MEDIUM -> 14.sp
    HintSizeOption.LARGE -> 16.sp
}
