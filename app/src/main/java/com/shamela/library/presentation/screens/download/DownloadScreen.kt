@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.shamela.library.presentation.screens.download


import androidx.compose.material3.LoadingIndicator
import androidx.compose.foundation.layout.Arrangement
import com.shamela.library.presentation.common.SegmentGap
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.res.stringResource
import com.shamela.apptheme.presentation.common.SegmentedTabs
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shamela.library.domain.model.Book
import com.shamela.library.presentation.common.BookItem
import com.shamela.library.presentation.common.CharacterHeader
import com.shamela.library.presentation.common.DownloadIconButton
import com.shamela.library.presentation.common.SectionItem
import com.shamela.library.presentation.navigation.Download
import com.shamela.library.presentation.screens.LocalPaddingValues
import com.shamela.library.presentation.screens.library.BooksViewType
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadScreen(
    viewModel: DownloadViewModel = hiltViewModel(),
    navigateToSectionBooksScreen: (categoryName: String, type: String) -> Unit,
    navigateToSearchResultsScreen: (categoryName: String, type: String) -> Unit,
    navigateToBookDetails: (Book) -> Unit,
    ) {
    val downloadState = viewModel.downloadState.collectAsStateWithLifecycle().value
    val localPadding = LocalPaddingValues.current
    LaunchedEffect(key1 = Unit, block = {
        Download.buttons.onEach {
            if (it) {
                if (viewModel.downloadState.value.booksViewType == BooksViewType.Books){
                    Log.d("Shamela", "DownloadScreen: Search Books is clicked")
                    navigateToSearchResultsScreen("all", "remote")
                }else{
                    Log.d("Shamela", "DownloadScreen: Search Sections is clicked")
                    navigateToSearchResultsScreen("all", "sections")
                }
            }
        }.launchIn(this)
    })
    LaunchedEffect(key1 = Unit, block ={
        when (viewModel.downloadState.value.booksViewType){
            BooksViewType.Sections -> viewModel.onEvent(DownloadEvent.LoadUserSections)
            BooksViewType.Books -> viewModel.onEvent(DownloadEvent.LoadUserBooks)
        }
    } )
    LazyColumn(
        Modifier.fillMaxSize().padding(localPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SegmentGap),
        contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
    ) {
        item {
            SegmentedTabs(
                options = BooksViewType.entries,
                selected = downloadState.booksViewType,
                label = { stringResource(it.label) },
                onSelect = { viewModel.onEvent(DownloadEvent.OnChangeViewType(it)) },
            )
        }


        when (downloadState.booksViewType) {
            BooksViewType.Sections -> {
                itemsIndexed(downloadState.sections, key = { _, it -> it.id }) { index, it ->
                    SectionItem(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        item = it,
                        onClick = { navigateToSectionBooksScreen(it.name, "remote") },
                        index = index,
                        count = downloadState.sections.size,
                    )
                }
            }

            BooksViewType.Books -> {
                val booksList = downloadState.groupedBooks
                booksList.forEach { (initial, books) ->
                    stickyHeader {
                        CharacterHeader(
                            modifier = Modifier,
                            char = initial
                        )
                    }
                    itemsIndexed(books, key = { _, it -> it.id }) { index, it ->
                        BookItem(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .animateItem(),
                            index = index,
                            count = books.size,
                            onClick = { navigateToBookDetails(it) },
                            icon = {
                                DownloadIconButton(
                                    bookId = it.id,
                                    downloadStatuses = downloadState.downloadStatuses,
                                    downloadedBookIds = downloadState.downloadedBookIds,
                                    onDownloadClick = {
                                        viewModel.onEvent(DownloadEvent.OnClickDownloadBook(it))
                                    },
                                    onCancelClick = {
                                        viewModel.onEvent(DownloadEvent.OnClickCancelDownload(it.id))
                                    },
                                )
                            },
                            item = it,
                            onInfoClick = { navigateToBookDetails(it) }
                        )
                    }
                }
                if (downloadState.isLoadingBooks) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            LoadingIndicator()
                        }
                    }
                }
            }
        }
    }

}

