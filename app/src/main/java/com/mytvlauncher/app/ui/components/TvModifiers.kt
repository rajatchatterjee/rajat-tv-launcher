package com.mytvlauncher.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Click handling for a TV remote.
 *
 * - OK / Enter press-and-release → [onClick]
 * - OK held down (the remote starts sending key repeats) → [onLongClick], once
 * - Menu key → [onLongClick] (handy on remotes that have one)
 *
 * The release only counts as a click if this element also saw the press.
 * That matters: a long-press here opens the app-options dialog, and the
 * OK *release* is then delivered to the dialog — without this guard the
 * dialog's first button would fire instantly.
 *
 * Touch/mouse still work through the regular clickable underneath, which
 * also makes the element focusable for the D-pad.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.tvClickable(
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier = composed {
    val pressState = remember { PressState() }
    val interaction = remember { MutableInteractionSource() }
    this
        .onPreviewKeyEvent { event ->
            if (!enabled) return@onPreviewKeyEvent false
            val isSelect = event.key == Key.DirectionCenter || event.key == Key.Enter ||
                event.key == Key.NumPadEnter
            when {
                event.key == Key.Menu && onLongClick != null -> {
                    if (event.type == KeyEventType.KeyUp) onLongClick()
                    true
                }
                !isSelect -> false
                event.type == KeyEventType.KeyDown -> {
                    if (event.nativeKeyEvent.repeatCount == 0) {
                        pressState.down = true
                        pressState.longFired = false
                    } else if (pressState.down && !pressState.longFired && onLongClick != null) {
                        pressState.longFired = true
                        onLongClick()
                    }
                    true
                }
                event.type == KeyEventType.KeyUp -> {
                    val wasClick = pressState.down && !pressState.longFired
                    pressState.down = false
                    pressState.longFired = false
                    if (wasClick) onClick()
                    true
                }
                else -> false
            }
        }
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onLongClick = onLongClick,
            onClick = onClick
        )
}

private class PressState {
    var down = false
    var longFired = false
}

/** requestFocus() throws if the node isn't attached yet; on TV that's never worth a crash. */
fun FocusRequester.safeRequestFocus(): Boolean = try {
    requestFocus()
    true
} catch (e: IllegalStateException) {
    false
}

/**
 * Lazy rows/grids compose their items during layout, which can land a
 * frame after a LaunchedEffect starts — so retry briefly before giving up.
 */
suspend fun FocusRequester.requestFocusWhenReady(attempts: Int = 10) {
    repeat(attempts) {
        if (safeRequestFocus()) return
        kotlinx.coroutines.delay(50)
    }
}
