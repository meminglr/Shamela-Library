@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.shamela.library.presentation.screens.library

import com.shamela.library.presentation.common.groupedSectionItems
import com.shamela.library.presentation.common.bookRowItems
import com.shamela.library.presentation.common.bookRows
import com.shamela.library.presentation.common.volumeSelectionToggles
import com.shamela.library.presentation.common.VolumeActions
import androidx.compose.runtime.saveable.rememberSaveable



import com.shamela.library.presentation.common.SegmentGap
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.IconButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.animation.AnimatedContent
import com.shamela.apptheme.presentation.common.EmptyState
import com.shamela.apptheme.presentation.common.ListDivider
import com.shamela.apptheme.presentation.common.TabbedPager
import com.shamela.library.R
import androidx.compose.ui.res.stringResource
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.shamela.apptheme.presentation.common.DelayedProgressIndicator
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shamela.apptheme.presentation.common.LoadingScreen
import com.shamela.apptheme.presentation.theme.AppFonts
import com.shamela.apptheme.presentation.theme.ShamelaIcons
import com.shamela.library.data.local.files.FilesBooksRepoImpl
import com.shamela.library.domain.model.Book
import androidx.activity.compose.BackHandler
import com.shamela.library.presentation.common.BookSortMenu
import com.shamela.library.presentation.common.ConfirmationDialog
import com.shamela.library.presentation.common.LibraryBookItem
import com.shamela.library.presentation.common.SectionItem
import com.shamela.library.presentation.navigation.Library
import com.shamela.library.presentation.screens.LocalPaddingValues
import com.shamela.library.presentation.screens.LocalFloatingBarInset
import com.shamela.library.presentation.screens.FloatingToolbar
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    navigateToSectionBooksScreen: (categoryName: String, type: String) -> Unit,
    navigateToSearchResultsScreen: (categoryName: String, type: String) -> Unit,
    navigateToBookDetails: (Book) -> Unit,
) {
    LaunchedEffect(key1 = Unit, block = {
        Library.buttons.onEach {
            if (it) {
                Log.d("Shamela", "LibraryScreen: Search is clicked")
                navigateToSearchResultsScreen("all", "local")
            }
        }.launchIn(this)
    })
    val libraryState = viewModel.libraryState.collectAsStateWithLifecycle().value
    val localPadding = LocalPaddingValues.current
    val sortedBooks = libraryState.sortedBooks
    // Scanned books' volumes sit under one row per book, opened like on the download page.
    var expandedVolumes by rememberSaveable { mutableStateOf(setOf<String>()) }
    val bookRows = remember(sortedBooks) { bookRows(sortedBooks) }
    val selecting = libraryState.selectedIds.isNotEmpty()
    val openOrSelect: (Book) -> Unit = { book ->
        if (viewModel.libraryState.value.selectedIds.isEmpty()) {
            FilesBooksRepoImpl.openEpub(
                book,
                onAddQuoteToFavorite = { quote -> viewModel.onEvent(LibraryEvent.AddQuoteToFavorite(quote)) })
        } else {
            viewModel.onEvent(LibraryEvent.SelectBook(book.id))
        }
    }
    // Back clears the selection first, like the section screen.
    BackHandler(enabled = selecting) { viewModel.onEvent(LibraryEvent.CancelSelection) }
    var bookPendingDelete by remember { mutableStateOf<Book?>(null) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }
    // The selection toolbar takes the floating navigation bar's place while books are selected.
    FloatingToolbar(visible = selecting) {
        SelectionToolbar(
            count = libraryState.selectedIds.size,
            onCancel = { viewModel.onEvent(LibraryEvent.CancelSelection) },
            onDelete = { showDeleteSelectedDialog = true },
            onSelectAll = { viewModel.onEvent(LibraryEvent.SelectAll) },
        )
    }
    Box(Modifier.fillMaxSize().padding(localPadding)) {
    TabbedPager(
        options = BooksViewType.entries,
        selected = libraryState.booksViewType,
        label = { stringResource(it.label) },
            icon = { it.icon },
        onSelect = { viewModel.onEvent(LibraryEvent.OnChangeViewType(it)) },
    ) { viewType ->
    LazyColumn(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SegmentGap),
        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp + LocalFloatingBarInset.current)
    ) {
        if (libraryState.isLoading) {
            item {
                Box(Modifier.fillParentMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    DelayedProgressIndicator()
                }
            }
        }
        if (!libraryState.isLoading && libraryState.books.isEmpty()) {
            item {
                EmptyLibraryState(
                    modifier = Modifier
                        .fillParentMaxSize()
                        .padding(horizontal = 32.dp)
                )
            }
        } else {
            when (viewType) {
            BooksViewType.Sections -> {
                groupedSectionItems(libraryState.sections.values) {
                    navigateToSectionBooksScreen(it.name, "local")
                }
            }

            BooksViewType.Books -> {
                item {
                    SortBar(
                        label = stringResource(R.string.sort_current, stringResource(libraryState.sortOption.label)),
                    ) {
                        BookSortMenu(
                            sortOption = libraryState.sortOption,
                            ascending = libraryState.sortAscending,
                            onOptionSelected = { viewModel.onEvent(LibraryEvent.OnChangeSortOption(it)) },
                            onToggleDirection = { viewModel.onEvent(LibraryEvent.OnToggleSortDirection) }
                        )
                    }
                }

                bookRowItems(
                    rows = bookRows,
                    expanded = expandedVolumes,
                    selectedIds = libraryState.selectedIds,
                    onToggleExpanded = { key -> expandedVolumes = if (key in expandedVolumes) expandedVolumes - key else expandedVolumes + key },
                    onToggleVolumes = { volumes ->
                        volumeSelectionToggles(volumes, viewModel.libraryState.value.selectedIds)
                            .forEach { viewModel.onEvent(LibraryEvent.SelectBook(it)) }
                    },
                    volumeActions = VolumeActions(
                        onClick = { book -> openOrSelect(book) },
                        onLongClick = { book -> viewModel.onEvent(LibraryEvent.SelectBook(book.id)) },
                        onFavorite = { book -> viewModel.onEvent(LibraryEvent.ToggleFavorite(book)) },
                        onInfo = { book -> navigateToBookDetails(book) },
                        isFavorite = { book -> book.isFavorite },
                    ),
                ) { it, rowModifier, index, count ->
                    LibraryBookItem(
                        modifier = rowModifier,
                        item = it,
                        onClick = { openOrSelect(it) },
                        onLongClick = { viewModel.onEvent(LibraryEvent.SelectBook(it.id)) },
                        onFavoriteIconClicked = { viewModel.onEvent(LibraryEvent.ToggleFavorite(it)) },
                        onSwipeOut = {
                            bookPendingDelete = it
                        },
                        isSelected = it.id in libraryState.selectedIds,
                        onInfoClick = { navigateToBookDetails(it) },
                        index = index,
                        count = count,
                    )
                }
            }
            }
        }
    }
    }

    }

    bookPendingDelete?.let { book ->
        ConfirmationDialog(
            title = stringResource(R.string.delete_book_title),
            message = stringResource(R.string.delete_book_message, isolate(book.title)),
            onConfirm = {
                viewModel.onEvent(LibraryEvent.DeleteBook(book))
                bookPendingDelete = null
            },
            onDismiss = { bookPendingDelete = null }
        )
    }

    if (showDeleteSelectedDialog) {
        ConfirmationDialog(
            title = stringResource(R.string.delete_selected_books),
            message = stringResource(R.string.delete_selected_books_message, libraryState.selectedIds.size),
            onConfirm = {
                viewModel.onEvent(LibraryEvent.DeleteSelectedBooks)
                showDeleteSelectedDialog = false
            },
            onDismiss = { showDeleteSelectedDialog = false }
        )
    }
}

