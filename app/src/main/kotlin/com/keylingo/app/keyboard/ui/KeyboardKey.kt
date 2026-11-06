package com.keylingo.app.keyboard.ui

import android.view.HapticFeedbackConstants
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keylingo.app.keyboard.EnterKeyAction
import com.keylingo.app.keyboard.KeyAction
import com.keylingo.app.keyboard.KeyboardLayoutProvider
import com.keylingo.app.keyboard.ShiftState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
    hapticsEnabled: Boolean = true,
    accentPopup: AccentPopupController? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.92f else 1f, animationSpec = tween(70), label = "keyPressScale")
    val view = LocalView.current
    val currentOnClick by rememberUpdatedState(onClick)
    val currentShift by rememberUpdatedState(shiftState)
    val haptics by rememberUpdatedState(hapticsEnabled)
    val alternates = remember(action) { KeyboardLayoutProvider.alternatesFor(action) }
    var bounds = remember { arrayOf(Rect.Zero) }

    val colors = MaterialTheme.colorScheme
    val isAccentKey = action is KeyAction.Shift || action is KeyAction.Backspace || action is KeyAction.Enter ||
        action is KeyAction.SwitchToSymbols || action is KeyAction.SwitchToLetters || action is KeyAction.SwitchToNumbers
    val enterHighlighted = action is KeyAction.Enter && enterAction != EnterKeyAction.NEWLINE
    val background = when {
        action is KeyAction.Shift && shiftState != ShiftState.OFF -> colors.primary
        enterHighlighted -> colors.primary
        isAccentKey -> colors.surfaceVariant
        else -> colors.surface
    }
    val contentColor = if ((action is KeyAction.Shift && shiftState != ShiftState.OFF) || enterHighlighted) colors.onPrimary else colors.onSurface

    fun tick() {
        if (haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun withShift(alt: String): KeyAction =
        if (action is KeyAction.Character) KeyAction.Character(alt.lowercase(), alt.uppercase()) else KeyAction.Symbol(alt)

    val gesture = if (action == KeyAction.Backspace) {
        Modifier.pointerInput(action) {
            coroutineScope {
                while (true) {
                    val down = awaitPointerEventScope { awaitFirstDown() }
                    val press = PressInteraction.Press(down.position)
                    interactionSource.tryEmit(press)
                    tick()
                    currentOnClick(action)
                    val repeatJob = launch {
                        delay(BACKSPACE_INITIAL_DELAY_MS)
                        var interval = BACKSPACE_REPEAT_INTERVAL_MS
                        var repeats = 0
                        while (isActive) {
                            currentOnClick(action)
                            if (++repeats == BACKSPACE_ACCELERATE_AFTER_REPEATS) interval = BACKSPACE_ACCELERATED_INTERVAL_MS
                            delay(interval)
                        }
                    }
                    val up = awaitPointerEventScope { waitForUpOrCancellation() }
                    repeatJob.cancel()
                    interactionSource.tryEmit(if (up != null) PressInteraction.Release(press) else PressInteraction.Cancel(press))
                }
            }
        }
    } else {
        Modifier.pointerInput(action, alternates) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val press = PressInteraction.Press(down.position)
                interactionSource.tryEmit(press)
                tick()
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
                    if (index != selected) { selected = index; accentPopup.select(index); tick() }
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
            .background(background, RoundedCornerShape(8.dp))
            .then(gesture)
            .semantics { contentDescription = describe(action, shiftState, enterAction) },
        contentAlignment = Alignment.Center
    ) {
        KeyContent(action, shiftState, contentColor, enterAction, spaceLabel, hasAlternates = alternates.isNotEmpty())
    }
}

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
