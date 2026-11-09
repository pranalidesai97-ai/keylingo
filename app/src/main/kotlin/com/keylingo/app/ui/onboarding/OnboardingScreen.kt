package com.keylingo.app.ui.onboarding

import com.keylingo.app.ui.layout.AppCard
import com.keylingo.app.ui.layout.AppSpacing
import com.keylingo.app.ui.layout.appContentWidth
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.keylingo.app.R

/**
 * Walks the user through the two steps every custom keyboard requires:
 * enabling it in system settings, then switching to it while typing.
 * Neither step can be done programmatically for privacy/security reasons
 * on Android, so this screen just opens the right system UI for each.
 */
@Composable
fun OnboardingScreen(onOpenSettings: () -> Unit, onOpenThemes: () -> Unit = {}) {
    val context = LocalContext.current

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .appContentWidth()
                .padding(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.CardGap)
        ) {
            com.keylingo.app.ui.brand.KeyLingoWordmark(markSize = 52.dp)
            Text(
                text = stringResource(R.string.onboarding_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 8.dp)
            )

            OnboardingStepCard(
                title = stringResource(R.string.onboarding_step1_title),
                description = stringResource(R.string.onboarding_step1_desc),
                actionLabel = stringResource(R.string.onboarding_step1_action),
                onAction = {
                    context.startActivity(
                        Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            )

            OnboardingStepCard(
                title = stringResource(R.string.onboarding_step2_title),
                description = stringResource(R.string.onboarding_step2_desc),
                actionLabel = stringResource(R.string.onboarding_step2_action),
                onAction = {
                    val imm = context.getSystemService(InputMethodManager::class.java)
                    imm?.showInputMethodPicker()
                }
            )

            OnboardingStepCard(
                title = stringResource(R.string.onboarding_step3_title),
                description = stringResource(R.string.onboarding_step3_desc),
                actionLabel = stringResource(R.string.onboarding_step3_action),
                onAction = onOpenSettings
            )

            OnboardingStepCard(
                title = stringResource(R.string.onboarding_step4_title),
                description = stringResource(R.string.onboarding_step4_desc),
                actionLabel = stringResource(R.string.onboarding_step4_action),
                onAction = onOpenThemes
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun OnboardingStepCard(
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    // Same card, inset and button alignment as Settings: the action starts on the text's left edge.
    AppCard {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(14.dp))
        Button(onClick = onAction) {
            Text(actionLabel)
        }
    }
}
