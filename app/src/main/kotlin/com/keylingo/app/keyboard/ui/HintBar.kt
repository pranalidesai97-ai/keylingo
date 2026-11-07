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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keylingo.app.data.settings.HintSizeOption
import com.keylingo.app.domain.model.AutocompleteSuggestion
import com.keylingo.app.domain.model.GrammarSuggestion
import com.keylingo.app.domain.model.HintWord
import com.keylingo.app.domain.model.LearningAssistance
import com.keylingo.app.domain.model.SuggestionKind
import com.keylingo.app.ui.theme.LocalKeyboardColors
import com.keylingo.app.ui.theme.toBrush

/** Height of the word-suggestion row: one line of text at [HintSizeOption.LARGE]. */
internal val WORD_ROW_HEIGHT = 40.dp

/**
 * Height of the learning row, sized for a two-line chip (translation +
 * grammar note) at [HintSizeOption.LARGE].
 */
internal val LEARNING_ROW_HEIGHT = 46.dp

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
 * Height depends only on [showLearningRow], which is decided per field
 * and settings (see KeyboardUiState.showLearningRow), never on the chips
 * currently shown. Within a field the keys never move while typing. Fields
 * that can never show language help (passwords, e-mail, numbers), or where
 * the user turned the learning row and grammar off, get only the slim word
 * row instead of an always-empty second row.
 */
@Composable
fun HintBar(
    suggestions: List<AutocompleteSuggestion>,
    autoCorrection: AutocompleteSuggestion?,
    assistance: LearningAssistance,
    hintSize: HintSizeOption,
    onSuggestionSelected: (AutocompleteSuggestion) -> Unit,
    modifier: Modifier = Modifier,
    showLearningRow: Boolean = true,
    onGrammarApplied: (GrammarSuggestion) -> Unit = {},
    starterPhrases: List<String> = emptyList(),
    onStarterPhrase: (String) -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(if (showLearningRow) WORD_ROW_HEIGHT + LEARNING_ROW_HEIGHT else WORD_ROW_HEIGHT)
            // No fill of its own: the keyboard's background gradient shows through (strip == background in every theme).
    ) {
        // Keyed so switching between phrases and suggestions starts scrolled to the beginning.
        if (starterPhrases.isNotEmpty()) key("phrases") {
            SuggestionRow(WORD_ROW_HEIGHT) {
                starterPhrases.forEach { phrase -> PhraseChip(phrase, hintSize, onStarterPhrase) }
            }
        } else key("suggestions") { SuggestionRow(WORD_ROW_HEIGHT) {
            suggestions.forEach { suggestion ->
                SuggestionChip(
                    suggestion = suggestion,
                    highlighted = autoCorrection != null && suggestion.word == autoCorrection.word && suggestion.kind != SuggestionKind.LITERAL,
                    hintSize = hintSize,
                    onClick = onSuggestionSelected
                )
            }
        } }
        if (showLearningRow) SuggestionRow(LEARNING_ROW_HEIGHT) {
            assistance.grammar?.let { grammar -> GrammarChip(grammar, hintSize, onGrammarApplied) }
            assistance.sentence?.let { sentence ->
                SentenceChip(
                    text = sentence.translatedText, typingLanguage = assistance.typingLanguage, hintSize = hintSize,
                    caption = when {
                        sentence.fromCorrectedSource -> "from corrected sentence"
                        else -> sentence.notes.firstOrNull()
                    }
                )
            }
            assistance.hints.forEach { hint -> HintChip(hint = hint, hintSize = hintSize) }
        }
    }
}

