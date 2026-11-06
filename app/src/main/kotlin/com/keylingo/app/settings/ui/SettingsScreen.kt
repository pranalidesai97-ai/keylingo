package com.keylingo.app.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.keylingo.app.R
import com.keylingo.app.data.settings.DarkModeOption
import com.keylingo.app.data.settings.HintSizeOption
import com.keylingo.app.data.settings.TypingLanguageOption
import com.keylingo.app.domain.model.LearningMode
import com.keylingo.app.settings.SettingsViewModel
import com.keylingo.app.ui.theme.KeyboardThemes
import androidx.compose.foundation.isSystemInDarkTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenLearnedWords: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.settings_title)) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // ---- Typing
            SectionHeader(stringResource(R.string.settings_section_typing), first = true)
            ToggleRow(
                title = stringResource(R.string.settings_autocorrect),
                description = stringResource(R.string.settings_autocorrect_desc),
                checked = settings.autocorrectEnabled,
                onToggle = viewModel::setAutocorrectEnabled
            )
            Gap()
            ToggleRow(
                title = stringResource(R.string.settings_show_number_row),
                description = stringResource(R.string.settings_show_number_row_desc),
                checked = settings.showNumberRow,
                onToggle = viewModel::setShowNumberRow
            )
            Gap()
            ToggleRow(
                title = stringResource(R.string.settings_haptics),
                description = stringResource(R.string.settings_haptics_desc),
                checked = settings.hapticsEnabled,
                onToggle = viewModel::setHapticsEnabled
            )
            Gap()
            ToggleRow(
                title = stringResource(R.string.settings_sound),
                description = stringResource(R.string.settings_sound_desc),
                checked = settings.soundEnabled,
                onToggle = viewModel::setSoundEnabled
            )
            Gap()
            TypingLanguageSection(selected = settings.typingLanguage, onSelect = viewModel::setTypingLanguage)

            // ---- Appearance
            SectionHeader(stringResource(R.string.settings_section_appearance))
            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            val systemDark = isSystemInDarkTheme()
            ThemePickerSection(
                selected = settings.themePreset,
                customAccentArgb = settings.customAccentArgb,
                darkPreview = { preset ->
                    KeyboardThemes.useDark(preset, settings.darkMode == DarkModeOption.SYSTEM, settings.darkMode == DarkModeOption.DARK, systemDark)
                },
                onSelectPreset = viewModel::setThemePreset,
                onSelectCustom = viewModel::setCustomAccent
            )
            Gap()
            DarkModeSection(selected = settings.darkMode, onSelect = viewModel::setDarkMode)
            Gap()
            HintSizeSection(selected = settings.hintSize, onSelect = viewModel::setHintSize)

            // ---- Learning & translation
            SectionHeader(stringResource(R.string.settings_section_learning))
            ToggleRow(
                title = stringResource(R.string.settings_learning_enabled),
                description = stringResource(R.string.settings_learning_enabled_desc),
                checked = settings.learningEnabled,
                onToggle = viewModel::setLearningEnabled
            )
            Gap()
            ToggleRow(
                title = stringResource(R.string.settings_sentence_translation),
                description = stringResource(R.string.settings_sentence_translation_desc),
                checked = settings.sentenceTranslationEnabled,
                onToggle = viewModel::setSentenceTranslationEnabled
            )
            Gap()
            ToggleRow(
                title = stringResource(R.string.settings_grammar),
                description = stringResource(R.string.settings_grammar_desc),
                checked = settings.grammarEnabled,
                onToggle = viewModel::setGrammarEnabled
            )
            Gap()
            LearningModeSection(selected = settings.learningMode, onSelect = viewModel::setLearningMode)
            Gap()
            LanguageSection(selectedCode = settings.targetLanguageCode, onSelect = viewModel::setTargetLanguage)

            // ---- Personal dictionary
            SectionHeader(stringResource(R.string.settings_section_personal))
            Text(stringResource(R.string.settings_learned_words), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.settings_learned_words_desc),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onOpenLearnedWords) { Text(stringResource(R.string.settings_learned_words_open)) }
            Gap()
            ClearPersonalDataSection(onConfirm = { done -> viewModel.clearPersonalData(done) })

            // ---- About
            SectionHeader(stringResource(R.string.settings_about))
            Text(
                text = stringResource(R.string.settings_about_desc),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String, first: Boolean = false) {
    if (!first) {
        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
    }
    Spacer(modifier = Modifier.height(if (first) 4.dp else 16.dp))
    Text(title.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun Gap() = Spacer(modifier = Modifier.height(20.dp))

@Composable
private fun TypingLanguageSection(selected: TypingLanguageOption, onSelect: (TypingLanguageOption) -> Unit) {
    Text(stringResource(R.string.settings_typing_language), style = MaterialTheme.typography.titleLarge)
    Text(
        stringResource(R.string.settings_typing_language_desc),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(4.dp))
    val options = listOf(
        TypingLanguageOption.AUTO to stringResource(R.string.settings_typing_language_auto),
        TypingLanguageOption.ENGLISH to stringResource(R.string.settings_typing_language_en),
        TypingLanguageOption.SPANISH to stringResource(R.string.settings_typing_language_es)
    )
    Column(modifier = Modifier.selectableGroup()) {
        options.forEach { (option, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = option == selected, onClick = { onSelect(option) }),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = option == selected, onClick = { onSelect(option) })
                Text(label, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, description: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    // The whole row is the touch target (and one accessibility node), not just the small switch.
    Row(
        modifier = Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onToggle),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ClearPersonalDataSection(onConfirm: (onDone: () -> Unit) -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    var cleared by remember { mutableStateOf(false) }
    Text(stringResource(R.string.settings_personal_data), style = MaterialTheme.typography.titleLarge)
    Text(
        stringResource(if (cleared) R.string.settings_personal_data_cleared else R.string.settings_personal_data_desc),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedButton(onClick = { confirming = true }) { Text(stringResource(R.string.settings_personal_data_clear)) }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.settings_personal_data_confirm_title)) },
            text = { Text(stringResource(R.string.settings_personal_data_confirm_text)) },
            confirmButton = {
                TextButton(onClick = { confirming = false; onConfirm { cleared = true } }) {
                    Text(stringResource(R.string.settings_personal_data_clear))
                }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(android.R.string.cancel)) } }
        )
    }
}

