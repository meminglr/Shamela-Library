@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.shamela.library.presentation.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shamela.apptheme.presentation.theme.AppFonts
import com.shamela.apptheme.presentation.theme.ShamelaIcons
import com.shamela.library.R
import com.shamela.library.presentation.utils.BookDeletionManager

/**
 * Floating bar shown on every screen while books are deleted, like the downloads bar: how many of
 * how many and which book; then the result for a few seconds. A tap opens [DeletionSheet].
 */
@Composable
fun DeletionBar(modifier: Modifier = Modifier) {
    val state by BookDeletionManager.state.collectAsStateWithLifecycle()
    var showSheet by remember { mutableStateOf(false) }
    // While the bar animates out the state is already reset: keep showing the last one.
    var last by remember { mutableStateOf(state) }
    if (state.visible) last = state

    AnimatedVisibility(
        visible = state.visible,
        enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it } +
            fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
        exit = slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it } +
            fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        modifier = modifier,
    ) {
        val shown = if (state.visible) state else last
        Surface(
            onClick = { showSheet = true },
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                    if (shown.running) CircularProgressIndicator(
                        progress = { if (shown.total > 0) shown.done.toFloat() / shown.total else 0f },
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.error,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        strokeCap = StrokeCap.Round,
                    ) else Icon(if (shown.stopped) ShamelaIcons.Delete else ShamelaIcons.CheckCircle, null)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (shown.running) stringResource(R.string.deleting_books_count, shown.done, shown.total)
                        else stringResource(R.string.books_deleted, shown.deleted),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                    )
                    val detail = if (shown.running) shown.current
                    else if (shown.stopped) stringResource(R.string.deleting_books_stopped)
                    else ""
                    if (detail.isNotEmpty()) Text(
                        text = detail,
                        style = AppFonts.content(MaterialTheme.typography.bodySmall, naturalAlign = true),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    if (showSheet) DeletionSheet(onDismiss = { showSheet = false })
}

/** The running deletion in detail: progress, the book being deleted, what's done, and Stop. */
@Composable
fun DeletionSheet(onDismiss: () -> Unit) {
    val state by BookDeletionManager.state.collectAsStateWithLifecycle()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(
                    when {
                        state.running -> R.string.deleting_books_title
                        state.stopped -> R.string.deleting_books_stopped
                        else -> R.string.deletion_done_title
                    }
                ),
                style = MaterialTheme.typography.titleLarge,
            )
            LinearProgressIndicator(
                progress = { if (state.total > 0) state.done.toFloat() / state.total else 1f },
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = if (state.running) stringResource(R.string.deleting_books_count, state.done, state.total)
                else stringResource(R.string.books_deleted, state.deleted),
                style = MaterialTheme.typography.titleSmall,
            )
            if (state.running && state.current.isNotEmpty()) {
                Text(
                    text = state.current,
                    style = AppFonts.content(MaterialTheme.typography.bodyLarge, naturalAlign = true),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (state.recent.isNotEmpty()) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(SegmentGap),
                    modifier = Modifier.heightIn(max = 360.dp),
                ) {
                    items(state.recent) { title ->
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(ShamelaIcons.Delete, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = title,
                                    style = AppFonts.content(MaterialTheme.typography.bodyMedium, naturalAlign = true),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
            if (state.running) {
                OutlinedButton(
                    onClick = { BookDeletionManager.stop() },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(ShamelaIcons.Close, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.deleting_books_stop))
                }
            } else {
                FilledTonalButton(
                    onClick = { BookDeletionManager.dismiss(); onDismiss() },
                    shapes = ButtonDefaults.shapes(),
                ) { Text(stringResource(R.string.close)) }
            }
        }
    }
}
