package com.shamela.library.presentation.screens.library


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
    LazyColumn(
        Modifier.fillMaxSize().padding(localPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
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
                    CircularProgressIndicator()
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
                items(libraryState.sections.values.toList(), key = { it.id }) {
                    SectionItem(modifier = Modifier
                        .clickable {
                            navigateToSectionBooksScreen(it.name, "local")
                        }, item = it)
                    ListDivider(startInset = 72)
                }
            }

            BooksViewType.Books -> {
                item {
                    AnimatedContent(
                        targetState = libraryState.selectedBooks.isNotEmpty(),
                        label = "library toolbar"
                    ) { selecting ->
                        if (selecting) {
                            SelectionBar(
                                count = libraryState.selectedBooks.size,
                                onCancel = { viewModel.onEvent(LibraryEvent.CancelSelection) },
                                onDelete = { showDeleteSelectedDialog = true },
                            )
                        } else {
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
                    }
                }

                items(sortedBooks, key = { it.id }) {
                    LibraryBookItem(modifier = Modifier
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { _ ->
                                if (viewModel.libraryState.value.selectedBooks.isEmpty()) {
                                    FilesBooksRepoImpl.openEpub(
                                        it,
                                        onAddQuoteToFavorite = { quote ->
                                            viewModel.onEvent(
                                                LibraryEvent.AddQuoteToFavorite(
                                                    quote
                                                )
                                            )
                                        })
                                } else {
                                    viewModel.onEvent(LibraryEvent.SelectBook(it))
                                }
                            }, onLongPress = { _ ->
                                viewModel.onEvent(LibraryEvent.SelectBook(it))
                            })
                        }
                        .animateItem(),
                        item = it,
                        onFavoriteIconClicked = { viewModel.onEvent(LibraryEvent.ToggleFavorite(it)) },
                        onSwipeOut = {
                            bookPendingDelete = it
                        },
                        isSelected = libraryState.selectedBooks.contains(it),
                        onInfoClick = { navigateToBookDetails(it) }
                    )
                    ListDivider(startInset = 72)
                }
            }
            }
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

/** Contextual bar replacing the sort row while books are selected. */
@Composable
private fun SelectionBar(count: Int, onCancel: () -> Unit, onDelete: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            Modifier.padding(start = 4.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel) {
                Icon(ShamelaIcons.Cancel, contentDescription = stringResource(R.string.cancel))
            }
            Text(
                text = stringResource(R.string.selected_count, count),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(ShamelaIcons.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.delete))
            }
        }
    }
}
