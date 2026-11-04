package com.shamela.apptheme.presentation.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.ExitTransition
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shamela.apptheme.presentation.theme.AppTheme

/** A centered, standard-size progress indicator over the screen background. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadingScreen(
    visibility: Boolean,
    /** Behind the indicator: the screen's own background (e.g. the reader's page color). */
    background: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surface,
) {
    // Only for loads that take a while: a quick one (most local data) shows no indicator at all
    // instead of one that flashes for a moment.
    var longLoad by remember { mutableStateOf(false) }
    LaunchedEffect(visibility) {
        longLoad = false
        if (visibility) {
            delay(LoadingIndicatorDelayMillis)
            longLoad = true
        }
    }
    AnimatedVisibility(
        visible = visibility && longLoad,
        enter = fadeIn(),
        exit = ExitTransition.None,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .clickable(enabled = false) {},
            contentAlignment = Alignment.Center
        ) {
            // M3 Expressive shape-morphing loading indicator.
            CircularProgressIndicator()
        }
    }
}

/**
 * A progress indicator that appears only if it is still shown after a moment: a quick load (most
 * local data) shows nothing instead of a spinner that flashes for a frame or two.
 */
@Composable
fun DelayedProgressIndicator(modifier: Modifier = Modifier) {
    var longLoad by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(LoadingIndicatorDelayMillis)
        longLoad = true
    }
    if (longLoad) CircularProgressIndicator(modifier)
}

/** How long a load runs before the indicator appears. */
private const val LoadingIndicatorDelayMillis = 300L

@Preview
@Composable
private fun LoadingScreenPrev() {
    AppTheme.ShamelaLibraryTheme {
        LoadingScreen(true)
    }
}