@Composable
fun EmptyLibraryState(modifier: Modifier = Modifier) {
    EmptyState(
        icon = ShamelaIcons.LocalLibrary,
        title = stringResource(R.string.library_empty_title),
        message = stringResource(R.string.library_empty_message),
        modifier = modifier,
    )
}

/** "Sorted by …" row with the sort menu, shown above the book list. */
@Composable
fun SortBar(label: String, menu: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        menu()
    }
}

/** Floating toolbar shown while books are selected: close, select all, selected count, delete. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SelectionToolbar(count: Int, onCancel: () -> Unit, onDelete: () -> Unit, onSelectAll: (() -> Unit)? = null) {
    HorizontalFloatingToolbar(
        expanded = true,
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
        leadingContent = {
            IconButton(onClick = onCancel) {
                Icon(ShamelaIcons.Cancel, contentDescription = stringResource(R.string.cancel))
            }
            onSelectAll?.let {
                IconButton(onClick = it) {
                    Icon(ShamelaIcons.SelectAll, contentDescription = stringResource(R.string.select_all))
                }
            }
        },
        trailingContent = {
            FilledIconButton(
                onClick = onDelete,
                shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Icon(ShamelaIcons.Delete, contentDescription = stringResource(R.string.delete_selected_books))
            }
        },
    ) {
        Text(
            text = stringResource(R.string.selected_count, count),
            style = MaterialTheme.typography.titleSmallEmphasized,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

/**
 * Keeps an Arabic name in a Turkish sentence from reordering the sentence around it (Unicode
 * first-strong isolate), e.g. in "\"<name>\" bölümündeki 175 kitap…".
 */
fun isolate(text: String): String = "\u2068$text\u2069"
