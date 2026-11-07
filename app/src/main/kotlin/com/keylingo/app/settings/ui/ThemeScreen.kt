package com.keylingo.app.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.keylingo.app.R
import com.keylingo.app.data.settings.DarkModeOption
import com.keylingo.app.data.settings.KeyLingoSettings
import com.keylingo.app.data.settings.KeyboardThemePreset
import com.keylingo.app.settings.SettingsViewModel
import com.keylingo.app.ui.theme.KeyboardThemes

/** Dedicated theme screen, reachable from the top of Settings and from the setup screen. */
@Composable
fun ThemeScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsState()
    ThemeScreenContent(settings, onBack, viewModel::setThemePreset, viewModel::setCustomAccent)
}

/** Stateless content, testable without Hilt. Uses the same picker and palettes as the keyboard. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeScreenContent(
    settings: KeyLingoSettings,
    onBack: () -> Unit,
    onSelectPreset: (KeyboardThemePreset) -> Unit,
    onSelectCustom: (Int) -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val darkPreview = { preset: KeyboardThemePreset ->
        KeyboardThemes.useDark(preset, settings.darkMode == DarkModeOption.SYSTEM, settings.darkMode == DarkModeOption.DARK, systemDark)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_theme)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.theme_back)) }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(stringResource(R.string.theme_current).uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(presetName(settings.themePreset), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.theme_screen_desc),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            ThemePickerSection(
                selected = settings.themePreset,
                customAccentArgb = settings.customAccentArgb,
                darkPreview = darkPreview,
                onSelectPreset = onSelectPreset,
                onSelectCustom = onSelectCustom
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Settings entry: current theme name and its colours; opens [ThemeScreen]. */
@Composable
fun ThemeEntryRow(settings: KeyLingoSettings, onClick: () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = KeyboardThemes.useDark(settings.themePreset, settings.darkMode == DarkModeOption.SYSTEM, settings.darkMode == DarkModeOption.DARK, systemDark)
    val colors = KeyboardThemes.palette(settings.themePreset, settings.customAccentArgb, dark)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Two overlapping dots: keyboard background and accent.
        Box(Modifier.width(44.dp).height(30.dp)) {
            Box(Modifier.size(30.dp).background(colors.background, CircleShape).border(1.dp, colors.specialKey, CircleShape))
            Box(Modifier.padding(start = 14.dp).size(30.dp).background(colors.accent, CircleShape).border(2.dp, MaterialTheme.colorScheme.surface, CircleShape))
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleLarge)
            Text(presetName(settings.themePreset), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.settings_theme_entry_desc))
    }
}
