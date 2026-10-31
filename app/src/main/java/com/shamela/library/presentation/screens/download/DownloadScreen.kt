@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.shamela.library.presentation.screens.download


import com.shamela.library.presentation.common.downloadStatusOf
import androidx.compose.foundation.layout.Arrangement
import com.shamela.library.presentation.common.SegmentGap
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.res.stringResource
import com.shamela.apptheme.presentation.common.TabbedPager
import android.util.Log
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shamela.apptheme.presentation.theme.ShamelaIcons
import com.shamela.library.R
import com.shamela.library.data.remote.shamela.ShamelaCatalogState
import com.shamela.library.domain.model.Book
import com.shamela.library.presentation.ShamelaVerifyActivity
import com.shamela.library.presentation.common.BookItem
import com.shamela.library.presentation.common.CharacterHeader
import com.shamela.library.presentation.common.DownloadIconButton
import com.shamela.library.presentation.common.groupedSectionItems
import com.shamela.library.presentation.navigation.Download
import com.shamela.library.presentation.screens.LocalPaddingValues
import com.shamela.library.presentation.screens.LocalFloatingBarInset
import com.shamela.library.presentation.screens.library.BooksViewType
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadScreen(
    viewModel: DownloadViewModel = hiltViewModel(),
    navigateToSectionBooksScreen: (categoryName: String, type: String) -> Unit,
    navigateToSearchResultsScreen: (categoryName: String, type: String) -> Unit,
    /** The second argument is the screen `type` the book was listed under ("remote"). */
    navigateToBookDetails: (Book, String) -> Unit,
    navigateToArchiveSearch: () -> Unit = {},
    ) {
    val downloadState = viewModel.downloadState.collectAsStateWithLifecycle().value
    val localPadding = LocalPaddingValues.current
    val type = "remote"
    LaunchedEffect(key1 = Unit, block = {
        Download.buttons.onEach {
            if (it) {
                // Both tabs search sections and books of the merged catalog.
                navigateToSearchResultsScreen("all", type)
            }
        }.launchIn(this)
    })
    Column(Modifier.fillMaxSize().padding(localPadding)) {
    TabbedPager(
        options = BooksViewType.entries,
        selected = downloadState.booksViewType,
        label = { stringResource(it.label) },
            icon = { it.icon },
        onSelect = { viewModel.onEvent(DownloadEvent.OnChangeViewType(it)) },
        modifier = Modifier.fillMaxSize(),
        trailing = {
            CatalogMenu(
                onRefresh = if (downloadState.shamelaCatalog !is ShamelaCatalogState.Loading) {
                    { viewModel.onEvent(DownloadEvent.RefreshShamelaCatalog) }
                } else null,
                onArchiveSearch = navigateToArchiveSearch,
            )
        },
    ) { viewType ->
    val lists = downloadState.lists
    LazyColumn(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SegmentGap),
        contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp + LocalFloatingBarInset.current)
    ) {
        // The shamela.ws part is still coming (or failed): Kütüphane 1 is listed meanwhile.
        when (val catalog = downloadState.shamelaCatalog) {
            is ShamelaCatalogState.Loading -> item(key = "shamela-loading") { CatalogLoading(catalog) }
            is ShamelaCatalogState.Failed -> item(key = "shamela-failed") {
                CatalogFailed(catalog, onRetry = { viewModel.onEvent(DownloadEvent.RefreshShamelaCatalog) })
            }
            else -> Unit
        }

        when (viewType) {
            BooksViewType.Sections -> {
                item(key = "archive") { ArchiveEntry(onClick = navigateToArchiveSearch) }
                groupedSectionItems(lists.sections) {
                    navigateToSectionBooksScreen(it.name, type)
                }
            }

            BooksViewType.Books -> {
                val booksList = lists.groupedBooks
                booksList.forEach { (initial, books) ->
                    stickyHeader {
                        CharacterHeader(
                            modifier = Modifier,
                            char = initial
                        )
                    }
                    itemsIndexed(books, key = { _, it -> it.id }) { index, it ->
                        val rowStatus = downloadStatusOf(it.id, downloadState.downloadStatuses, downloadState.downloadedBookIds)
                        BookItem(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .animateItem(),
                            index = index,
                            count = books.size,
                            onClick = { navigateToBookDetails(it, type) },
                            icon = {
                                DownloadIconButton(
                                    status = rowStatus,
                                    onDownloadClick = {
                                        viewModel.onEvent(DownloadEvent.OnClickDownloadBook(it))
                                    },
                                    onCancelClick = {
                                        viewModel.onEvent(DownloadEvent.OnClickCancelDownload(it.id))
                                    },
                                )
                            },
                            item = it,
                            onInfoClick = { navigateToBookDetails(it, type) },
                            downloadStatus = rowStatus,
                        )
                    }
                }
                if (lists.isLoadingBooks) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
    }
    }

}

/** Overflow menu next to the tabs: fetch the shamela.ws part of the catalog again; scanned books. */
@Composable
private fun CatalogMenu(onRefresh: (() -> Unit)?, onArchiveSearch: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.padding(end = 8.dp)) {
        IconButton(onClick = { expanded = true }) {
            Icon(ShamelaIcons.MoreVert, contentDescription = stringResource(R.string.shamela_refresh_catalog))
        }
        DropdownMenuPopup(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, 1)) {
                DropdownMenuItem(
                    onClick = {
                        expanded = false
                        onRefresh?.invoke()
                    },
                    enabled = onRefresh != null,
                    text = { Text(stringResource(R.string.shamela_refresh_catalog)) },
                    shape = MenuDefaults.itemShape(0, 2).shape,
                    leadingIcon = { Icon(ShamelaIcons.Refresh, contentDescription = null) },
                )
                DropdownMenuItem(
                    onClick = {
                        expanded = false
                        onArchiveSearch()
                    },
                    text = { Text(stringResource(R.string.archive_title)) },
                    shape = MenuDefaults.itemShape(1, 2).shape,
                    leadingIcon = { Icon(ShamelaIcons.Search, contentDescription = null) },
                )
            }
        }
    }
}

/** Above the sections: the way to scanned books (PDF) on archive.org, which aren't listed here. */
@Composable
private fun ArchiveEntry(onClick: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(ShamelaIcons.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.archive_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text(
                text = stringResource(R.string.archive_entry_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
        Icon(ShamelaIcons.NavigateForward, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
    }
}

@Composable
private fun CatalogLoading(state: ShamelaCatalogState.Loading) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (state.total > 0) stringResource(R.string.shamela_catalog_loading, state.done, state.total)
            else stringResource(R.string.shamela_catalog_loading_start),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        if (state.total > 0) {
            LinearProgressIndicator(
                progress = { state.done.toFloat() / state.total },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        } else {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
        }
    }
}

@Composable
private fun CatalogFailed(state: ShamelaCatalogState.Failed, onRetry: () -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(
                if (state.needsVerification) R.string.shamela_catalog_needs_verify else R.string.shamela_catalog_failed
            ),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.needsVerification) {
                FilledTonalButton(onClick = { ShamelaVerifyActivity.open(context) }) {
                    Text(stringResource(R.string.shamela_verify_action))
                }
            }
            OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.dl_retry_now)) }
        }
    }
}
