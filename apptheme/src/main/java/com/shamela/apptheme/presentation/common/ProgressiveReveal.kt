package com.shamela.apptheme.presentation.common

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A page's rows, built a few per frame as the page appears instead of all in its first frame.
 * Building the ~10 rows a list shows took ~90 ms on a phone, a visible stall in the middle of the
 * page transition; a row or two per frame fits within each frame, and the rows fill in top to
 * bottom while the page slides in. Rows register in the order they are first built, which in a
 * list is top to bottom.
 */
class RevealState {
    private var registered = 0
    var revealed by mutableIntStateOf(0)
        internal set
    /** Everything on screen is built: rows built from now on (scrolling, new data) appear at once. */
    var done by mutableStateOf(false)
        internal set

    internal fun register() = registered++
    internal val registeredCount get() = registered
}

val LocalRevealState = compositionLocalOf<RevealState?> { null }

/** Lets the rows (see [Revealed]) of [content] appear a few per frame. */
@Composable
fun ProgressiveReveal(content: @Composable () -> Unit) {
    val state = remember { RevealState() }
    LaunchedEffect(state) {
        var frames = 0
        while (!state.done) {
            withFrameNanos { }
            frames++
            state.revealed += RowsPerFrame
            // Wait a few frames for late rows (the list's data may arrive a moment after the page).
            if (frames >= MinFrames && state.revealed >= state.registeredCount) state.done = true
        }
    }
    CompositionLocalProvider(LocalRevealState provides state, content = content)
}

/**
 * A list row: built once its turn comes (see [ProgressiveReveal]); until then an empty space of
 * about its height. Outside a [ProgressiveReveal] it is built at once.
 */
@Composable
fun Revealed(placeholderHeight: Dp = 76.dp, content: @Composable () -> Unit) {
    val state = LocalRevealState.current
    val order = remember(state) { if (state == null || state.done) -1 else state.register() }
    // One call site for the content, so a row built early isn't rebuilt when the reveal finishes.
    if (state == null || order < 0 || state.done || order < state.revealed) content()
    else Spacer(Modifier.fillMaxWidth().height(placeholderHeight))
}

/** Rows built per frame: one costs ~8 ms on a mid-range phone, within a 120 Hz frame. */
private const val RowsPerFrame = 1
private const val MinFrames = 12
