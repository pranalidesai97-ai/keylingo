package com.keylingo.app.ui.layout

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.keylingo.app.data.settings.KeyLingoSettings
import com.keylingo.app.data.settings.SettingsDataStore
import com.keylingo.app.settings.SettingsViewModel
import com.keylingo.app.settings.ui.SettingsScreen
import com.keylingo.app.settings.ui.ThemeScreenContent
import com.keylingo.app.testing.FakeContextPrediction
import com.keylingo.app.testing.FakeLearnedVocabulary
import com.keylingo.app.ui.onboarding.OnboardingScreen
import com.keylingo.app.ui.theme.KeyLingoAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Layout geometry of the companion-app screens, measured from the real
 * composables: every card shares the same page margins on every screen,
 * section headers and buttons start on the card text's left edge, theme cards
 * are equal, and on wide screens content is capped and centred.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h3000dp-xhdpi")
class AppLayoutAlignmentTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun bounds(node: SemanticsNodeInteraction): DpRect = node.getBoundsInRoot()
    private fun cards(): List<DpRect> = compose.onAllNodes(hasTestTag(AppSpacing.CARD_TAG)).fetchSemanticsNodes().map {
        val r = it.boundsInRoot; val d = compose.activity.resources.displayMetrics.density
        DpRect((r.left / d).dp, (r.top / d).dp, (r.right / d).dp, (r.bottom / d).dp)
    }
    private fun assertDp(message: String, expected: Dp, actual: Dp) = assertEquals(message, expected.value, actual.value, 0.6f)

    private fun settingsViewModel() = SettingsViewModel(SettingsDataStore(ApplicationProvider.getApplicationContext()), FakeLearnedVocabulary(), FakeContextPrediction())

    @Test fun setupAndSettingsCardsShareMarginsAndInsets() {
        var showSettings by androidx.compose.runtime.mutableStateOf(false)
        compose.setContent { KeyLingoAppTheme(darkTheme = false) { if (showSettings) SettingsScreen(onBack = {}, viewModel = settingsViewModel()) else OnboardingScreen(onOpenSettings = {}) } }
        compose.waitForIdle()
        // Setup: four step cards at the page margins; each button starts on its card's text edge.
        val setupCards = cards()
        assertEquals(4, setupCards.size)
        for (c in setupCards) { assertDp("setup card left", 20.dp, c.left); assertDp("setup card right", 391.dp, c.right) }
        val title = bounds(compose.onNodeWithText("1. Enable the keyboard"))
        val button = bounds(compose.onNodeWithText("Open keyboard settings", useUnmergedTree = true))
        assertDp("title inset", 20.dp + AppSpacing.CardHorizontal, title.left)
        assertTrue("button starts at the text edge (button ${button.left}, text ${title.left})", button.left < title.left + 30.dp && button.left >= title.left)

        showSettings = true
        compose.waitForIdle()
        val settingsCards = cards()
        assertTrue(settingsCards.size >= 5)
        for (c in settingsCards) { assertDp("settings card left", 20.dp, c.left); assertDp("settings card right", 391.dp, c.right) }
        // Section header on the same left edge as the card text below it.
        val header = bounds(compose.onNodeWithText("TYPING"))
        val autocorrect = bounds(compose.onNodeWithText("Autocorrect"))
        assertDp("header aligned with card text", autocorrect.left, header.left)
        // Radio circles pulled back to the text edge: the 48 dp radio target starts 12 dp before the text.
        val radioRow = bounds(compose.onAllNodesWithText("Auto-detect").onFirst())
        assertTrue("radio label left ${radioRow.left}", radioRow.left < autocorrect.left + 48.dp)
    }

    @Test fun themeCardsAreEqualInEveryRow() {
        compose.setContent { KeyLingoAppTheme(darkTheme = false) { ThemeScreenContent(KeyLingoSettings(), {}, {}, {}) } }
        compose.waitForIdle()
        val widths = listOf("Ocean Mist", "Sunset Bloom", "Aqua Breeze", "Golden Dune", "Slate Grey").map { name ->
            compose.onAllNodesWithText(name).fetchSemanticsNodes().maxOf { it.boundsInRoot.width }
        }
        val first = widths.first()
        widths.forEach { assertEquals("theme card widths $widths", first, it, 2f) }
    }

    @Test @Config(qualifiers = "w900dp-h1400dp-mdpi")
    fun wideScreensCapAndCentreTheContent() {
        compose.setContent { KeyLingoAppTheme(darkTheme = false) { OnboardingScreen(onOpenSettings = {}) } }
        compose.waitForIdle()
        for (c in cards()) {
            assertDp("card width capped", AppSpacing.MaxContentWidth - AppSpacing.ScreenHorizontal * 2, c.right - c.left)
            assertDp("centred", 900.dp - c.right, c.left)
        }
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.onFirst() = this[0]
