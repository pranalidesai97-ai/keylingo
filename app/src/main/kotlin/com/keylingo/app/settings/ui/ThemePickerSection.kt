package com.keylingo.app.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.keylingo.app.R
import com.keylingo.app.data.settings.KeyboardThemePreset
import com.keylingo.app.ui.theme.KeyboardColors
import com.keylingo.app.ui.theme.KeyboardThemes
import com.keylingo.app.ui.theme.toBrush

/** Human-readable preset names (also used by the settings summary). */
@Composable
fun presetName(preset: KeyboardThemePreset): String = stringResource(
    when (preset) {
        KeyboardThemePreset.OCEAN_MIST -> R.string.theme_ocean_mist
        KeyboardThemePreset.SUNSET_BLOOM -> R.string.theme_sunset_bloom
        KeyboardThemePreset.AQUA_BREEZE -> R.string.theme_aqua_breeze
        KeyboardThemePreset.GOLDEN_DUNE -> R.string.theme_golden_dune
        KeyboardThemePreset.SLATE_GREY -> R.string.theme_slate_grey
        KeyboardThemePreset.CUSTOM -> R.string.theme_custom
    }
)

/**
 * Theme presets as miniature keyboard previews, then a custom colour picker
 * (quick swatches + hue slider). Previews use exactly the palettes the
 * keyboard draws with, in the variant the keyboard will actually use.
 */
@Composable
fun ThemePickerSection(
    selected: KeyboardThemePreset,
    customAccentArgb: Int,
    darkPreview: (KeyboardThemePreset) -> Boolean,
    onSelectPreset: (KeyboardThemePreset) -> Unit,
    onSelectCustom: (Int) -> Unit
) {
    val presets = KeyboardThemePreset.entries.filter { it != KeyboardThemePreset.CUSTOM }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        presets.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { preset ->
                    ThemeCard(
                        name = presetName(preset),
                        colors = KeyboardThemes.palette(preset, null, darkPreview(preset)),
                        isSelected = preset == selected,
                        onClick = { onSelectPreset(preset) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.theme_custom), style = MaterialTheme.typography.bodyLarge)
        ThemeCard(
            name = stringResource(R.string.theme_custom_preview),
            colors = KeyboardThemes.palette(KeyboardThemePreset.CUSTOM, customAccentArgb, darkPreview(KeyboardThemePreset.CUSTOM)),
            isSelected = selected == KeyboardThemePreset.CUSTOM,
            onClick = { onSelectCustom(customAccentArgb) },
            modifier = Modifier.fillMaxWidth()
        )
        KeyboardThemes.customSwatches.chunked(7).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { argb ->
                    val isSel = selected == KeyboardThemePreset.CUSTOM && argb == customAccentArgb
                    Box(
                        Modifier
                            .size(34.dp)
                            .background(Color(argb), CircleShape)
                            .border(if (isSel) 3.dp else 1.dp, if (isSel) MaterialTheme.colorScheme.onSurface else Color.Black.copy(alpha = 0.15f), CircleShape)
                            .clickable { onSelectCustom(argb) }
                            .semantics { contentDescription = "Custom colour"; role = Role.RadioButton; this.selected = isSel }
                    )
                }
            }
        }
        var hue by remember(customAccentArgb) { mutableFloatStateOf(KeyboardThemes.toHsl(Color(customAccentArgb)).h) }
        Text(stringResource(R.string.theme_custom_hue), style = MaterialTheme.typography.labelLarge)
        Slider(
            value = hue,
            onValueChange = { hue = it },
            onValueChangeFinished = { onSelectCustom(KeyboardThemes.hsl(hue, 0.75f, 0.5f).toArgb()) },
            valueRange = 0f..359f
        )
    }
}

@Composable
private fun ThemeCard(name: String, colors: KeyboardColors, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier
            .border(if (isSelected) 3.dp else 1.dp, if (isSelected) colors.accent else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(6.dp)
            .semantics { role = Role.RadioButton; this.selected = isSelected },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Miniature keyboard: strip with an accent chip, two key rows, special keys.
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.backgroundGradient.toBrush(), RoundedCornerShape(10.dp))
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.width(22.dp).height(8.dp).background(colors.translationGradient.toBrush(), RoundedCornerShape(4.dp)))
                Box(Modifier.width(16.dp).height(8.dp).background(colors.chipGradient.toBrush(), RoundedCornerShape(4.dp)))
            }
            repeat(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(5) { Box(Modifier.weight(1f).height(11.dp).background(colors.key, RoundedCornerShape(3.dp))) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(Modifier.weight(1f).height(11.dp).background(colors.specialKeyGradient.toBrush(), RoundedCornerShape(3.dp)))
                Box(Modifier.weight(3f).height(11.dp).background(colors.key, RoundedCornerShape(3.dp)))
                Box(Modifier.weight(1f).height(11.dp).background(colors.accentGradient.toBrush(), RoundedCornerShape(3.dp)))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        if (isSelected) {
            Text(
                "✓ " + stringResource(R.string.theme_selected),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
