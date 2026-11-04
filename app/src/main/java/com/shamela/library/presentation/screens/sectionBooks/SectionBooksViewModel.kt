@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.shamela.library.presentation.screens.sectionBooks


import com.shamela.library.R
import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shamela.library.data.catalog.CatalogRepoImpl
import com.shamela.library.data.catalog.CatalogSources
import com.shamela.library.data.catalog.SiteSections
import com.shamela.library.data.local.files.FilesRepoImpl
import com.shamela.library.data.remote.shamela.ShamelaRepoImpl
import com.shamela.library.domain.model.Book
import com.shamela.library.domain.model.DownloadStatus
import com.shamela.library.domain.usecases.books.BooksUseCases
import com.shamela.library.domain.usecases.quotes.QuotesUseCases
import com.shamela.library.domain.util.BookSortOption
import com.shamela.library.presentation.utils.BooksDownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.distinctUntilChanged
import com.shamela.library.domain.util.BookSorter
import kotlinx.coroutines.launch
import com.shamela.library.presentation.utils.BookDeletionManager
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SectionBooksViewModel @Inject constructor(
    @CatalogRepoImpl private val remoteBooksUseCases: BooksUseCases,
    @FilesRepoImpl private val localBooksUseCases: BooksUseCases,
    @ShamelaRepoImpl private val shamelaBooksUseCases: BooksUseCases,
    private val handle: SavedStateHandle,
    private val quotesUseCases: QuotesUseCases,
    private val application: Application
) : ViewModel(), BooksDownloadManager.Subscriber {
    private val _sectionBooksState = MutableStateFlow(SectionBooksState())
    val sectionBooksState = _sectionBooksState.asStateFlow()

    /** The catalog books of this section come from: shamela.ws alone for "shamela", else the merged catalog. */
    private val catalogUseCases: BooksUseCases
        get() = if (handle.get<String>("type") == "shamela") shamelaBooksUseCases else remoteBooksUseCases

    fun onEvent(event: SectionBooksEvent) {
        when (event) {
            SectionBooksEvent.OnClickDownloadSection -> {
                viewModelScope.launch {
                    val categoryName = handle.get<String>("categoryName").toString()
                    if (sectionBooksState.value.books.isEmpty()) {
                        loadRemoteBooksOfSection(categoryName)
                    }
                    val bookUriMap = sectionBooksState.value.books.values.associateWith { book ->
                        async { catalogUseCases.getDownloadUri(book.categoryName, book.title) }
                    }
                    val bookUriList = withContext(coroutineContext) {
                        bookUriMap.mapValues { it.value.await() }
                    }
                    Log.d("SectionBooksViewModel", "bookUriList: ${bookUriList.values}")
                    BooksDownloadManager.downloadSection(bookUriList)
                    _sectionBooksState.update { it.copy(isDownloadButtonEnabled = false) }
                }
            }

            is SectionBooksEvent.OnClickDownloadBook -> {
                BooksDownloadManager.markPreparing(event.book.id)
                viewModelScope.launch {
                    catalogUseCases.getDownloadUri(event.book.categoryName, event.book.title)
                        ?.let { uri ->
                            BooksDownloadManager.downloadBook(
                                downloadUri = uri,
                                book = event.book,
                                bookCategory = event.book.categoryName
                            )
                        } ?: BooksDownloadManager.reportLinkUnavailable(event.book.title, event.book.id)
                }
            }

            is SectionBooksEvent.OnClickCancelDownload -> {
                BooksDownloadManager.cancelDownload(event.bookId)
            }

            is SectionBooksEvent.AddQuoteToFavorite -> {
                viewModelScope.launch {
                    quotesUseCases.saveQuote(event.quote)
                    Toast.makeText(application, application.getString(R.string.quote_added), Toast.LENGTH_SHORT).show()
                }
            }

            is SectionBooksEvent.OnChangeSortOption -> {
                _sectionBooksState.update {
                    it.copy(
                        sortOption = event.option,
                        sortAscending = event.option.defaultAscending
                    )
                }
                if (event.option == BookSortOption.DOWNLOAD_TIME) ensureDownloadTimes()
            }

            SectionBooksEvent.OnToggleSortDirection -> {
                _sectionBooksState.update { it.copy(sortAscending = !it.sortAscending) }
            }

            is SectionBooksEvent.ToggleFavorite -> {
                val favorite = event.book.id !in sectionBooksState.value.favoriteIds
                _sectionBooksState.update {
                    it.copy(favoriteIds = if (favorite) it.favoriteIds + event.book.id else it.favoriteIds - event.book.id)
                }
                viewModelScope.launch { localBooksUseCases.updateBook(event.book.id, if (favorite) 1 else 0) }
            }

            is SectionBooksEvent.ToggleSelection -> _sectionBooksState.update {
                it.copy(
                    selectedIds = if (event.bookId in it.selectedIds) it.selectedIds - event.bookId
                    else it.selectedIds + event.bookId
                )
            }

            SectionBooksEvent.SelectAll -> _sectionBooksState.update { it.copy(selectedIds = it.books.keys) }

            SectionBooksEvent.CancelSelection -> _sectionBooksState.update { it.copy(selectedIds = emptySet()) }


            is SectionBooksEvent.DeleteBooks -> {
                val books = event.bookIds.mapNotNull { sectionBooksState.value.books[it] }
                _sectionBooksState.update { it.copy(selectedIds = emptySet()) }
                // Runs app-wide in the background; the floating bar shows the progress.
                BookDeletionManager.delete(books, deleteBook = { localBooksUseCases.deleteBook(it) })
            }
        }
    }

    private var downloadTimesJob: Job? = null

    /**
     * Lazily fills [SectionBooksState.downloadTimes] with each local book's file last-modified time.
     * Only meaningful for local sections (remote books have no file); runs on IO since StrictMode
     * forbids file access on the main thread.
     */
    private fun ensureDownloadTimes() {
        if (sectionBooksState.value.type != "local") return
        if (downloadTimesJob?.isActive == true) return
        downloadTimesJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val current = _sectionBooksState.value
                val missing = current.books.keys - current.downloadTimes.keys
                if (missing.isEmpty()) break
                val newTimes = missing.associateWith { id ->
                    val book = current.books[id] ?: return@associateWith 0L
                    File(BooksDownloadManager.getBookPath(book))
                        .takeIf { it.isFile }?.lastModified() ?: 0L
                }
                _sectionBooksState.update { it.copy(downloadTimes = it.downloadTimes + newTimes) }
            }
        }
    }

    private suspend fun loadRemoteBooksOfSection(categoryName: String) {
        // In one update: book by book, a 344-book section copied the map and recomposed the screen
        // 344 times, freezing the opening animation's first frames.
        val books = catalogUseCases.getBooksByCategory(categoryName = categoryName).toList()
        _sectionBooksState.update {
            it.copy(books = it.books + books.associateBy { book -> book.id }, isLoading = false)
        }
    }

    /**
     * A local section is read from the database, which already holds each book's author and page
     * count: opening it no longer parses every EPUB of the section. The list follows the table, so
     * books deleted (here or anywhere) leave it as they go, and an emptied section closes.
     */
    private suspend fun observeLocalBooksOfSection(categoryName: String) {
        // The section's own folder and the shamela.ws sections shown under it.
        localBooksUseCases.booksDao.getBooksInCategories(SiteSections.foldersOf(categoryName)).collect { list ->
            val books = list.associateBy { it.id }
            _sectionBooksState.update {
                it.copy(
                    books = books,
                    selectedIds = it.selectedIds intersect books.keys,
                    isLoading = false,
                    isSectionEmptied = it.books.isNotEmpty() && books.isEmpty(),
                )
            }
            if (_sectionBooksState.value.sortOption == BookSortOption.DOWNLOAD_TIME) {
                ensureDownloadTimes()
            }
        }
    }

    init {
        // Loaded once per screen (not on every return to it, e.g. back from a book's details).
        val categoryName = handle.get<String>("categoryName").orEmpty()
        val type = handle.get<String>("type").orEmpty()
        _sectionBooksState.update { it.copy(type = type) }
        // Sorted off the main thread whenever the books or the sort change.
        viewModelScope.launch {
            _sectionBooksState
                .map { SortInputs(it.books, it.sortOption, it.sortAscending, it.downloadTimes) }
                .distinctUntilChanged()
                .mapLatest { (books, option, ascending, times) ->
                    BookSorter.sortBooks(books.values.toList(), option, ascending, times)
                }
                .flowOn(Dispatchers.Default)
                .collect { sorted -> _sectionBooksState.update { it.copy(sortedBooks = sorted) } }
        }
        viewModelScope.launch {
            when (type) {
                "local" -> observeLocalBooksOfSection(categoryName)
                "remote", "shamela" -> loadRemoteBooksOfSection(categoryName)
            }
        }
        BooksDownloadManager.subscribe(this)
        viewModelScope.launch {
            combine(
                BooksDownloadManager.downloadStatusFlow,
                localBooksUseCases.getDownloadedBooks(),
                CatalogSources.index,
            ) { statusMap, downloadedList, _ ->
                Triple(
                    statusMap,
                    CatalogSources.withListedIds(downloadedList.map { it.id }.toSet()),
                    downloadedList.filter { it.isFavorite }.map { it.id }.toSet(),
                )
            }
                // Matching every downloaded book against the catalogs is too much for the main thread
                // (it ran in the first frames of the screen's opening animation).
                .flowOn(Dispatchers.Default)
                .collect { (statusMap, downloadedIds, favoriteIds) ->
                _sectionBooksState.update {
                    it.copy(downloadStatuses = statusMap, downloadedBookIds = downloadedIds, favoriteIds = favoriteIds)
                }
            }
        }
    }

    private data class SortInputs(
        val books: Map<String, Book>,
        val option: BookSortOption,
        val ascending: Boolean,
        val times: Map<String, Long>,
    )

    override fun onCleared() {
        super.onCleared()
        BooksDownloadManager.unsubscribe(this)
    }

    override fun onBookDownloaded(book: Book, isLastBook: Boolean) {
        if (!sectionBooksState.value.isDownloadButtonEnabled && isLastBook) {
            _sectionBooksState.update { it.copy(isDownloadButtonEnabled = true) }
        }
    }
}
