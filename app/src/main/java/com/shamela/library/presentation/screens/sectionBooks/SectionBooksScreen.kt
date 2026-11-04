@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.shamela.library.presentation.screens.sectionBooks


import com.shamela.library.presentation.common.downloadStatusOf
import com.shamela.library.presentation.common.bookRowItems
import com.shamela.library.presentation.common.bookRows
import com.shamela.library.presentation.common.volumeSelectionToggles
import com.shamela.library.presentation.common.VolumeActions
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.foundation.layout.Arrangement
import com.shamela.library.presentation.common.SegmentGap
import androidx.compose.foundation.lazy.itemsIndexed
import com.shamela.library.presentation.common.ConfirmationDialog
import com.shamela.library.presentation.common.sectionDisplayName
import com.shamela.library.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shamela.apptheme.presentation.common.LoadingScreen
import com.shamela.apptheme.presentation.theme.AppFonts
import com.shamela.apptheme.presentation.theme.ShamelaIcons
import com.shamela.library.data.local.files.FilesBooksRepoImpl
import com.shamela.library.domain.model.Book
import com.shamela.library.domain.model.DownloadStatus
import com.shamela.library.domain.util.BookSortOption
import com.shamela.library.presentation.common.BookItem
import com.shamela.library.presentation.common.BookSortMenu
import com.shamela.library.presentation.common.DownloadIconButton
import com.shamela.library.presentation.common.LibraryBookItem
import com.shamela.library.presentation.screens.library.SelectionToolbar
import com.shamela.library.presentation.screens.library.isolate
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import com.shamela.library.presentation.screens.FloatingToolbar
import com.shamela.library.presentation.screens.LocalFloatingBarInset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionBooksScreen(
    viewModel: SectionBooksViewModel = hiltViewModel(),
    categoryName: String,
    navigateBack: () -> Unit,
    navigateToSearchResultsScreen: (categoryName: String, type: String) -> Unit,
    navigateToBookDetails: (Book) -> Unit,
) {
    val sectionBooksState = viewModel.sectionBooksState.collectAsStateWithLifecycle().value

    // Download time only applies to local books (remote ones have no file on disk).
    val availableSortOptions = remember(sectionBooksState.type) {
        if (sectionBooksState.type == "local") BookSortOption.values().toList()
        else BookSortOption.values().filter { it != BookSortOption.DOWNLOAD_TIME }
    }
    val sortedBooks = sectionBooksState.sortedBooks
    // Scanned books' volumes sit under one row per book, opened like on the download page.
    var expandedVolumes by rememberSaveable { mutableStateOf(setOf<String>()) }
    val bookRows = remember(sortedBooks) { bookRows(sortedBooks) }

    val downloadedInSection = remember(sectionBooksState.downloadedBookIds, sectionBooksState.books) {
        sectionBooksState.downloadedBookIds.intersect(sectionBooksState.books.keys).size
    }
    val totalInSection = sectionBooksState.books.size
    val hasActiveDownloadsInSection = remember(sectionBooksState.downloadStatuses, sectionBooksState.books) {
        sectionBooksState.books.keys.any { sectionBooksState.downloadStatuses[it]?.isActive == true }
    }

    var confirmSectionDownload by remember { mutableStateOf(false) }
    if (confirmSectionDownload) {
        val remaining = (totalInSection - downloadedInSection).coerceAtLeast(0)
        ConfirmationDialog(
            title = stringResource(R.string.download_section),
            message = stringResource(R.string.download_section_message, remaining),
            confirmText = stringResource(R.string.download),
            icon = ShamelaIcons.FileDownload,
            destructive = false,
            onConfirm = {
                confirmSectionDownload = false
                viewModel.onEvent(SectionBooksEvent.OnClickDownloadSection)
            },
            onDismiss = { confirmSectionDownload = false },
        )
    }
    val isLocal = sectionBooksState.type == "local"
    val selecting = sectionBooksState.selectedIds.isNotEmpty()
    val openOrSelect: (Book) -> Unit = { book ->
        if (viewModel.sectionBooksState.value.selectedIds.isNotEmpty()) {
            viewModel.onEvent(SectionBooksEvent.ToggleSelection(book.id))
        } else {
            FilesBooksRepoImpl.openEpub(
                book,
                onAddQuoteToFavorite = { quote -> viewModel.onEvent(SectionBooksEvent.AddQuoteToFavorite(quote)) })
        }
    }
    // Books waiting for the delete confirmation: one (swiped), the selection, or the whole section.
    var pendingDelete by remember { mutableStateOf<Set<String>?>(null) }
    var pendingDeleteSection by remember { mutableStateOf(false) }
    LaunchedEffect(sectionBooksState.isSectionEmptied) {
        if (sectionBooksState.isSectionEmptied) navigateBack()
    }
    BackHandler(enabled = selecting) { viewModel.onEvent(SectionBooksEvent.CancelSelection) }
    // In the host's bottom stack, so the downloads bar stays above it.
    FloatingToolbar(visible = selecting) {
        SelectionToolbar(
            count = sectionBooksState.selectedIds.size,
            onCancel = { viewModel.onEvent(SectionBooksEvent.CancelSelection) },
            onDelete = { pendingDelete = sectionBooksState.selectedIds },
            onSelectAll = { viewModel.onEvent(SectionBooksEvent.SelectAll) },
        )
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)) {
        SectionTopBar(
            scrollBehavior = scrollBehavior,
            title = categoryName,
            onNavigateBack = navigateBack,
            onSearch = { navigateToSearchResultsScreen(categoryName, sectionBooksState.type) },
            // shamela.ws books are built page by page from the site: one at a time, not whole sections.
            onDownload = if (sectionBooksState.type == "shamela") null else ({ confirmSectionDownload = true }),
            onDeleteSection = if (isLocal && totalInSection > 0) ({ pendingDeleteSection = true }) else null,
            isDownloadButtonEnabled = sectionBooksState.isDownloadButtonEnabled,
            downloadedBookCount = downloadedInSection,
            totalBookCount = totalInSection,
            hasActiveDownloads = hasActiveDownloadsInSection,
            sortOption = sectionBooksState.sortOption,
            sortAscending = sectionBooksState.sortAscending,
            availableSortOptions = availableSortOptions,
            onSortOptionSelected = { viewModel.onEvent(SectionBooksEvent.OnChangeSortOption(it)) },
            onToggleSortDirection = { viewModel.onEvent(SectionBooksEvent.OnToggleSortDirection) },
        )

        LazyColumn(
            Modifier
                .fillMaxSize()
                .clipToBounds(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SegmentGap),
            contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp + LocalFloatingBarInset.current)
        ) {
            if (isLocal) bookRowItems(
                rows = bookRows,
                expanded = expandedVolumes,
                selectedIds = sectionBooksState.selectedIds,
                onToggleExpanded = { key -> expandedVolumes = if (key in expandedVolumes) expandedVolumes - key else expandedVolumes + key },
                onToggleVolumes = { volumes ->
                    volumeSelectionToggles(volumes, viewModel.sectionBooksState.value.selectedIds)
                        .forEach { viewModel.onEvent(SectionBooksEvent.ToggleSelection(it)) }
                },
                volumeActions = VolumeActions(
                    onClick = { book -> openOrSelect(book) },
                    onLongClick = { book -> viewModel.onEvent(SectionBooksEvent.ToggleSelection(book.id)) },
                    onFavorite = { book ->
                        viewModel.onEvent(SectionBooksEvent.ToggleFavorite(book.copy(isFavorite = book.id in viewModel.sectionBooksState.value.favoriteIds)))
                    },
                    onInfo = { book -> navigateToBookDetails(book) },
                    isFavorite = { book -> book.id in sectionBooksState.favoriteIds },
                ),
            ) { currentBook, rowModifier, index, count ->
                        // Same as the library's book list: swipe to delete, long-press to select.
                        val book = currentBook.copy(isFavorite = currentBook.id in sectionBooksState.favoriteIds)
                        LibraryBookItem(
                            modifier = rowModifier,
                            item = book,
                            onClick = { openOrSelect(currentBook) },
                            onLongClick = { viewModel.onEvent(SectionBooksEvent.ToggleSelection(book.id)) },
                            onFavoriteIconClicked = { viewModel.onEvent(SectionBooksEvent.ToggleFavorite(book)) },
                            onSwipeOut = { pendingDelete = setOf(book.id) },
                            isSelected = book.id in sectionBooksState.selectedIds,
                            onInfoClick = { navigateToBookDetails(currentBook) },
                            index = index,
                            count = count,
                        )
            } else itemsIndexed(sortedBooks, key = { _, it -> it.id }) { index, currentBook ->
                when (sectionBooksState.type) {
                    "remote", "shamela" -> {
                        val rowStatus = downloadStatusOf(
                            currentBook.id,
                            sectionBooksState.downloadStatuses,
                            sectionBooksState.downloadedBookIds,
                        )
                        BookItem(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            onClick = { navigateToBookDetails(currentBook) },
                            index = index,
                            count = sortedBooks.size,
                            onInfoClick = { navigateToBookDetails(currentBook) },
                            icon = {
                                DownloadIconButton(
                                    status = rowStatus,
                                    onDownloadClick = {
                                        viewModel.onEvent(
                                            SectionBooksEvent.OnClickDownloadBook(currentBook)
                                        )
                                    },
                                    onCancelClick = {
                                        viewModel.onEvent(
                                            SectionBooksEvent.OnClickCancelDownload(currentBook.id)
                                        )
                                    },
                                )
                            },
                            item = currentBook,
                            downloadStatus = rowStatus,
                        )
                    }
                }
            }
        }
    }

    }

    pendingDelete?.let { ids ->
        val single = ids.singleOrNull()?.let { sectionBooksState.books[it] }
        ConfirmationDialog(
            title = stringResource(if (single != null) R.string.delete_book_title else R.string.delete_selected_books),
            message = if (single != null) stringResource(R.string.delete_book_message, isolate(single.title))
            else stringResource(R.string.delete_selected_books_message, ids.size),
            onConfirm = {
                viewModel.onEvent(SectionBooksEvent.DeleteBooks(ids))
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }

    if (pendingDeleteSection) {
        ConfirmationDialog(
            title = stringResource(R.string.delete_section),
            message = stringResource(R.string.delete_section_message, isolate(sectionDisplayName(categoryName)), totalInSection),
            onConfirm = {
                viewModel.onEvent(SectionBooksEvent.DeleteBooks(sectionBooksState.books.keys))
                pendingDeleteSection = false
            },
            onDismiss = { pendingDeleteSection = false },
        )
    }

    LoadingScreen(visibility = sectionBooksState.isLoading)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SectionTopBar(
    title: String,
    /** Download the whole section; null hides the button. */
    onDownload: (() -> Unit)?,
    /** Local sections: delete every book in it (instead of the download button). */
    onDeleteSection: (() -> Unit)?,
    onSearch: () -> Unit,
    onNavigateBack: () -> Unit,
    isDownloadButtonEnabled: Boolean,
    downloadedBookCount: Int,
    totalBookCount: Int,
    hasActiveDownloads: Boolean,
    sortOption: BookSortOption,
    sortAscending: Boolean,
    availableSortOptions: List<BookSortOption>,
    onSortOptionSelected: (BookSortOption) -> Unit,
    onToggleSortDirection: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    // Expressive flexible app bar: section name with its book count as subtitle, collapsing on scroll.
    MediumFlexibleTopAppBar(
        modifier = Modifier,
        title = {
            Text(
                text = sectionDisplayName(title),
                style = AppFonts.content(LocalTextStyle.current, naturalAlign = true),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        subtitle = {
            if (totalBookCount > 0) {
                Text(text = stringResource(R.string.books_count_label, totalBookCount))
            }
        },
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        actions = {
            BookSortMenu(
                sortOption = sortOption,
                ascending = sortAscending,
                availableOptions = availableSortOptions,
                onOptionSelected = onSortOptionSelected,
                onToggleDirection = onToggleSortDirection,
            )
            IconButton(onClick = onSearch) {
                Icon(ShamelaIcons.Search, contentDescription = stringResource(R.string.nav_search))
            }
            if (onDeleteSection != null) {
                IconButton(onClick = onDeleteSection) {
                    Icon(ShamelaIcons.Delete, contentDescription = stringResource(R.string.delete_section))
                }
            } else if (onDownload != null) IconButton(onClick = onDownload, enabled = isDownloadButtonEnabled) {
                when {
                    totalBookCount > 0 && downloadedBookCount == totalBookCount -> {
                        Icon(
                            ShamelaIcons.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    hasActiveDownloads -> {
                        CircularProgressIndicator(
                            progress = { if (totalBookCount > 0) downloadedBookCount.toFloat() / totalBookCount else 0f },
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    else -> {
                        Icon(ShamelaIcons.FileDownload, contentDescription = stringResource(R.string.download_section))
                    }
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(ShamelaIcons.NavigateBack, contentDescription = stringResource(com.shamela.apptheme.R.string.back))
            }
        }
    )
}
