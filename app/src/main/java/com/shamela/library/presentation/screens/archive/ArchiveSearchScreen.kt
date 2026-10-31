package com.shamela.library.presentation.screens.archive

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shamela.apptheme.presentation.common.EmptyListScreen
import com.shamela.apptheme.presentation.common.LoadingScreen
import com.shamela.apptheme.presentation.common.SearchTopBar
import com.shamela.apptheme.presentation.theme.AppFonts
import com.shamela.apptheme.presentation.theme.ShamelaIcons
import com.shamela.library.R
import com.shamela.library.data.remote.archive.ArchiveItem
import com.shamela.library.domain.model.DownloadStatus
import com.shamela.library.presentation.common.DownloadIconButton
import com.shamela.library.presentation.common.ListLeadingTile
import com.shamela.library.presentation.common.downloadStatusOf
import com.shamela.library.presentation.screens.LocalFloatingBarInset
import com.shamela.library.presentation.screens.LocalPaddingValues

/**
 * Kütüphane 4: scanned books on archive.org. A result opens to its volumes (PDFs), each downloaded
 * into the scanned-books section of the library and read in the PDF reader.
 */
@Composable
fun ArchiveSearchScreen(
    viewModel: ArchiveSearchViewModel = hiltViewModel(),
    navigateBack: () -> Unit,
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val focusRequester = remember { FocusRequester() }
    Column(Modifier.fillMaxSize().padding(LocalPaddingValues.current)) {
        SearchTopBar(
            onNavigateBack = navigateBack,
            hint = stringResource(R.string.archive_search_hint),
            focusRequester = focusRequester,
            value = state.query,
            onValueChanged = viewModel::onQueryChanged,
            onClickClear = { viewModel.onQueryChanged("") },
            onClickSearch = { viewModel.search() },
            windowInsets = WindowInsets(0),
        )
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp + LocalFloatingBarInset.current),
            ) {
                item(key = "about") {
                    Text(
                        text = stringResource(if (state.failed) R.string.archive_failed else R.string.archive_about),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    )
                }
                items(state.results.orEmpty(), key = { it.id }) { item ->
                    ArchiveResult(item, state, viewModel)
                }
            }
            EmptyListScreen(visibility = state.results?.isEmpty() == true && !state.searching, text = stringResource(R.string.no_results))
            LoadingScreen(visibility = state.searching)
        }
    }
    DisposableEffect(Unit) {
        if (state.query.isBlank()) focusRequester.requestFocus()
        onDispose { }
    }
}

@Composable
private fun ArchiveResult(item: ArchiveItem, state: ArchiveSearchViewModel.State, viewModel: ArchiveSearchViewModel) {
    val open = state.openId == item.id
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (open) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer)
            .clickable { viewModel.toggle(item) }
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ListLeadingTile(
                ShamelaIcons.MenuBook,
                container = MaterialTheme.colorScheme.tertiaryContainer,
                content = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = AppFonts.content(MaterialTheme.typography.titleMedium),
                    maxLines = if (open) 6 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = listOf(item.creator, item.year).filter { it.isNotBlank() }.joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (open) {
            Spacer(Modifier.height(8.dp))
            val volumes = state.volumes[item.id]
            when {
                volumes == null && state.loadingVolumes ->
                    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                volumes == null -> Unit
                volumes.isEmpty() -> Text(
                    text = stringResource(R.string.archive_no_pdf),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                )
                else -> {
                    if (volumes.size > 1) {
                        FilledTonalButton(onClick = { viewModel.downloadAll(item) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.archive_download_all, volumes.size, megabytes(volumes.sumOf { it.size })))
                        }
                    }
                    volumes.forEach { volume ->
                        val book = viewModel.bookOf(item, volume)
                        val status = downloadStatusOf(book.id, state.downloadStatuses, state.downloadedIds)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = status == DownloadStatus.Downloaded) { viewModel.open(book) }
                                .padding(start = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = if (volumes.size > 1) stringResource(R.string.archive_volume, volume.number)
                                    else stringResource(R.string.archive_single_volume),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = if (status == DownloadStatus.Downloaded) stringResource(R.string.archive_tap_to_read)
                                    else megabytes(volume.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            DownloadIconButton(
                                status = status,
                                onDownloadClick = { viewModel.download(item, volume) },
                                onCancelClick = { viewModel.cancel(book) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun megabytes(bytes: Long): String =
    if (bytes <= 0) "—" else "%.0f MB".format(java.util.Locale.US, bytes / 1_000_000.0).let { if (it == "0 MB") "<1 MB" else it }
