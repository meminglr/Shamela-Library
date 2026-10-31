package com.shamela.library.presentation.screens.searchResults


import com.shamela.library.R
import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shamela.apptheme.data.db.FtsQuery
import com.shamela.library.data.catalog.CatalogRepoImpl
import com.shamela.library.data.catalog.CatalogSources
import com.shamela.library.domain.search.BookSearchMatcher
import com.shamela.library.data.local.files.FilesRepoImpl
import com.shamela.library.data.remote.shamela.ShamelaRepoImpl
import com.shamela.library.domain.model.Book
import com.shamela.library.domain.model.DownloadStatus
import com.shamela.library.domain.usecases.books.BooksUseCases
import com.shamela.library.domain.usecases.quotes.QuotesUseCases
import com.shamela.library.presentation.utils.BooksDownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchResultsViewModel @Inject constructor(
    @CatalogRepoImpl private val remoteBooksUseCases: BooksUseCases,
    @FilesRepoImpl private val localBooksUseCases: BooksUseCases,
    @ShamelaRepoImpl private val shamelaBooksUseCases: BooksUseCases,
    private val quotesUseCases: QuotesUseCases,
    private val handle: SavedStateHandle,
    private val application: Application,
) : ViewModel() {
    private val _searchResultsState = MutableStateFlow(SearchResultsState())
    val searchResultsState = _searchResultsState.asStateFlow()
    private var searchJob: Job? = null


    fun onEvent(event: SearchResultsEvent) {
        when (event) {
            is SearchResultsEvent.Search -> {
                searchJob?.cancel()
                searchJob = viewModelScope.launch {
                    event.query.trim().let { query ->
                        _searchResultsState.update {
                            it.copy(
                                isLoading = true,
                                booksResultsList = emptyList(),
                                sectionsResultsList = emptyList(),
                                isListEmpty = false,
                                showsClosest = false,
                            )
                        }
                        val categoryName =
                            handle.get<String>("categoryName").toString().ifBlank { "all" }
                        val type = handle.get<String>("type").toString()
                        _searchResultsState.update { it.copy(type = type) }
                        val repo = when (type) {
                            "local" -> localBooksUseCases
                            "remote" -> remoteBooksUseCases
                            "shamela" -> shamelaBooksUseCases
                            else -> localBooksUseCases
                        }
                        // Download catalogs: matching sections are listed above the matching books.
                        if (type == "remote" || type == "shamela") {
                            val normalizedQuery = FtsQuery.normalized(query)
                            val sections = repo.getAllCategories().filter { category ->
                                FtsQuery.normalized(category.name).contains(normalizedQuery)
                            }.toList()
                            _searchResultsState.update {
                                it.copy(
                                    sectionsResultsList = sections,
                                    lastQuery = if (sections.isNotEmpty()) query else ""
                                )
                            }
                        }
                        repo.searchForABook(categoryName, query).collect { book ->
                            _searchResultsState.update {
                                it.copy(
                                    booksResultsList = it.booksResultsList + book,
                                    lastQuery = query
                                )
                            }
                        }
                        _searchResultsState.update {
                            it.copy(
                                isLoading = false,
                                // Also when nothing was found: the screen then offers the scanned books.
                                lastQuery = query,
                                isListEmpty = it.booksResultsList.isEmpty() && it.sectionsResultsList.isEmpty(),
                                showsClosest = it.booksResultsList.isNotEmpty() &&
                                    it.booksResultsList.all { book -> BookSearchMatcher.isClosest(book, query) },
                            )
                        }
                    }
                }
            }

            is SearchResultsEvent.OnClickDownloadBook -> {
                BooksDownloadManager.markPreparing(event.book.id)
                viewModelScope.launch {
                    val catalog = if (_searchResultsState.value.type == "shamela") shamelaBooksUseCases else remoteBooksUseCases
                    catalog.getDownloadUri(event.book.categoryName, event.book.title)
                        ?.let { uri ->
                            BooksDownloadManager.downloadBook(
                                downloadUri = uri,
                                book = event.book,
                                bookCategory = event.book.categoryName
                            )
                        } ?: BooksDownloadManager.reportLinkUnavailable(event.book.title, event.book.id)
                }
            }

            is SearchResultsEvent.OnClickCancelDownload -> {
                BooksDownloadManager.cancelDownload(event.bookId)
            }

            is SearchResultsEvent.OnSearchQueryChanged -> {
                _searchResultsState.update { it.copy(query = event.newSearchQuery) }
            }

            SearchResultsEvent.ClearSearchQuery -> {
                _searchResultsState.update { it.copy(query = "", isLoading = false) }
                searchJob?.cancel()
            }

            is SearchResultsEvent.AddQuoteToFavorite -> {
                viewModelScope.launch {
                    quotesUseCases.saveQuote(event.quote)
                    Toast.makeText(application, application.getString(R.string.quote_added), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    init {
        viewModelScope.launch {
            combine(
                BooksDownloadManager.downloadStatusFlow,
                localBooksUseCases.getDownloadedBooks(),
                CatalogSources.index,
            ) { statusMap, downloadedList, _ ->
                Pair(statusMap, CatalogSources.withListedIds(downloadedList.map { it.id }.toSet()))
            }.collect { (statusMap, downloadedIds) ->
                _searchResultsState.update {
                    it.copy(downloadStatuses = statusMap, downloadedBookIds = downloadedIds)
                }
            }
        }
    }


}
