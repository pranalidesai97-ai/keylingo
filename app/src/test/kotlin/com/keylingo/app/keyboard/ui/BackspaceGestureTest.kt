package com.keylingo.app.keyboard.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.keylingo.app.keyboard.KeyAction
import com.keylingo.app.keyboard.ShiftState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real Compose pointer input on Robolectric, with a virtual clock. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackspaceGestureTest {
    @get:Rule val compose = createComposeRule()

    private var deletes = 0
    private var vibrations = 0

    private fun setUp() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Row(Modifier.width(120.dp).height(60.dp)) {
                KeyboardKey(
                    action = KeyAction.Backspace,
                    shiftState = ShiftState.OFF,
                    onClick = { deletes++ },
                    modifier = Modifier.weight(1f).testTag("backspace"),
                    feedback = KeyFeedback({ vibrations++ }, {})
                )
            }
        }
    }

    @Test fun tapDeletesExactlyOnce() {
        setUp()
        compose.onNodeWithTag("backspace").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("backspace").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(2_000)
        assertEquals(1, deletes)
        assertEquals(0, vibrations) // default: no vibration
    }

    @Test fun holdRepeatsAndReleaseStopsImmediately() {
        setUp()
        compose.onNodeWithTag("backspace").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(300)
        assertEquals("only the immediate delete before the repeat delay", 1, deletes)
        compose.mainClock.advanceTimeBy(1_500)
        val whileHeld = deletes
        assertTrue("expected auto-repeat, got $whileHeld", whileHeld >= 20)
        compose.onNodeWithTag("backspace").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(2_000)
        assertEquals("no deletion after release", whileHeld, deletes)
    }
}
