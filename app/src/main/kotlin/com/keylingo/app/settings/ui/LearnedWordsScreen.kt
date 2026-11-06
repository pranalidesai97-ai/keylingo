package com.keylingo.app.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.keylingo.app.R
import com.keylingo.app.domain.model.LearnedWord
import com.keylingo.app.settings.LearnedWordsViewModel

@Composable
fun LearnedWordsScreen(onBack: () -> Unit, viewModel: LearnedWordsViewModel = hiltViewModel()) {
    val words by viewModel.words.collectAsState()
    LearnedWordsContent(words, onBack, viewModel::forget, viewModel::forgetAll)
}

/** Stateless content, so it can be previewed and tested without Hilt. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnedWordsContent(
    words: List<LearnedWord>?,
    onBack: () -> Unit,
    onForget: (LearnedWord) -> Unit,
    onForgetAll: () -> Unit
) {
    var filter by rememberSaveable { mutableStateOf("") }
    var confirmAll by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.learned_words_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.learned_words_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when {
                words == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                words.isEmpty() -> Text(
                    stringResource(R.string.learned_words_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp)
                )
                else -> {
                    OutlinedTextField(
                        value = filter,
                        onValueChange = { filter = it },
                        label = { Text(stringResource(R.string.learned_words_filter)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                    val shown = words.filter { filter.isBlank() || it.word.contains(filter.trim(), ignoreCase = true) }
                    LazyColumn(Modifier.weight(1f)) {
                        items(shown, key = { "${it.languageCode}:${it.word}" }) { w ->
                            LearnedWordRow(w, onForget)
                            HorizontalDivider(Modifier.padding(horizontal = 20.dp))
                        }
                    }
                    OutlinedButton(
                        onClick = { confirmAll = true },
                        modifier = Modifier.padding(20.dp)
                    ) { Text(stringResource(R.string.settings_personal_data_clear)) }
                }
            }
        }
    }
    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text(stringResource(R.string.settings_personal_data_confirm_title)) },
            text = { Text(stringResource(R.string.settings_personal_data_confirm_text)) },
            confirmButton = {
                TextButton(onClick = { confirmAll = false; onForgetAll() }) { Text(stringResource(R.string.settings_personal_data_clear)) }
            },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text(stringResource(android.R.string.cancel)) } }
        )
    }
}

@Composable
private fun LearnedWordRow(word: LearnedWord, onForget: (LearnedWord) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(word.word, style = MaterialTheme.typography.titleMedium)
            Text(
                "${word.languageCode.uppercase()} · " + (word.organicCount + word.acceptedCount).let { n -> pluralStringResource(R.plurals.learned_words_uses, n, n) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = { onForget(word) }) {
            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.learned_words_delete, word.word))
        }
    }
}
