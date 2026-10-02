package com.shamela.library.presentation.screens.searchResults


import androidx.compose.foundation.layout.Arrangement
import com.shamela.library.presentation.common.SegmentGap
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.WindowInsets
import com.shamela.library.R
import androidx.compose.ui.res.stringResource
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shamela.apptheme.presentation.common.EmptyListScreen
import com.shamela.apptheme.presentation.common.LoadingScreen
import com.shamela.apptheme.presentation.common.SearchTopBar
import com.shamela.library.data.local.files.FilesBooksRepoImpl
import com.shamela.library.domain.model.Book
import com.shamela.library.presentation.common.BookItem
import com.shamela.library.presentation.common.DownloadIconButton
import com.shamela.library.presentation.common.SectionItem
import com.shamela.library.presentation.screens.LocalPaddingValues

@Composable
fun SearchResultsScreen(
    viewModel: SearchResultsViewModel = hiltViewModel(),
    navigateToSectionBooksScreen: (categoryName: String, type: String) -> Unit,
    navigateBack: () -> Unit,
    navigateToBookDetails: (Book) -> Unit,
) {
    val state = viewModel.searchResultsState.collectAsStateWithLifecycle().value
    val localPadding = LocalPaddingValues.current
    val focusRequester = remember { FocusRequester() }
    Column(Modifier.fillMaxSize().padding(localPadding)) {
        SearchTopBar(
            onNavigateBack = navigateBack,
            hint = stringResource(R.string.search_hint),
            focusRequester = focusRequester,
            value = state.query,
            onValueChanged = { viewModel.onEvent((SearchResultsEvent.OnSearchQueryChanged(it))) },
            onClickClear = { viewModel.onEvent(SearchResultsEvent.ClearSearchQuery) },
            onClickSearch = { query -> viewModel.onEvent(SearchResultsEvent.Search(query)) },
            windowInsets = WindowInsets(0),
        )
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(SegmentGap),
                contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)
            ) {
                if (state.type == "sections"){
                    itemsIndexed(state.sectionsResultsList, key = { _, it -> it.id }) { index, it ->
                        SectionItem(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            item = it,
                            highlightText = state.lastQuery,
                            onClick = { navigateToSectionBooksScreen(it.name, "remote") },
                            index = index,
                            count = state.sectionsResultsList.size,
                        )
                    }
                }else{
                    itemsIndexed(state.booksResultsList, key = { _, it -> it.id }) { index, currentBook ->
                        when (state.type){
                            "local"->{
                                BookItem(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    onClick = {
                                        FilesBooksRepoImpl.openEpub(
                                            currentBook,
                                            onAddQuoteToFavorite = { quote ->
                                                viewModel.onEvent(
                                                    SearchResultsEvent.AddQuoteToFavorite(quote)
                                                )
                                            })
                                    },
                                    index = index,
                                    count = state.booksResultsList.size,
                                    item = currentBook,
                                    highlightText = state.lastQuery,
                                    onInfoClick = { navigateToBookDetails(currentBook) }
                                )
                            }
                            "remote"->{
                                BookItem(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    onClick = { navigateToBookDetails(currentBook) },
                                    index = index,
                                    count = state.booksResultsList.size,
                                    item = currentBook,
                                    onInfoClick = { navigateToBookDetails(currentBook) },
                                    icon = {
                                        DownloadIconButton(
                                            bookId = currentBook.id,
                                            downloadStatuses = state.downloadStatuses,
                                            downloadedBookIds = state.downloadedBookIds,
                                            onDownloadClick = {
                                                viewModel.onEvent(SearchResultsEvent.OnClickDownloadBook(currentBook))
                                            },
                                            onCancelClick = {
                                                viewModel.onEvent(SearchResultsEvent.OnClickCancelDownload(currentBook.id))
                                            },
                                        )
                                    },
                                    highlightText = state.lastQuery
                                )
                            }
                        }
                    }
                }
            }
            // Overlays on top of the (empty) list, not stacked above it.
            EmptyListScreen(
                visibility = state.isListEmpty,
                text = stringResource(R.string.no_results),
            )
            LoadingScreen(visibility = state.isLoading)
        }
    }

    DisposableEffect(Unit) {
        focusRequester.requestFocus()
        onDispose { }
    }
}