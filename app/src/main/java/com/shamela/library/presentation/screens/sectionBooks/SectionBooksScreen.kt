@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.shamela.library.presentation.screens.sectionBooks


import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.CircularWavyProgressIndicator
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
import com.shamela.library.domain.util.BookSorter
import com.shamela.library.presentation.common.BookItem
import com.shamela.library.presentation.common.BookSortMenu
import com.shamela.library.presentation.common.DownloadIconButton

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
    val sortedBooks = remember(
        sectionBooksState.books,
        sectionBooksState.sortOption,
        sectionBooksState.sortAscending,
        sectionBooksState.downloadTimes,
    ) {
        BookSorter.sortBooks(
            books = sectionBooksState.books.values.toList(),
            option = sectionBooksState.sortOption,
            ascending = sectionBooksState.sortAscending,
            downloadTimes = sectionBooksState.downloadTimes,
        )
    }

    val downloadedInSection = remember(sectionBooksState.downloadedBookIds, sectionBooksState.books) {
        sectionBooksState.downloadedBookIds.intersect(sectionBooksState.books.keys).size
    }
    val totalInSection = sectionBooksState.books.size
    val hasActiveDownloadsInSection = remember(sectionBooksState.downloadStatuses, sectionBooksState.books) {
        sectionBooksState.books.keys.any { sectionBooksState.downloadStatuses[it] is DownloadStatus.Downloading }
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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Column(Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)) {
        SectionTopBar(
            scrollBehavior = scrollBehavior,
            title = categoryName,
            onNavigateBack = navigateBack,
            onSearch = { navigateToSearchResultsScreen(categoryName, sectionBooksState.type) },
            onDownload = { confirmSectionDownload = true },
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
            contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)
        ) {
            itemsIndexed(sortedBooks, key = { _, it -> it.id }) { index, currentBook ->
                when (sectionBooksState.type) {
                    "local" -> {
                        BookItem(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            onClick = {
                                FilesBooksRepoImpl.openEpub(
                                    currentBook,
                                    onAddQuoteToFavorite = { quote ->
                                        viewModel.onEvent(
                                            SectionBooksEvent.AddQuoteToFavorite(quote)
                                        )
                                    })
                            },
                            index = index,
                            count = sortedBooks.size,
                            item = currentBook,
                            onInfoClick = { navigateToBookDetails(currentBook) }
                        )
                    }

                    "remote" -> {
                        BookItem(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            onClick = { navigateToBookDetails(currentBook) },
                            index = index,
                            count = sortedBooks.size,
                            onInfoClick = { navigateToBookDetails(currentBook) },
                            icon = {
                                DownloadIconButton(
                                    bookId = currentBook.id,
                                    downloadStatuses = sectionBooksState.downloadStatuses,
                                    downloadedBookIds = sectionBooksState.downloadedBookIds,
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
                            item = currentBook
                        )
                    }
                }
            }
        }
    }
    LoadingScreen(visibility = sectionBooksState.isLoading)

    LaunchedEffect(Unit) {
        viewModel.onEvent(SectionBooksEvent.LoadBooks)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SectionTopBar(
    title: String,
    onDownload: () -> Unit,
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
            IconButton(onClick = onDownload, enabled = isDownloadButtonEnabled) {
                when {
                    totalBookCount > 0 && downloadedBookCount == totalBookCount -> {
                        Icon(
                            ShamelaIcons.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    hasActiveDownloads -> {
                        CircularWavyProgressIndicator(
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
                Icon(ShamelaIcons.NavigateBack, contentDescription = null)
            }
        }
    )
}
