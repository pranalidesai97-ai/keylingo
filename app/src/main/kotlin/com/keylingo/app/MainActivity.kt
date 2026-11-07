package com.keylingo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.hilt.navigation.compose.hiltViewModel
import com.keylingo.app.data.settings.DarkModeOption
import com.keylingo.app.settings.SettingsViewModel
import com.keylingo.app.settings.ui.LearnedWordsScreen
import com.keylingo.app.settings.ui.SettingsScreen
import com.keylingo.app.settings.ui.ThemeScreen
import com.keylingo.app.ui.onboarding.OnboardingScreen
import com.keylingo.app.ui.theme.KeyLingoAppTheme
import com.keylingo.app.ui.theme.KeyboardThemes
import dagger.hilt.android.AndroidEntryPoint

private object Routes {
    const val ONBOARDING = "onboarding"
    const val SETTINGS = "settings"
    const val LEARNED_WORDS = "learned_words"
    const val THEMES = "themes"
}

/**
 * The companion app's only Activity. Its job is entirely setup/configuration:
 * guide the user to enable + switch to the KeyLingo keyboard, and expose the
 * settings screen. All actual typing happens in [com.keylingo.app.keyboard.KeyLingoInputMethodService].
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KeyLingoApp()
        }
    }
}

@Composable
private fun KeyLingoApp() {
    val navController: NavHostController = rememberNavController()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val settings by settingsViewModel.settings.collectAsState()

    val darkTheme = when (settings.darkMode) {
        DarkModeOption.DARK -> true
        DarkModeOption.LIGHT -> false
        DarkModeOption.SYSTEM -> isSystemInDarkTheme()
    }

    val keyboardPalette = KeyboardThemes.palette(settings.themePreset, settings.customAccentArgb, darkTheme)
    KeyLingoAppTheme(darkTheme = darkTheme, accent = keyboardPalette.accent, onAccent = keyboardPalette.onAccent) {
        NavHost(navController = navController, startDestination = Routes.ONBOARDING) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenThemes = { navController.navigate(Routes.THEMES) }
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenLearnedWords = { navController.navigate(Routes.LEARNED_WORDS) },
                    onOpenThemes = { navController.navigate(Routes.THEMES) }
                )
            }
            composable(Routes.THEMES) {
                ThemeScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.LEARNED_WORDS) {
                LearnedWordsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
