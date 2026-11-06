package com.keylingo.app.keyboard.ui

import android.content.Context
import android.media.AudioManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.keylingo.app.keyboard.KeyAction

/**
 * The single place where KeyLingo produces non-visual key feedback.
 * Vibration and sound are independent and each happens only when its own
 * setting is on (both OFF by default). Nothing else in the keyboard calls
 * performHapticFeedback or playSoundEffect -- keep it that way.
 *
 * The platform effects are injected as functions so the decision logic is
 * unit-testable without a device.
 */
class KeyFeedback(
    private val vibrate: () -> Unit,
    private val playSound: (effect: Int) -> Unit
) {
    var hapticsEnabled: Boolean = false
    var soundEnabled: Boolean = false

    /** Called once per key press (never for auto-repeat ticks). */
    fun onKeyDown(action: KeyAction) {
        if (hapticsEnabled) vibrate()
        if (soundEnabled) playSound(soundFor(action))
    }

    companion object {
        fun soundFor(action: KeyAction): Int = when (action) {
            KeyAction.Backspace -> AudioManager.FX_KEYPRESS_DELETE
            KeyAction.Space -> AudioManager.FX_KEYPRESS_SPACEBAR
            KeyAction.Enter -> AudioManager.FX_KEYPRESS_RETURN
            else -> AudioManager.FX_KEYPRESS_STANDARD
        }

        /** Real platform feedback for the IME view. */
        fun forView(view: View, context: Context): KeyFeedback {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            return KeyFeedback(
                // KEYBOARD_TAP respects the system-wide "touch vibration" switch as well as ours.
                vibrate = { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) },
                // A negative volume plays at the user's system sound-effects volume.
                playSound = { effect -> audio?.playSoundEffect(effect, -1f) }
            )
        }
    }
}

@Composable
fun rememberKeyFeedback(hapticsEnabled: Boolean, soundEnabled: Boolean): KeyFeedback {
    val view = LocalView.current
    val context = LocalContext.current
    val feedback = remember(view) { KeyFeedback.forView(view, context) }
    feedback.hapticsEnabled = hapticsEnabled
    feedback.soundEnabled = soundEnabled
    return feedback
}
