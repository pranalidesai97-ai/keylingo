package com.keylingo.app.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keylingo.app.domain.model.LearnedWord
import com.keylingo.app.domain.repository.ContextPredictionProvider
import com.keylingo.app.domain.repository.LearnedVocabularyProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backs the "Learned words" screen: lists the personal vocabulary and forgets single words. */
@HiltViewModel
class LearnedWordsViewModel @Inject constructor(
    private val learnedVocabularyProvider: LearnedVocabularyProvider,
    private val contextPredictionProvider: ContextPredictionProvider
) : ViewModel() {

    /** Null while loading. */
    private val _words = MutableStateFlow<List<LearnedWord>?>(null)
    val words: StateFlow<List<LearnedWord>?> = _words.asStateFlow()

    init { reload() }

    fun reload() {
        viewModelScope.launch { _words.value = learnedVocabularyProvider.listLearned() }
    }

    /** Forgets [word] everywhere: no longer suggested, no longer protected from autocorrect, gone from predictions. */
    fun forget(word: LearnedWord) {
        _words.value = _words.value?.filterNot { it.word == word.word && it.languageCode == word.languageCode }
        viewModelScope.launch {
            learnedVocabularyProvider.forget(word.word, word.languageCode)
            contextPredictionProvider.forgetWord(word.word, word.languageCode)
        }
    }

    fun forgetAll() {
        _words.value = emptyList()
        viewModelScope.launch {
            learnedVocabularyProvider.clearAll()
            contextPredictionProvider.clearAll()
        }
    }
}
