package com.keylingo.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.keylingo.app.domain.model.LearningMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class DarkModeOption { SYSTEM, LIGHT, DARK }
enum class HintSizeOption { SMALL, MEDIUM, LARGE }

/**
 * Keyboard colour themes: Ocean Mist (the approved flat blue design), four
 * gradient themes, and a custom colour. Each has light and dark variants;
 * the dark-mode setting decides which is used. [CUSTOM] derives the palette
 * from [KeyLingoSettings.customAccentArgb].
 */
/** Which language the user is typing. AUTO detects it per sentence; the others force it (and skip detection). */
enum class TypingLanguageOption(val code: String?) { AUTO(null), ENGLISH("en"), SPANISH("es") }

enum class KeyboardThemePreset(val prefersDark: Boolean = false) {
    OCEAN_MIST, SUNSET_BLOOM, AQUA_BREEZE, GOLDEN_DUNE, SLATE_GREY, CUSTOM;

    companion object {
        /** Golden Dune succeeds the former default, Sunlight Yellow. */
        val DEFAULT = GOLDEN_DUNE

        /**
         * Reads a stored theme name, including names from earlier versions, so an
         * upgrade keeps the user's choice: Ocean Blue is now Ocean Mist (same colours),
         * and the other retired flat themes map to the closest new gradient.
         */
        fun fromStored(name: String?): KeyboardThemePreset? = when (name) {
            null -> null
            "OCEAN_BLUE" -> OCEAN_MIST
            "SUNLIGHT_YELLOW" -> GOLDEN_DUNE
            "BLUSH_PINK" -> SUNSET_BLOOM
            "MINT_GREEN" -> AQUA_BREEZE
            "LAVENDER_PURPLE", "DARK_VIOLET" -> SLATE_GREY
            else -> entries.firstOrNull { it.name == name }
        }
    }
}

data class KeyLingoSettings(
    val learningEnabled: Boolean = true,
    val targetLanguageCode: String = "es",
    val darkMode: DarkModeOption = DarkModeOption.SYSTEM,
    val hintSize: HintSizeOption = HintSizeOption.MEDIUM,
    val learningMode: LearningMode = LearningMode.INTERMEDIATE,
    val showNumberRow: Boolean = true,
    val autocorrectEnabled: Boolean = true,
    /** Vibration on key press. OFF by default; independent of [soundEnabled]. */
    val hapticsEnabled: Boolean = false,
    /** Key-click sound. OFF by default; independent of [hapticsEnabled]. */
    val soundEnabled: Boolean = false,
    val themePreset: KeyboardThemePreset = KeyboardThemePreset.DEFAULT,
    /** Accent colour (ARGB) used by [KeyboardThemePreset.CUSTOM]. */
    val customAccentArgb: Int = DEFAULT_CUSTOM_ACCENT,
    val typingLanguage: TypingLanguageOption = TypingLanguageOption.AUTO,
    /** Grammar suggestions in the learning row; independent of [learningEnabled]. */
    val grammarEnabled: Boolean = true,
    /** Show the whole-sentence translation chip (word hints are controlled by [learningEnabled]). */
    val sentenceTranslationEnabled: Boolean = true
) {
    companion object {
        const val DEFAULT_CUSTOM_ACCENT: Int = 0xFF2E9E8F.toInt()
    }
}

