package com.shamela.library.presentation.screens.searchResults


import com.shamela.library.presentation.common.downloadStatusOf
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text
import androidx.compose.material3.FilledTonalButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.shamela.apptheme.presentation.common.ListGroupHeader
import com.shamela.apptheme.presentation.common.LoadingScreen
import com.shamela.apptheme.presentation.common.SearchTopBar
import com.shamela.library.data.local.files.FilesBooksRepoImpl
import com.shamela.library.domain.model.Book
import com.shamela.library.presentation.common.BookItem
import com.shamela.library.presentation.common.DownloadIconButton
import com.shamela.library.presentation.common.SectionItem
import com.shamela.library.presentation.screens.LocalPaddingValues
import com.shamela.library.presentation.screens.LocalFloatingBarInset

@Composable
fun SearchResultsScreen(
    viewModel: SearchResultsViewModel = hiltViewModel(),
    navigateToSectionBooksScreen: (categoryName: String, type: String) -> Unit,
    navigateBack: () -> Unit,
    navigateToBookDetails: (Book) -> Unit,
    navigateToArchiveSearch: (query: String) -> Unit = {},
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
                contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp + LocalFloatingBarInset.current)
            ) {
                val sections = state.sectionsResultsList
                if (sections.isNotEmpty()) {
                    item(key = "sections-header") {
                        ListGroupHeader(text = stringResource(R.string.view_sections) + " (${sections.size})")
                    }
                    itemsIndexed(sections, key = { _, it -> "section-${it.id}" }) { index, it ->
                        SectionItem(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            item = it,
                            highlightText = state.lastQuery,
                            onClick = { navigateToSectionBooksScreen(it.name, state.type) },
                            index = index,
                            count = sections.size,
                        )
                    }
                    if (state.booksResultsList.isNotEmpty()) {
                        item(key = "books-header") {
                            ListGroupHeader(text = stringResource(R.string.view_books) + " (${state.booksResultsList.size})")
                        }
                    }
                }
                if (state.showsClosest) {
                    item(key = "closest-header") {
                        ListGroupHeader(text = stringResource(R.string.search_closest_results))
                    }
                }
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
                        "remote", "shamela" -> {
                            val rowStatus = downloadStatusOf(currentBook.id, state.downloadStatuses, state.downloadedBookIds)
                            BookItem(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                onClick = { navigateToBookDetails(currentBook) },
                                index = index,
                                count = state.booksResultsList.size,
                                item = currentBook,
                                onInfoClick = { navigateToBookDetails(currentBook) },
                                icon = {
                                    DownloadIconButton(
                                    status = rowStatus,
                                        onDownloadClick = {
                                            viewModel.onEvent(SearchResultsEvent.OnClickDownloadBook(currentBook))
                                        },
                                        onCancelClick = {
                                            viewModel.onEvent(SearchResultsEvent.OnClickCancelDownload(currentBook.id))
                                        },
                                    )
                                },
                                highlightText = state.lastQuery,
                                downloadStatus = rowStatus,
                            )
                        }
                    }
                }
                if (state.type == "remote" && state.lastQuery.isNotBlank() && !state.isLoading) {
                    item(key = "archive") {
                        ArchiveSuggestion(
                            nothingFound = state.booksResultsList.isEmpty() || state.showsClosest,
                            onClick = { navigateToArchiveSearch(state.lastQuery) },
                        )
                    }
                }
            }
            // Overlays on top of the (empty) list, not stacked above it. The catalogs' search offers
            // archive.org's scanned books instead.
            EmptyListScreen(
                visibility = state.isListEmpty && state.type != "remote",
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

/** At the end of the catalogs' results: search the same words among archive.org's scanned books. */
@Composable
private fun ArchiveSuggestion(nothingFound: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(if (nothingFound) R.string.archive_suggest_nothing else R.string.archive_suggest_more),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(onClick = onClick) { Text(stringResource(R.string.archive_search_button)) }
    }
}
