package com.keylingo.app.keyboard.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.scale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.KeyboardReturn
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keylingo.app.keyboard.KeyAction
import com.keylingo.app.keyboard.ShiftState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Long-press-to-repeat tuning for Backspace, matching Gboard's feel. */
private const val BACKSPACE_INITIAL_DELAY_MS = 350L
private const val BACKSPACE_REPEAT_INTERVAL_MS = 55L
private const val BACKSPACE_ACCELERATED_INTERVAL_MS = 40L
private const val BACKSPACE_ACCELERATE_AFTER_REPEATS = 15

/**
 * A single key. Weighted width is applied by the caller (the row Composable)
 * via [RowScope.weight]; this Composable only cares about its own content
 * and press feedback.
 */
@Composable
fun RowScope.KeyboardKey(
    action: KeyAction,
    weight: Float,
    shiftState: ShiftState,
    onClick: (KeyAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = tween(80),
        label = "keyPressScale"
    )

    val isAccentKey = action is KeyAction.Shift || action is KeyAction.Backspace ||
        action is KeyAction.Enter || action is KeyAction.SwitchToSymbols ||
        action is KeyAction.SwitchToLetters || action is KeyAction.SwitchToNumbers

    val background = when {
        action is KeyAction.Shift && shiftState != ShiftState.OFF -> MaterialTheme.colorScheme.primary
        isAccentKey -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }
    val contentColor = if (action is KeyAction.Shift && shiftState != ShiftState.OFF) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    // Backspace gets a custom gesture handler (tap = delete one, hold =
    // repeat with acceleration) instead of plain .clickable, so it can
    // drive repeated onClick(action) calls itself while the pointer stays
    // down. Every other key keeps the original .clickable behavior
    // unchanged -- this is intentionally scoped to Backspace only, not a
    // general press-handling rewrite. Press/Release/Cancel interactions
    // are emitted manually here so the existing scale animation above
    // (which reads interactionSource) keeps working identically for this
    // key too.
    val pressModifier = if (action == KeyAction.Backspace) {
        Modifier.pointerInput(action) {
            // AwaitPointerEventScope (the receiver inside awaitPointerEventScope{})
            // is @RestrictsSuspension: only suspend functions that are members/
            // extensions of that exact scope (awaitFirstDown, waitForUpOrCancellation)
            // may be called inside it -- not coroutineScope/launch/delay/emit. So
            // those two calls are each isolated in their own small
            // awaitPointerEventScope{} block, called from this outer,
            // unrestricted coroutineScope -- which is where the repeat-loop and
            // interaction emissions actually run. The outer while(true) gives the
            // same "keep detecting new gestures forever" behavior awaitEachGesture
            // provided.
            coroutineScope {
                while (true) {
                    val down = awaitPointerEventScope { awaitFirstDown() }
                    val press = PressInteraction.Press(down.position)
                    interactionSource.tryEmit(press)

                    onClick(action) // immediate single delete on initial press, same as a plain tap

                    val repeatJob = launch {
                        delay(BACKSPACE_INITIAL_DELAY_MS)
                        var intervalMs = BACKSPACE_REPEAT_INTERVAL_MS
                        var repeats = 0
                        while (isActive) {
                            onClick(action)
                            repeats++
                            if (repeats == BACKSPACE_ACCELERATE_AFTER_REPEATS) {
                                intervalMs = BACKSPACE_ACCELERATED_INTERVAL_MS
                            }
                            delay(intervalMs)
                        }
                    }

                    val up = awaitPointerEventScope { waitForUpOrCancellation() }
                    repeatJob.cancel() // stop immediately when released (or gesture cancelled)
                    interactionSource.tryEmit(
                        if (up != null) PressInteraction.Release(press) else PressInteraction.Cancel(press)
                    )
                }
            }
        }
    } else {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = { onClick(action) }
        )
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(3.dp)
            .scale(scale)
            .background(background, RoundedCornerShape(8.dp))
            .then(pressModifier)
            .wrapContentSize(Alignment.Center),
        contentAlignment = Alignment.Center
    ) {
        KeyContent(action = action, shiftState = shiftState, contentColor = contentColor)
    }
}

@Composable
private fun KeyContent(action: KeyAction, shiftState: ShiftState, contentColor: Color) {
    when (action) {
        is KeyAction.Character -> {
            val label = if (shiftState != ShiftState.OFF) action.upper else action.lower
            Text(text = label, color = contentColor, fontSize = 20.sp)
        }
        is KeyAction.Symbol -> {
            Text(text = action.value, color = contentColor, fontSize = 18.sp)
        }
        KeyAction.Shift -> Icon(
            imageVector = Icons.Filled.ArrowUpward,
            contentDescription = "Shift",
            tint = contentColor
        )
        KeyAction.Backspace -> Icon(
            imageVector = Icons.AutoMirrored.Filled.Backspace,
            contentDescription = "Backspace",
            tint = contentColor
        )
        KeyAction.Enter -> Icon(
            imageVector = Icons.Filled.KeyboardReturn,
            contentDescription = "Enter",
            tint = contentColor
        )
        KeyAction.Space -> Icon(
            imageVector = Icons.Filled.SpaceBar,
            contentDescription = "Space",
            tint = contentColor
        )
        KeyAction.Emoji -> Icon(
            imageVector = Icons.Filled.Mood,
            contentDescription = "Emoji",
            tint = contentColor
        )
        KeyAction.SwitchToSymbols -> Text(text = "#+=", color = contentColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        KeyAction.SwitchToLetters -> Text(text = "ABC", color = contentColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        KeyAction.SwitchToNumbers -> Text(text = "?123", color = contentColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
