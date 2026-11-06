package com.keylingo.app.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keylingo.app.data.settings.DarkModeOption
import com.keylingo.app.data.settings.HintSizeOption
import com.keylingo.app.data.settings.KeyLingoSettings
import com.keylingo.app.data.settings.SettingsDataStore
import com.keylingo.app.domain.model.LearningMode
import com.keylingo.app.domain.repository.ContextPredictionProvider
import com.keylingo.app.domain.repository.LearnedVocabularyProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val learnedVocabularyProvider: LearnedVocabularyProvider,
    private val contextPredictionProvider: ContextPredictionProvider
) : ViewModel() {

    val settings: StateFlow<KeyLingoSettings> = settingsDataStore.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = KeyLingoSettings()
    )

    fun setLearningEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setLearningEnabled(enabled) }
    }

    fun setTargetLanguage(languageCode: String) {
        viewModelScope.launch { settingsDataStore.setTargetLanguage(languageCode) }
    }

    fun setDarkMode(option: DarkModeOption) {
        viewModelScope.launch { settingsDataStore.setDarkMode(option) }
    }

    fun setHintSize(option: HintSizeOption) {
        viewModelScope.launch { settingsDataStore.setHintSize(option) }
    }

    fun setLearningMode(mode: LearningMode) {
        viewModelScope.launch { settingsDataStore.setLearningMode(mode) }
    }

    fun setShowNumberRow(show: Boolean) {
        viewModelScope.launch { settingsDataStore.setShowNumberRow(show) }
    }

    fun setAutocorrectEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setAutocorrectEnabled(enabled) }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setHapticsEnabled(enabled) }
    }

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setSoundEnabled(enabled) }
    }

    /** Deletes every word and word sequence KeyLingo has learned from this user. Bundled dictionaries are unaffected. */
    fun clearPersonalData(onDone: () -> Unit) {
        viewModelScope.launch {
            learnedVocabularyProvider.clearAll()
            contextPredictionProvider.clearAll()
            onDone()
        }
    }
}
