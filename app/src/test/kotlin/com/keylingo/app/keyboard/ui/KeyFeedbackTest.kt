package com.keylingo.app.keyboard.ui

import com.keylingo.app.data.settings.KeyLingoSettings
import com.keylingo.app.keyboard.KeyAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class KeyFeedbackTest {
    private val everyKey = listOf(
        KeyAction.Character("a", "A"), KeyAction.Symbol("1"), KeyAction.Symbol("."), KeyAction.Symbol("😀"),
        KeyAction.Space, KeyAction.Backspace, KeyAction.Enter, KeyAction.Shift, KeyAction.SwitchToSymbols,
        KeyAction.SwitchToLetters, KeyAction.SwitchToNumbers, KeyAction.Emoji,
    )

    @Test fun vibrationAndSoundAreOffByDefault() {
        val defaults = KeyLingoSettings()
        assertFalse(defaults.hapticsEnabled)
        assertFalse(defaults.soundEnabled)
    }

    @Test fun noKeyVibratesOrClicksWithDefaultSettings() {
        var vibrations = 0; var sounds = 0
        val feedback = KeyFeedback({ vibrations++ }, { sounds++ })
        everyKey.forEach(feedback::onKeyDown)
        assertEquals(0, vibrations); assertEquals(0, sounds)
    }

    @Test fun vibrationAndSoundAreIndependent() {
        var vibrations = 0; var sounds = 0
        val feedback = KeyFeedback({ vibrations++ }, { sounds++ })
        feedback.soundEnabled = true
        everyKey.forEach(feedback::onKeyDown)
        assertEquals(0, vibrations); assertEquals(everyKey.size, sounds)
        sounds = 0; feedback.soundEnabled = false; feedback.hapticsEnabled = true
        everyKey.forEach(feedback::onKeyDown)
        assertEquals(everyKey.size, vibrations); assertEquals(0, sounds)
    }
}