@Composable
private fun SuggestionRow(height: Dp, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
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
    val colors = LocalKeyboardColors.current
    val label = if (suggestion.kind == SuggestionKind.LITERAL) "“${suggestion.word}”" else suggestion.word
    Row(
        modifier = Modifier
            .fillMaxHeight()
            .widthIn(min = 56.dp)
            .background((if (highlighted) colors.accentGradient else colors.chipGradient).toBrush(), RoundedCornerShape(14.dp))
            .clickable { onClick(suggestion) }
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .semantics { contentDescription = if (highlighted) "Autocorrect to ${suggestion.word}" else "Suggestion ${suggestion.word}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            color = if (highlighted) colors.onAccent else colors.chipText,
            fontSize = fontSizeFor(hintSize),
            // Explicit: the inherited 24sp body line height would clip Large text in the 40dp row.
            lineHeight = fontSizeFor(hintSize) * 1.2f,
            fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** An everyday starter phrase shown in an empty field; tapping inserts it. Same look as a word suggestion. */
@Composable
private fun PhraseChip(phrase: String, hintSize: HintSizeOption, onClick: (String) -> Unit) {
    val colors = LocalKeyboardColors.current
    Row(
        modifier = Modifier
            .fillMaxHeight()
            .widthIn(min = 56.dp)
            .background(colors.chipGradient.toBrush(), RoundedCornerShape(14.dp))
            .clickable { onClick(phrase) }
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .semantics { contentDescription = "Insert phrase $phrase" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = phrase,
            color = colors.chipText,
            fontSize = fontSizeFor(hintSize),
            lineHeight = fontSizeFor(hintSize) * 1.2f,
            maxLines = 1
        )
    }
}

/** Suggested correction of the sentence. Tapping applies it; nothing changes otherwise. */
@Composable
private fun GrammarChip(grammar: GrammarSuggestion, hintSize: HintSizeOption, onApply: (GrammarSuggestion) -> Unit) {
    val colors = LocalKeyboardColors.current
    Row(
        modifier = Modifier
            .fillMaxHeight()
            .background(colors.accentGradient.toBrush(), RoundedCornerShape(14.dp))
            .clickable { onApply(grammar) }
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .semantics { contentDescription = "Grammar suggestion: ${grammar.corrected}. ${grammar.issues.firstOrNull()?.message.orEmpty()}" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("✓", color = colors.onAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 6.dp))
        Column(verticalArrangement = Arrangement.Center) {
            Text(grammar.corrected.trim(), color = colors.onAccent, fontSize = fontSizeFor(hintSize), lineHeight = fontSizeFor(hintSize) * 1.15f,
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                grammar.issues.joinToString(" · ") { it.message }.let { if (grammar.issues.size > 1) "${grammar.issues.size} fixes: $it" else it },
                color = colors.onAccent.copy(alpha = 0.85f), fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SentenceChip(text: String, typingLanguage: String, hintSize: HintSizeOption, caption: String? = null) {
    val colors = LocalKeyboardColors.current
    Row(
        modifier = Modifier
            .fillMaxHeight()
            .background(colors.translationGradient.toBrush(), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .semantics { contentDescription = "Translation: $text" + (caption?.let { ". $it" } ?: "") },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (typingLanguage == "en") "ES" else "EN",
            color = colors.onTranslationChip.copy(alpha = 0.8f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 6.dp)
        )
        Column(verticalArrangement = Arrangement.Center) {
            Text(text = text, color = colors.onTranslationChip, fontSize = fontSizeFor(hintSize), lineHeight = fontSizeFor(hintSize) * 1.15f,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (caption != null) Text(caption, color = colors.onTranslationChip.copy(alpha = 0.8f), fontSize = 10.sp, lineHeight = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun HintChip(hint: HintWord, hintSize: HintSizeOption) {
    val colors = LocalKeyboardColors.current
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .background(colors.chipGradient.toBrush(), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .semantics { contentDescription = "${hint.originalWord}: ${hint.translatedWord}, ${hint.formLabel}" },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Explicit line heights: the default body line height (24sp) clipped the caption in the 46dp row.
        Text(
            text = hint.translatedWord,
            color = colors.hintText,
            fontSize = fontSizeFor(hintSize),
            lineHeight = fontSizeFor(hintSize) * 1.15f,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${hint.originalWord} · ${hint.formLabel}",
            color = colors.caption,
            fontSize = 10.sp,
            lineHeight = 12.sp,
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
