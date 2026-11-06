package com.keylingo.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
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
    val soundEnabled: Boolean = false
)

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
            soundEnabled = prefs[Keys.SOUND] ?: false
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
}
