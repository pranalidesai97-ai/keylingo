package com.keylingo.app.ui.onboarding

import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
fun OnboardingScreen(onOpenSettings: () -> Unit) {
    val context = LocalContext.current

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.titleLarge
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onAction,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(actionLabel)
            }
        }
    }
}