@Composable
private fun LearningModeSection(selected: LearningMode, onSelect: (LearningMode) -> Unit) {
    // Data-driven on purpose: adding a future mode (e.g. Exam or
    // Conversation) means adding one entry here plus its two string
    // resources -- no new branching logic in this Composable.
    val modes = listOf(
        Triple(LearningMode.BEGINNER, R.string.learning_mode_beginner_title, R.string.learning_mode_beginner_desc),
        Triple(LearningMode.INTERMEDIATE, R.string.learning_mode_intermediate_title, R.string.learning_mode_intermediate_desc),
        Triple(LearningMode.ADVANCED, R.string.learning_mode_advanced_title, R.string.learning_mode_advanced_desc)
    )

    Text(stringResource(R.string.settings_learning_mode), style = MaterialTheme.typography.titleLarge)
    Spacer(modifier = Modifier.height(8.dp))
    Column(modifier = Modifier.selectableGroup()) {
        modes.forEach { (mode, titleRes, descRes) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = mode == selected, onClick = { onSelect(mode) })
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                RadioButton(selected = mode == selected, onClick = { onSelect(mode) })
                Column(modifier = Modifier.padding(start = 8.dp, top = 12.dp)) {
                    Text(stringResource(titleRes), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(descRes),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageSection(selectedCode: String, onSelect: (String) -> Unit) {
    // Only Spanish is bundled in this MVP; the list is data-driven so
    // adding a new LanguagePack later just means adding an entry here.
    val languages = listOf("es" to "Spanish")

    Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleLarge)
    Spacer(modifier = Modifier.height(8.dp))
    Column(modifier = Modifier.selectableGroup()) {
        languages.forEach { (code, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = code == selectedCode, onClick = { onSelect(code) }),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = code == selectedCode, onClick = { onSelect(code) })
                Text(label, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun DarkModeSection(selected: DarkModeOption, onSelect: (DarkModeOption) -> Unit) {
    Text(stringResource(R.string.settings_dark_mode), style = MaterialTheme.typography.titleLarge)
    Spacer(modifier = Modifier.height(8.dp))
    Column(modifier = Modifier.selectableGroup()) {
        DarkModeOption.entries.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = option == selected, onClick = { onSelect(option) }),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = option == selected, onClick = { onSelect(option) })
                Text(option.name.lowercase().replaceFirstChar { it.uppercase() }, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun HintSizeSection(selected: HintSizeOption, onSelect: (HintSizeOption) -> Unit) {
    Text(stringResource(R.string.settings_hint_size), style = MaterialTheme.typography.titleLarge)
    Spacer(modifier = Modifier.height(8.dp))
    Column(modifier = Modifier.selectableGroup()) {
        val options = listOf(
            HintSizeOption.SMALL to stringResource(R.string.settings_hint_size_small),
            HintSizeOption.MEDIUM to stringResource(R.string.settings_hint_size_medium),
            HintSizeOption.LARGE to stringResource(R.string.settings_hint_size_large)
        )
        options.forEach { (option, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = option == selected, onClick = { onSelect(option) }),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = option == selected, onClick = { onSelect(option) })
                Text(label, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
