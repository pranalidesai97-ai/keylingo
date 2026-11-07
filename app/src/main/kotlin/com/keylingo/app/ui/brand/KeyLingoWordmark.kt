package com.keylingo.app.ui.brand

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keylingo.app.R

/** KeyLingo brand gradient (same stops as the launcher icon). */
val KeyLingoBrandGradient = listOf(Color(0xFF1B2A6B), Color(0xFF1E78D6), Color(0xFF2BC4B4))

/**
 * The KeyLingo mark beside the "KeyLingo" wordmark: "Key" in the text colour,
 * "Lingo" in the brand gradient.
 */
@Composable
fun KeyLingoWordmark(modifier: Modifier = Modifier, markSize: Dp = 44.dp) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Row(modifier.semantics(mergeDescendants = true) { contentDescription = "KeyLingo" }, verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.ic_keylingo_mark), contentDescription = null, modifier = Modifier.size(markSize))
        Spacer(Modifier.width(markSize * 0.28f))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = onSurface)) { append("Key") }
                withStyle(SpanStyle(brush = Brush.linearGradient(KeyLingoBrandGradient))) { append("Lingo") }
            },
            fontSize = (markSize.value * 0.62f).sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp
        )
    }
}
