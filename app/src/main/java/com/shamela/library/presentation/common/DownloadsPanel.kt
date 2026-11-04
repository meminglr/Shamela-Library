@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.shamela.library.presentation.common

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.IconButton
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
import com.shamela.library.domain.model.DownloadStatus
import com.shamela.library.presentation.ShamelaVerifyActivity
import com.shamela.library.presentation.screens.library.isolate
import com.shamela.library.presentation.utils.BooksDownloadManager
import com.shamela.library.presentation.utils.DownloadItem

/**
 * A compact bar shown above the navigation bar on every screen while books download (or after
 * some failed): overall progress, what is happening, and a tap opens the downloads panel.
 */
@Composable
fun DownloadsBar(modifier: Modifier = Modifier) {
    val items by BooksDownloadManager.downloads.collectAsStateWithLifecycle()
    var showPanel by remember { mutableStateOf(false) }
    // While the bar animates out the list is already empty: keep showing the last entries.
    var lastItems by remember { mutableStateOf(items) }
    if (items.isNotEmpty()) lastItems = items

    AnimatedVisibility(
        visible = items.isNotEmpty(),
        enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it } +
            fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
        exit = slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it } +
            fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        modifier = modifier,
    ) {
        val shown = items.ifEmpty { lastItems }
        if (shown.isEmpty()) return@AnimatedVisibility
        val active = shown.filter { it.status.isActive }
        val failed = shown.count { it.status is DownloadStatus.Failed }
        val running = active.firstOrNull { it.status is DownloadStatus.Downloading }
        val allWaiting = active.isNotEmpty() && active.none { it.status is DownloadStatus.Downloading }
            && active.any { it.status is DownloadStatus.Waiting }

        Surface(
            onClick = { showPanel = true },
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                    when {
                        active.isEmpty() -> Icon(ShamelaIcons.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                        allWaiting -> Icon(ShamelaIcons.CloudOff, null)
                        else -> {
                            val percent = overallProgress(active)
                            if (percent != null) CircularProgressIndicator(
                                progress = { percent },
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 3.dp,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                strokeCap = StrokeCap.Round,
                            ) else CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = when {
                            active.isEmpty() -> stringResource(R.string.dl_bar_failed, failed)
                            allWaiting -> stringResource(R.string.dl_bar_waiting, active.size)
                            else -> stringResource(R.string.dl_bar_active, active.size)
                        },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                    )
                    val detail = running ?: active.firstOrNull() ?: shown.first()
                    Text(
                        // Status first, so a long title is what gets cut; the title isolated, so an
                        // Arabic one doesn't reorder the Turkish status around it.
                        text = listOfNotNull(downloadStatusText(detail.status), isolate(detail.title)).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    if (showPanel) DownloadsSheet(onDismiss = { showPanel = false })
}

/** Overall progress of the active downloads, or null when no size is known yet. */
private fun overallProgress(active: List<DownloadItem>): Float? {
    val known = active.mapNotNull { item ->
        when (val s = item.status) {
            is DownloadStatus.Downloading -> if (s.total > 0) s.progress else null
            is DownloadStatus.Waiting -> if (s.total > 0) ((s.bytes * 100) / s.total).toInt() else null
            else -> 0
        }
    }
    if (known.isEmpty() || active.none { (it.status as? DownloadStatus.Downloading)?.total ?: 0 > 0 }) return null
    return known.sum() / (active.size * 100f)
}

/** Every download with its live state, and actions for one or all of them. */
@Composable
fun DownloadsSheet(onDismiss: () -> Unit) {
    val items by BooksDownloadManager.downloads.collectAsStateWithLifecycle()
    // Close once everything is finished, cancelled or cleared.
    var hadItems by remember { mutableStateOf(items.isNotEmpty()) }
    LaunchedEffect(items.isEmpty()) {
        if (items.isNotEmpty()) hadItems = true
        else if (hadItems) onDismiss()
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Text(
                text = stringResource(R.string.dl_panel_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            if (items.isEmpty()) {
                Text(
                    text = stringResource(R.string.dl_panel_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
                return@Column
            }
            val hasActive = items.any { it.status.isActive }
            val hasFailed = items.any { it.status is DownloadStatus.Failed }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                if (hasFailed) {
                    FilledTonalButton(onClick = { BooksDownloadManager.retryFailed() }, shapes = ButtonDefaults.shapes()) {
                        Icon(ShamelaIcons.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.dl_retry_failed))
                    }
                    OutlinedButton(onClick = { BooksDownloadManager.clearFailed() }, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.dl_clear_failed))
                    }
                }
                if (hasActive) {
                    OutlinedButton(
                        onClick = { BooksDownloadManager.cancelAll() },
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(ShamelaIcons.Close, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.dl_cancel_all))
                    }
                }
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(SegmentGap),
                modifier = Modifier.heightIn(max = 520.dp)
            ) {
                items(items, key = { it.bookId }) { item ->
                    DownloadRow(item, Modifier.animateItem())
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(item: DownloadItem, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = AppFonts.content(MaterialTheme.typography.titleSmall),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = sectionDisplayName(item.categoryName),
                    style = AppFonts.content(MaterialTheme.typography.bodySmall),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                DownloadStatusLine(item.status)
            }
            val context = androidx.compose.ui.platform.LocalContext.current
            when {
                (item.status as? DownloadStatus.Failed)?.reason == BooksDownloadManager.REASON_SHAMELA_VERIFY ->
                    androidx.compose.material3.TextButton(onClick = { ShamelaVerifyActivity.open(context) }) {
                        Text(stringResource(R.string.shamela_verify_action))
                    }
                item.status is DownloadStatus.Failed || item.status is DownloadStatus.Waiting ->
                    IconButton(onClick = { BooksDownloadManager.retry(item.bookId) }) {
                        Icon(ShamelaIcons.Refresh, stringResource(R.string.dl_retry_now), tint = MaterialTheme.colorScheme.primary)
                    }
                else -> Unit
            }
            if (item.status.isActive) {
                IconButton(onClick = { BooksDownloadManager.cancelDownload(item.bookId) }) {
                    Icon(ShamelaIcons.Close, stringResource(R.string.dl_cancel))
                }
            }
        }
    }
}
