@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.shamela.library.presentation.screens.library


import androidx.compose.material3.LoadingIndicator
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
import com.shamela.apptheme.presentation.common.SegmentedTabs
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.shamela.library.domain.util.BookSorter
import com.shamela.library.presentation.common.BookSortMenu
import com.shamela.library.presentation.common.ConfirmationDialog
import com.shamela.library.presentation.common.LibraryBookItem
import com.shamela.library.presentation.common.SectionItem
import com.shamela.library.presentation.navigation.Library
import com.shamela.library.presentation.screens.LocalPaddingValues
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
    val sortedBooks = remember(
        libraryState.books,
        libraryState.sortOption,
        libraryState.sortAscending,
        libraryState.downloadTimes,
    ) {
        BookSorter.sortBooks(
            books = libraryState.books.values.toList(),
            option = libraryState.sortOption,
            ascending = libraryState.sortAscending,
            downloadTimes = libraryState.downloadTimes,
        )
    }
    var bookPendingDelete by remember { mutableStateOf<Book?>(null) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().padding(localPadding)) {
    LazyColumn(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SegmentGap),
        contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp)
    ) {
        item {
            SegmentedTabs(
                options = BooksViewType.entries,
                selected = libraryState.booksViewType,
                label = { stringResource(it.label) },
                onSelect = { viewModel.onEvent(LibraryEvent.OnChangeViewType(it)) },
            )
        }
        if (libraryState.isLoading) {
            item {
                Box(Modifier.fillParentMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
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
            when (libraryState.booksViewType) {
            BooksViewType.Sections -> {
                val sections = libraryState.sections.values.toList()
                itemsIndexed(sections, key = { _, it -> it.id }) { index, it ->
                    SectionItem(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        item = it,
                        onClick = { navigateToSectionBooksScreen(it.name, "local") },
                        index = index,
                        count = sections.size,
                    )
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

                itemsIndexed(sortedBooks, key = { _, it -> it.id }) { index, it ->
                    LibraryBookItem(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .animateItem(),
                        item = it,
                        onClick = {
                            if (viewModel.libraryState.value.selectedBooks.isEmpty()) {
                                FilesBooksRepoImpl.openEpub(
                                    it,
                                    onAddQuoteToFavorite = { quote ->
                                        viewModel.onEvent(LibraryEvent.AddQuoteToFavorite(quote))
                                    })
                            } else {
                                viewModel.onEvent(LibraryEvent.SelectBook(it))
                            }
                        },
                        onLongClick = { viewModel.onEvent(LibraryEvent.SelectBook(it)) },
                        onFavoriteIconClicked = { viewModel.onEvent(LibraryEvent.ToggleFavorite(it)) },
                        onSwipeOut = {
                            bookPendingDelete = it
                        },
                        isSelected = libraryState.selectedBooks.contains(it),
                        onInfoClick = { navigateToBookDetails(it) },
                        index = index,
                        count = sortedBooks.size,
                    )
                }
            }
            }
        }
    }

        // M3 Expressive floating toolbar for the selection's actions.
        AnimatedVisibility(
            visible = libraryState.selectedBooks.isNotEmpty(),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
        ) {
            SelectionToolbar(
                count = libraryState.selectedBooks.size,
                onCancel = { viewModel.onEvent(LibraryEvent.CancelSelection) },
                onDelete = { showDeleteSelectedDialog = true },
            )
        }
    }

    bookPendingDelete?.let { book ->
        ConfirmationDialog(
            title = stringResource(R.string.delete_book_title),
            message = stringResource(R.string.delete_book_message, book.title),
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
            message = stringResource(R.string.delete_selected_books_message, libraryState.selectedBooks.size),
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

/** Floating toolbar shown while books are selected: close, selected count, delete. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SelectionToolbar(count: Int, onCancel: () -> Unit, onDelete: () -> Unit) {
    HorizontalFloatingToolbar(
        expanded = true,
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
        leadingContent = {
            IconButton(onClick = onCancel) {
                Icon(ShamelaIcons.Cancel, contentDescription = stringResource(R.string.cancel))
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
