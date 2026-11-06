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
}
