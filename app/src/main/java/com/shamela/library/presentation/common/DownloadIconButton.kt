package com.shamela.library.presentation.common

import android.text.format.Formatter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shamela.apptheme.presentation.theme.ShamelaIcons
import com.shamela.library.R
import com.shamela.library.domain.model.DownloadStatus
import com.shamela.library.presentation.utils.BooksDownloadManager

/** The download state of [bookId]: downloaded books win over any (stale) job status. */
fun downloadStatusOf(
    bookId: String,
    downloadStatuses: Map<String, DownloadStatus>,
    downloadedBookIds: Set<String>,
): DownloadStatus = when {
    downloadedBookIds.contains(bookId) -> DownloadStatus.Downloaded
    else -> downloadStatuses[bookId] ?: DownloadStatus.NotDownloaded
}

/**
 * Download button with live state: a progress ring with a stop mark while downloading (tap to
 * cancel), a spinner while queued or connecting, a cloud-off mark while waiting for the network,
 * a retry mark after a failure, and a check once downloaded.
 */
@Composable
fun DownloadIconButton(
    /** This row's status only (see [downloadStatusOf]): passing the whole status map made every
     *  visible row recompose on each progress tick of any download. */
    status: DownloadStatus,
    onDownloadClick: () -> Unit,
    onCancelClick: () -> Unit = {},
    onRetryClick: () -> Unit = onDownloadClick,
) {
    when (status) {
        DownloadStatus.NotDownloaded -> IconButton(onClick = onDownloadClick) {
            Icon(
                imageVector = ShamelaIcons.FileDownload,
                contentDescription = stringResource(R.string.download),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        DownloadStatus.Queued -> IconButton(onClick = onCancelClick) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                Icon(ShamelaIcons.Close, contentDescription = stringResource(R.string.dl_cancel), modifier = Modifier.size(14.dp))
            }
        }

        is DownloadStatus.Downloading -> IconButton(onClick = onCancelClick) {
            Box(contentAlignment = Alignment.Center) {
                if (status.total > 0) {
                    CircularProgressIndicator(
                        progress = { status.progress / 100f },
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.5.dp,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        strokeCap = StrokeCap.Round,
                    )
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                }
                Icon(ShamelaIcons.Close, contentDescription = stringResource(R.string.dl_cancel), modifier = Modifier.size(14.dp))
            }
        }

        is DownloadStatus.Waiting -> IconButton(onClick = onRetryClick) {
            Icon(
                imageVector = if (status.noNetwork) ShamelaIcons.CloudOff else ShamelaIcons.Schedule,
                contentDescription = stringResource(R.string.dl_retry_now),
                tint = MaterialTheme.colorScheme.tertiary,
            )
        }

        is DownloadStatus.Failed -> IconButton(onClick = onRetryClick) {
            Icon(
                imageVector = ShamelaIcons.Refresh,
                contentDescription = stringResource(R.string.dl_retry),
                tint = MaterialTheme.colorScheme.error,
            )
        }

        DownloadStatus.Downloaded -> IconButton(onClick = {}) {
            Icon(
                imageVector = ShamelaIcons.CheckCircle,
                contentDescription = stringResource(R.string.downloaded),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** A one-line description of a download: "45% · 1.2 MB / 2.6 MB · 300 kB/s", "Queued", ... */
@Composable
fun downloadStatusText(status: DownloadStatus): String? {
    val context = LocalContext.current
    // Isolated left-to-right, so "1.2 MB / 2.6 MB" keeps its order inside Arabic text.
    fun size(bytes: Long) = "\u2066" + Formatter.formatShortFileSize(context, bytes) + "\u2069"
    return when (status) {
        DownloadStatus.Queued -> stringResource(R.string.dl_queued)
        is DownloadStatus.Downloading -> when {
            status.unitIsPages && status.total > 0 ->
                stringResource(R.string.dl_progress_pages, status.progress, status.bytes, status.total)
            status.unitIsPages -> stringResource(R.string.dl_shamela_preparing)
            status.total > 0 -> buildString {
                append(stringResource(R.string.dl_progress, status.progress, size(status.bytes), size(status.total)))
                if (status.bytesPerSecond > 0) {
                    append(" · ").append("\u2068").append(stringResource(R.string.dl_speed, size(status.bytesPerSecond))).append("\u2069")
                }
            }
            status.bytes > 0 -> stringResource(R.string.dl_progress_unknown, size(status.bytes))
            else -> stringResource(R.string.dl_connecting)
        }
        is DownloadStatus.Waiting ->
            stringResource(if (status.noNetwork) R.string.dl_waiting_network else R.string.dl_waiting_retry)
        is DownloadStatus.Failed -> when (status.reason) {
            BooksDownloadManager.REASON_NOT_FOUND -> stringResource(R.string.dl_failed_not_found)
            BooksDownloadManager.REASON_NO_SPACE -> stringResource(R.string.dl_failed_no_space)
            BooksDownloadManager.REASON_STORAGE -> stringResource(R.string.dl_failed_storage)
            BooksDownloadManager.REASON_CORRUPT -> stringResource(R.string.dl_failed_corrupt)
            BooksDownloadManager.REASON_SHAMELA_VERIFY -> stringResource(R.string.dl_failed_shamela_verify)
            else -> stringResource(R.string.dl_failed)
        }
        else -> null
    }
}

/** Progress bar and status text under a book row while it downloads (or after it failed). */
@Composable
fun DownloadStatusLine(status: DownloadStatus, modifier: Modifier = Modifier) {
    val text = downloadStatusText(status) ?: return
    Column(modifier.fillMaxWidth().padding(top = 6.dp)) {
        when (status) {
            is DownloadStatus.Downloading ->
                if (status.total > 0) LinearProgressIndicator(
                    progress = { status.progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    drawStopIndicator = {},
                )
                else LinearProgressIndicator(Modifier.fillMaxWidth())
            DownloadStatus.Queued -> LinearProgressIndicator(Modifier.fillMaxWidth())
            is DownloadStatus.Waiting -> if (status.total > 0) LinearProgressIndicator(
                progress = { (status.bytes.toFloat() / status.total).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.tertiary,
                drawStopIndicator = {},
            )
            else -> Unit
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = when (status) {
                is DownloadStatus.Failed -> MaterialTheme.colorScheme.error
                is DownloadStatus.Waiting -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
