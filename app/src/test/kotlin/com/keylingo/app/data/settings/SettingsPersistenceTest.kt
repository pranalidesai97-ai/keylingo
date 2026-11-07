package com.keylingo.app.data.settings

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings round-trip through the real DataStore file (what the keyboard reads after a restart). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsPersistenceTest {
    private val store = SettingsDataStore(ApplicationProvider.getApplicationContext())

    @Test fun vibrationAndSoundDefaultOffAndPersistIndependently() = runBlocking {
        val fresh = store.settingsFlow.first()
        assertFalse(fresh.hapticsEnabled); assertFalse(fresh.soundEnabled)
        store.setSoundEnabled(true)
        store.settingsFlow.first().let { assertTrue(it.soundEnabled); assertFalse(it.hapticsEnabled) }
        store.setHapticsEnabled(true); store.setSoundEnabled(false)
        store.settingsFlow.first().let { assertTrue(it.hapticsEnabled); assertFalse(it.soundEnabled) }
        assertEquals(true, SettingsDataStore(ApplicationProvider.getApplicationContext()).settingsFlow.first().hapticsEnabled)
    }

    @Test fun themeDefaultsToOceanMistAndPersists() = runBlocking {
        // New install (nothing saved): Ocean Mist.
        assertEquals(KeyboardThemePreset.OCEAN_MIST, store.settingsFlow.first().themePreset)
        for (preset in KeyboardThemePreset.entries.filter { it != KeyboardThemePreset.CUSTOM }) {
            store.setThemePreset(preset)
            assertEquals(preset, SettingsDataStore(ApplicationProvider.getApplicationContext()).settingsFlow.first().themePreset)
        }
        store.setThemePreset(KeyboardThemePreset.OCEAN_MIST)
        assertEquals(KeyboardThemePreset.OCEAN_MIST, SettingsDataStore(ApplicationProvider.getApplicationContext()).settingsFlow.first().themePreset)
        store.setCustomAccent(0xFF123456.toInt())
        store.settingsFlow.first().let {
            assertEquals(KeyboardThemePreset.CUSTOM, it.themePreset)
            assertEquals(0xFF123456.toInt(), it.customAccentArgb)
        }
    }

    @Test fun anExistingSavedThemeIsNeverReplacedByTheDefault() = runBlocking {
        // A user who chose Golden Dune (or any theme) keeps it after an update; the default applies only when nothing is saved.
        store.setThemePreset(KeyboardThemePreset.GOLDEN_DUNE)
        assertEquals(KeyboardThemePreset.GOLDEN_DUNE, SettingsDataStore(ApplicationProvider.getApplicationContext()).settingsFlow.first().themePreset)
        store.setThemePreset(KeyboardThemePreset.SUNSET_BLOOM)
        assertEquals(KeyboardThemePreset.SUNSET_BLOOM, SettingsDataStore(ApplicationProvider.getApplicationContext()).settingsFlow.first().themePreset)
        store.setThemePreset(KeyboardThemePreset.DEFAULT)
    }
}
