package com.keylingo.app.keyboard.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardCapslock
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keylingo.app.keyboard.EnterKeyAction
import com.keylingo.app.keyboard.KeyAction
import com.keylingo.app.keyboard.KeyboardLayoutProvider
import com.keylingo.app.keyboard.ShiftState
import com.keylingo.app.ui.theme.LocalKeyboardColors
import com.keylingo.app.ui.theme.toBrush

/** Long-press-to-repeat tuning for Backspace. */
private const val BACKSPACE_INITIAL_DELAY_MS = 350L
private const val BACKSPACE_REPEAT_INTERVAL_MS = 55L
private const val BACKSPACE_ACCELERATED_INTERVAL_MS = 35L
private const val BACKSPACE_ACCELERATE_AFTER_REPEATS = 15
/** How long a key must be held before its accent alternatives appear. */
private const val ALTERNATES_DELAY_MS = 320L

/** Callbacks a key uses to drive the screen-level accent popup overlay. */
class AccentPopupController(
    val show: (options: List<String>, anchor: Rect) -> Unit,
    val select: (index: Int) -> Unit,
    val dismiss: () -> Unit
)

/**
 * A single key. Width comes from the caller's [RowScope.weight]. Letters
 * and symbols commit on release (like Gboard); holding a key with
 * alternatives opens the accent popup -- slide to choose, release to type.
 * Backspace deletes immediately on press and auto-repeats with acceleration.
 */
@Composable
fun RowScope.KeyboardKey(
    action: KeyAction,
    shiftState: ShiftState,
    onClick: (KeyAction) -> Unit,
    modifier: Modifier = Modifier,
    enterAction: EnterKeyAction = EnterKeyAction.NEWLINE,
    spaceLabel: String = "",
    feedback: KeyFeedback? = null,
    accentPopup: AccentPopupController? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    // Visual press feedback (replaces vibration): a slight shrink plus a darker/lighter key face.
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.95f else 1f, animationSpec = tween(60), label = "keyPressScale")
    val currentOnClick by rememberUpdatedState(onClick)
    val currentShift by rememberUpdatedState(shiftState)
    val currentFeedback by rememberUpdatedState(feedback)
    val alternates = remember(action) { KeyboardLayoutProvider.alternatesFor(action) }
    val bounds = remember { arrayOf(Rect.Zero) }

    val palette = LocalKeyboardColors.current
    val isAccentKey = action is KeyAction.Shift || action is KeyAction.Backspace || action is KeyAction.Enter ||
        action is KeyAction.SwitchToSymbols || action is KeyAction.SwitchToLetters || action is KeyAction.SwitchToNumbers
    val enterHighlighted = action is KeyAction.Enter && enterAction != EnterKeyAction.NEWLINE
    val active = (action is KeyAction.Shift && shiftState != ShiftState.OFF) || enterHighlighted
    val faceBrush = remember(palette, active, isAccentKey) {
        when {
            active -> palette.accentGradient
            isAccentKey -> palette.specialKeyGradient
            else -> listOf(palette.key)
        }.toBrush()
    }
    val contentColor = when {
        active -> palette.onAccent
        isAccentKey -> palette.specialKeyText
        else -> palette.keyText
    }
    // Pressed: a 16% tint of the label colour over the face (flat or gradient).
    val pressedOverlay by animateColorAsState(
        targetValue = if (isPressed) contentColor.copy(alpha = 0.16f) else Color.Transparent,
        animationSpec = tween(60), label = "keyPressColor"
    )

    fun withShift(alt: String): KeyAction =
        if (action is KeyAction.Character) KeyAction.Character(alt.lowercase(), alt.uppercase()) else KeyAction.Symbol(alt)

    val gesture = if (action == KeyAction.Backspace) {
        Modifier.pointerInput(action) {
            // One gesture loop, no helper coroutine: each repeat waits for "up" with a timeout, so lifting the
            // finger (or the gesture being cancelled / sliding off the key) ends deletion on that very event.
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val press = PressInteraction.Press(down.position)
                interactionSource.tryEmit(press)
                currentFeedback?.onKeyDown(action)
                currentOnClick(action) // immediate single delete, same as a tap
                var wait = BACKSPACE_INITIAL_DELAY_MS
                var repeats = 0
                while (true) {
                    val outcome = withTimeoutOrNull(wait) {
                        if (waitForUpOrCancellation() != null) GestureEnd.RELEASED else GestureEnd.CANCELLED
                    }
                    if (outcome != null) {
                        interactionSource.tryEmit(if (outcome == GestureEnd.RELEASED) PressInteraction.Release(press) else PressInteraction.Cancel(press))
                        break
                    }
                    currentOnClick(action) // still held: repeat
                    repeats++
                    wait = if (repeats >= BACKSPACE_ACCELERATE_AFTER_REPEATS) BACKSPACE_ACCELERATED_INTERVAL_MS else BACKSPACE_REPEAT_INTERVAL_MS
                }
            }
        }
    } else {
        Modifier.pointerInput(action, alternates) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val press = PressInteraction.Press(down.position)
                interactionSource.tryEmit(press)
                currentFeedback?.onKeyDown(action)
                val quickUp = withTimeoutOrNull(if (alternates.isEmpty()) Long.MAX_VALUE else ALTERNATES_DELAY_MS) { waitForUpOrCancellation() }
                if (quickUp != null || alternates.isEmpty() || accentPopup == null) {
                    // Normal tap (or a cancelled gesture, in which case quickUp is null and nothing is typed).
                    interactionSource.tryEmit(if (quickUp != null) PressInteraction.Release(press) else PressInteraction.Cancel(press))
                    if (quickUp != null) currentOnClick(action)
                    return@awaitEachGesture
                }
                // Long press: show alternatives; horizontal movement selects; release commits.
                val anchor = bounds[0]
                accentPopup.show(alternates, anchor)
                var selected = 0
                var committed = false
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) { committed = true; change.consume(); break }
                    val cell = anchor.width.coerceAtLeast(1f)
                    val index = ((change.position.x - down.position.x) / cell + 0.5f).toInt().coerceIn(0, alternates.size - 1)
                    if (index != selected) { selected = index; accentPopup.select(index) }
                    change.consume()
                }
                accentPopup.dismiss()
                interactionSource.tryEmit(PressInteraction.Release(press))
                if (committed) {
                    val alt = alternates[selected]
                    currentOnClick(withShift(if (currentShift != ShiftState.OFF) alt.uppercase() else alt))
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(3.dp)
            .onGloballyPositioned { bounds[0] = it.boundsInRoot() }
            .scale(scale)
            .background(faceBrush, RoundedCornerShape(8.dp))
            .background(pressedOverlay, RoundedCornerShape(8.dp))
            .then(gesture)
            // TalkBack: announce the key and let a double-tap type it (pointerInput alone exposes no click action).
            .semantics {
                contentDescription = describe(action, shiftState, enterAction)
                role = Role.Button
                onClick { currentOnClick(action); true }
            },
        contentAlignment = Alignment.Center
    ) {
        KeyContent(action, shiftState, contentColor, enterAction, spaceLabel, hasAlternates = alternates.isNotEmpty())
    }
}

