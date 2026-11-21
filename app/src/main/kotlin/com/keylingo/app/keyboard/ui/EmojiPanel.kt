package com.keylingo.app.keyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import com.keylingo.app.ui.theme.LocalKeyboardColors
import com.keylingo.app.ui.theme.toBrush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keylingo.app.domain.model.EmojiCategory
import com.keylingo.app.keyboard.KeyboardLayoutProvider

/**
 * Height of the emoji suggestion row. Reserved per field (see KeyboardUiState.showEmojiRow),
 * never toggled by its content, so the keys do not move while typing.
 */
internal val EMOJI_ROW_HEIGHT = 36.dp

/**
 * Emoji for the word just typed (heart -> ❤️ 💕 💖) or the most used emoji. A row of its own,
 * below the English and Spanish rows, so it never replaces or reorders them.
 */
@Composable
internal fun EmojiSuggestionRow(emojis: List<String>, onSelect: (String) -> Unit) {
    val colors = LocalKeyboardColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(EMOJI_ROW_HEIGHT)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        emojis.forEach { emoji ->
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(min = 44.dp)
                    .background(colors.chipGradient.toBrush(), RoundedCornerShape(12.dp))
                    .clickable { onSelect(emoji) }
                    .padding(horizontal = 8.dp)
                    .semantics { contentDescription = "Emoji $emoji" },
                contentAlignment = Alignment.Center
            ) { Text(emoji, fontSize = 20.sp, lineHeight = 24.sp, color = colors.chipText) }
        }
    }
}

/**
 * The emoji panel inside the key area: a header (search + category tabs) above a scrollable grid.
 * It fills the space the letter rows use, so opening it never resizes the keyboard.
 */
@Composable
internal fun EmojiPanel(
    categories: Map<EmojiCategory, List<String>>,
    recent: List<String>,
    onEmoji: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalKeyboardColors.current
    var category by rememberSaveable { mutableStateOf(if (recent.isNotEmpty()) EmojiCategory.RECENT else EmojiCategory.SMILEYS) }
    // Until the catalogue has loaded (a fraction of a second after the keyboard starts) the built-in list is shown.
    val emojis = if (category == EmojiCategory.RECENT) recent else categories[category] ?: KeyboardLayoutProvider.EMOJI
    val gridState = rememberLazyGridState()
    LaunchedEffect(category) { gridState.scrollToItem(0) }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 2.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1.2f)
                    .background(colors.chipGradient.toBrush(), RoundedCornerShape(12.dp))
                    .clickable(onClick = onSearch)
                    .semantics { contentDescription = "Search emoji"; role = Role.Button },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Search, contentDescription = null, tint = colors.chipText) }
            // All ten tabs always visible: they share the row's width (no hidden, scrolled-away categories).
            Row(
                modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EmojiCategory.entries.forEach { c ->
                    val isSelected = c == category
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(if (isSelected) colors.accentGradient.toBrush() else SolidColor(Color.Transparent), RoundedCornerShape(12.dp))
                            .clickable { category = c }
                            .semantics { contentDescription = "Emoji category ${c.label}"; role = Role.Tab; selected = isSelected },
                        contentAlignment = Alignment.Center
                    ) { Text(c.icon, fontSize = 18.sp, lineHeight = 22.sp) }
                }
            }
        }
        Box(modifier = Modifier.fillMaxWidth().weight(3f)) {
            if (emojis.isEmpty()) {
                Text(
                    if (category == EmojiCategory.RECENT) "Emoji you use will appear here" else "",
                    color = colors.hintText, fontSize = 14.sp, modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyVerticalGrid(columns = GridCells.Fixed(8), state = gridState, modifier = Modifier.fillMaxSize()) {
                    items(emojis, key = { it }) { emoji -> EmojiCell(emoji, onEmoji) }
                }
            }
        }
    }
}

@Composable
private fun EmojiCell(emoji: String, onEmoji: (String) -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable { onEmoji(emoji) }
            .semantics { contentDescription = "Insert emoji $emoji" },
        contentAlignment = Alignment.Center
    ) { Text(emoji, fontSize = 24.sp, lineHeight = 28.sp, color = LocalKeyboardColors.current.keyText) }
}

/**
 * Emoji search: the query (typed with KeyLingo's own letter keys below) and the matching emoji.
 * Takes one key-row of height, so the panel keeps its size.
 */
@Composable
internal fun EmojiSearchBar(
    query: String,
    results: List<String>,
    recent: List<String>,
    onEmoji: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalKeyboardColors.current
    Row(modifier = modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(1f)
                .clickable(onClick = onClose)
                .semantics { contentDescription = "Back to emoji"; role = Role.Button },
            contentAlignment = Alignment.Center
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = colors.keyText) }
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(min = 96.dp, max = 150.dp)
                .background(colors.chipGradient.toBrush(), RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp)
                .semantics { contentDescription = "Emoji search: $query" },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = colors.chipText, modifier = Modifier.size(18.dp))
            Text(
                if (query.isEmpty()) "Search emoji" else "$query▏",
                color = if (query.isEmpty()) colors.hintText else colors.chipText,
                fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        val shown = if (query.isBlank()) recent else results
        LazyRow(
            modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(shown, key = { it }) { emoji ->
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f)
                        .clickable { onEmoji(emoji) }
                        .semantics { contentDescription = "Insert emoji $emoji" },
                    contentAlignment = Alignment.Center
                ) { Text(emoji, fontSize = 22.sp, lineHeight = 26.sp) }
            }
        }
    }
}
