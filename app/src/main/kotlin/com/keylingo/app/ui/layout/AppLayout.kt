package com.keylingo.app.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * One set of measurements for every companion-app screen (setup, Settings with
 * About, Theme picker, Learned words), so page margins, card insets and
 * section spacing line up from screen to screen. The keyboard does not use these.
 */
object AppSpacing {
    /** Left/right page margin on phones. */
    val ScreenHorizontal = 20.dp
    /** Gap below the top app bar. */
    val ScreenTop = 12.dp
    /** Inside a card. Card text, titles, radio buttons and buttons all start at this inset. */
    val CardHorizontal = 18.dp
    val CardVertical = 16.dp
    /** Between consecutive cards. */
    val CardGap = 14.dp
    /** Above a section header. */
    val SectionGap = 28.dp
    /** Page content never grows wider than this (tablets, landscape, foldables); it is centred instead. */
    val MaxContentWidth = 600.dp
    val CardShape = RoundedCornerShape(22.dp)

    const val CARD_TAG = "app-card"
}

/**
 * Page content column: full width up to [AppSpacing.MaxContentWidth], centred
 * beyond it, with the standard side margin. Apply after `verticalScroll` so the
 * whole screen width still scrolls.
 */
fun Modifier.appContentWidth(): Modifier = this
    .fillMaxWidth()
    .wrapContentWidth(Alignment.CenterHorizontally)
    .widthIn(max = AppSpacing.MaxContentWidth)
    .padding(horizontal = AppSpacing.ScreenHorizontal)

/** A rounded surface card with the standard border and inset. */
@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(AppSpacing.CARD_TAG)
            .clip(AppSpacing.CardShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), AppSpacing.CardShape)
            .padding(horizontal = AppSpacing.CardHorizontal, vertical = AppSpacing.CardVertical),
        content = content
    )
}