private val Context.dataStore by preferencesDataStore(name = "keylingo_settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) {
    private object Keys {
        val LEARNING_ENABLED = booleanPreferencesKey("learning_enabled")
        val TARGET_LANGUAGE = stringPreferencesKey("target_language")
        val DARK_MODE = stringPreferencesKey("dark_mode")
        val HINT_SIZE = stringPreferencesKey("hint_size")
        val LEARNING_MODE = stringPreferencesKey("learning_mode")
        val SHOW_NUMBER_ROW = booleanPreferencesKey("show_number_row")
        val AUTOCORRECT = booleanPreferencesKey("autocorrect_enabled")
        val HAPTICS = booleanPreferencesKey("haptics_enabled")
        val SOUND = booleanPreferencesKey("sound_enabled")
        val THEME = stringPreferencesKey("theme_preset")
        val CUSTOM_ACCENT = intPreferencesKey("custom_accent_argb")
        val TYPING_LANGUAGE = stringPreferencesKey("typing_language")
        val GRAMMAR = booleanPreferencesKey("grammar_enabled")
        val SENTENCE_TRANSLATION = booleanPreferencesKey("sentence_translation_enabled")
    }

    val settingsFlow: Flow<KeyLingoSettings> = context.dataStore.data.map { prefs ->
        KeyLingoSettings(
            learningEnabled = prefs[Keys.LEARNING_ENABLED] ?: true,
            targetLanguageCode = prefs[Keys.TARGET_LANGUAGE] ?: "es",
            darkMode = prefs[Keys.DARK_MODE]?.let { runCatching { DarkModeOption.valueOf(it) }.getOrNull() }
                ?: DarkModeOption.SYSTEM,
            hintSize = prefs[Keys.HINT_SIZE]?.let { runCatching { HintSizeOption.valueOf(it) }.getOrNull() }
                ?: HintSizeOption.MEDIUM,
            learningMode = prefs[Keys.LEARNING_MODE]?.let { runCatching { LearningMode.valueOf(it) }.getOrNull() }
                ?: LearningMode.INTERMEDIATE,
            showNumberRow = prefs[Keys.SHOW_NUMBER_ROW] ?: true,
            autocorrectEnabled = prefs[Keys.AUTOCORRECT] ?: true,
            // Absent key = the user never chose: vibration stays off. An explicit choice is respected.
            hapticsEnabled = prefs[Keys.HAPTICS] ?: false,
            soundEnabled = prefs[Keys.SOUND] ?: false,
            themePreset = KeyboardThemePreset.fromStored(prefs[Keys.THEME]) ?: KeyboardThemePreset.DEFAULT,
            customAccentArgb = prefs[Keys.CUSTOM_ACCENT] ?: KeyLingoSettings.DEFAULT_CUSTOM_ACCENT,
            typingLanguage = prefs[Keys.TYPING_LANGUAGE]?.let { runCatching { TypingLanguageOption.valueOf(it) }.getOrNull() }
                ?: TypingLanguageOption.AUTO,
            grammarEnabled = prefs[Keys.GRAMMAR] ?: true,
            sentenceTranslationEnabled = prefs[Keys.SENTENCE_TRANSLATION] ?: true
        )
    }

    suspend fun setLearningEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.LEARNING_ENABLED] = enabled }
    }

    suspend fun setTargetLanguage(languageCode: String) {
        context.dataStore.edit { it[Keys.TARGET_LANGUAGE] = languageCode }
    }

    suspend fun setDarkMode(option: DarkModeOption) {
        context.dataStore.edit { it[Keys.DARK_MODE] = option.name }
    }

    suspend fun setHintSize(option: HintSizeOption) {
        context.dataStore.edit { it[Keys.HINT_SIZE] = option.name }
    }

    suspend fun setLearningMode(mode: LearningMode) {
        context.dataStore.edit { it[Keys.LEARNING_MODE] = mode.name }
    }

    suspend fun setShowNumberRow(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_NUMBER_ROW] = show }
    }

    suspend fun setAutocorrectEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTOCORRECT] = enabled }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HAPTICS] = enabled }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SOUND] = enabled }
    }

    suspend fun setGrammarEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.GRAMMAR] = enabled }
    }

    suspend fun setSentenceTranslationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SENTENCE_TRANSLATION] = enabled }
    }

    suspend fun setTypingLanguage(option: TypingLanguageOption) {
        context.dataStore.edit { it[Keys.TYPING_LANGUAGE] = option.name }
    }

    suspend fun setThemePreset(preset: KeyboardThemePreset) {
        context.dataStore.edit { it[Keys.THEME] = preset.name }
    }

    /** Choosing a custom colour also switches the theme to [KeyboardThemePreset.CUSTOM]. */
    suspend fun setCustomAccent(argb: Int) {
        context.dataStore.edit {
            it[Keys.CUSTOM_ACCENT] = argb
            it[Keys.THEME] = KeyboardThemePreset.CUSTOM.name
        }
    }
}