private enum class GestureEnd { RELEASED, CANCELLED }

private fun describe(action: KeyAction, shift: ShiftState, enter: EnterKeyAction): String = when (action) {
    is KeyAction.Character -> if (shift != ShiftState.OFF) action.upper else action.lower
    is KeyAction.Symbol -> action.value
    KeyAction.Shift -> when (shift) { ShiftState.CAPS_LOCK -> "Caps lock on"; ShiftState.SHIFTED_ONCE -> "Shift on"; else -> "Shift" }
    KeyAction.Backspace -> "Delete"
    KeyAction.Enter -> enter.name.lowercase().replaceFirstChar { it.uppercase() }.let { if (enter == EnterKeyAction.NEWLINE) "Enter" else it }
    KeyAction.Space -> "Space"
    KeyAction.Emoji -> "Emoji"
    KeyAction.SwitchToSymbols -> "More symbols"
    KeyAction.SwitchToLetters -> "Letters"
    KeyAction.SwitchToNumbers -> "Numbers and symbols"
}

@Composable
private fun KeyContent(action: KeyAction, shiftState: ShiftState, contentColor: Color, enterAction: EnterKeyAction, spaceLabel: String, hasAlternates: Boolean) {
    when (action) {
        is KeyAction.Character -> Box(contentAlignment = Alignment.Center) {
            Text(text = if (shiftState != ShiftState.OFF) action.upper else action.lower, color = contentColor, fontSize = 20.sp)
            if (hasAlternates) Text("·", color = contentColor.copy(alpha = 0.35f), fontSize = 10.sp, modifier = Modifier.align(Alignment.TopEnd).padding(end = 2.dp))
        }
        is KeyAction.Symbol -> Text(text = action.value, color = contentColor, fontSize = 18.sp)
        KeyAction.Shift -> Icon(
            imageVector = if (shiftState == ShiftState.CAPS_LOCK) Icons.Filled.KeyboardCapslock else Icons.Filled.ArrowUpward,
            contentDescription = null, tint = contentColor
        )
        KeyAction.Backspace -> Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = null, tint = contentColor)
        KeyAction.Enter -> Icon(
            imageVector = when (enterAction) {
                EnterKeyAction.SEND -> Icons.AutoMirrored.Filled.Send
                EnterKeyAction.SEARCH -> Icons.Filled.Search
                EnterKeyAction.GO, EnterKeyAction.NEXT -> Icons.AutoMirrored.Filled.ArrowForward
                EnterKeyAction.DONE -> Icons.Filled.Check
                EnterKeyAction.PREVIOUS -> Icons.AutoMirrored.Filled.ArrowBack
                EnterKeyAction.NEWLINE -> Icons.AutoMirrored.Filled.KeyboardReturn
            },
            contentDescription = null, tint = contentColor
        )
        KeyAction.Space -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = spaceLabel, color = contentColor.copy(alpha = 0.55f), fontSize = 12.sp)
        }
        KeyAction.Emoji -> Icon(Icons.Filled.Mood, contentDescription = null, tint = contentColor)
        KeyAction.SwitchToSymbols -> Text("#+=", color = contentColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        KeyAction.SwitchToLetters -> Text("ABC", color = contentColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        KeyAction.SwitchToNumbers -> Text("?123", color = contentColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
