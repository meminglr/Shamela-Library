@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.shamela.library.presentation.screens.library


import com.shamela.library.data.catalog.CatalogBooksRepoImpl
import com.shamela.library.R
import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shamela.library.data.local.files.FilesRepoImpl
import com.shamela.library.domain.model.Book
import com.shamela.library.domain.usecases.books.BooksUseCases
import com.shamela.library.domain.usecases.quotes.QuotesUseCases
import com.shamela.library.domain.util.BookSortOption
import com.shamela.library.presentation.utils.BookIndexing
import com.shamela.library.presentation.utils.BooksDownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import com.shamela.library.data.remote.shamela.TocRetrofit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import com.shamela.library.presentation.utils.ErrorReporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.distinctUntilChanged
import com.shamela.library.domain.util.BookSorter
import kotlinx.coroutines.launch
import com.shamela.library.presentation.utils.BookDeletionManager
import kotlinx.coroutines.withContext
import com.shamela.apptheme.data.search.BookIndexer
import com.shamela.library.data.local.files.FilesBooksRepoImpl
import java.io.File
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    @FilesRepoImpl private val booksUseCases: BooksUseCases,
    private val quotesUseCases: QuotesUseCases,
    private val application: Application,
    private val catalogRepo: CatalogBooksRepoImpl,
) : ViewModel(), BooksDownloadManager.Subscriber {
    private val _libraryState = MutableStateFlow<LibraryState>(LibraryState())
    val libraryState = _libraryState.asStateFlow()

    /** Map of Book Id -> isFavorite*/

    init {
        onEvent(LibraryEvent.LoadUserBooksAndSections)
        // Match the two catalogs in the background, for the source numbers on the library's books.
        viewModelScope.launch(Dispatchers.IO) { catalogRepo.warmUp() }
        // The sorted list is rebuilt off the main thread whenever its inputs change (it used to be
        // sorted in composition: an Arabic collation of the whole library on every change).
        viewModelScope.launch {
            _libraryState
                .map { SortInputs(it.books, it.sortOption, it.sortAscending, it.downloadTimes) }
                .distinctUntilChanged()
                .mapLatest { (books, option, ascending, times) ->
                    BookSorter.sortBooks(books.values.toList(), option, ascending, times)
                }
                .flowOn(Dispatchers.Default)
                .collect { sorted -> _libraryState.update { it.copy(sortedBooks = sorted) } }
        }
        BooksDownloadManager.subscribe(this)
    }

    fun onEvent(event: LibraryEvent) {
        when (event) {
            is LibraryEvent.OnChangeViewType -> {
                _libraryState.update { it.copy(booksViewType = event.newBooksViewType) }
            }

            LibraryEvent.LoadUserBooksAndSections -> {
                viewModelScope.launch {
                    Log.d("Shamela", "LibraryViewModel: loading Books")
                    launch {
                        // A failed sync (unreadable file, full disk) must not crash the app: the
                        // books already in the database are still listed.
                        try {
                            syncLibraryFolder()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            ErrorReporter.report("LibraryViewModel", "library folder sync failed", e)
                        }
                        // Books saved with a flat page list as their contents get a generated one.
                        withContext(Dispatchers.IO) {
                            BookIndexing.libraryFiles().forEach { file ->
                                if (TocRetrofit.upgrade(file)) Log.d("Shamela", "table of contents made for ${file.name}")
                            }
                        }
                        // Books downloaded while the app was killed (or indexed by an older version)
                        // have no full-text index yet; build it in the background.
                        BookIndexing.indexMissing(application)
                    }
                    booksUseCases.getDownloadedBooks().onEach {
                        val databaseBooks = it.associateBy { book -> book.id }
                        val idsChanged = databaseBooks.keys != _libraryState.value.books.keys
                        // The database is the source of truth: books deleted elsewhere (e.g. "delete
                        // all" in settings) must disappear here too.
                        _libraryState.update { state ->
                            state.copy(
                                books = databaseBooks,
                                selectedIds = state.selectedIds intersect databaseBooks.keys,
                                isLoading = false
                            )
                        }
                        if (idsChanged) scheduleSectionsReload()
                        // Keep download times in sync with newly arrived books while that sort is active.
                        if (_libraryState.value.sortOption == BookSortOption.DOWNLOAD_TIME) {
                            ensureDownloadTimes()
                        }
                    }.launchIn(this)
                    launch {
                        booksUseCases.getAllCategories().collect { category ->
                            _libraryState.update {
                                it.copy(
                                    // Loading ends with the book list (above); a section arriving first
                                    // must not flash the "no books yet" screen.
                                    sections = it.sections + mapOf(category.id to category),
                                )
                            }
                        }
                    }
                }
            }

            is LibraryEvent.ToggleFavorite -> {
                val book = event.book
                val newState = !book.isFavorite
                _libraryState.update {
                    it.copy(
                        books = it.books + mapOf(book.id to book.copy(isFavorite = newState)),
                    )
                }
                viewModelScope.launch {
                    booksUseCases.updateBook(book.id, if (newState) 1 else 0)
                }
            }

            is LibraryEvent.AddQuoteToFavorite -> {
                viewModelScope.launch {
                    quotesUseCases.saveQuote(event.quote)
                    Toast.makeText(application, application.getString(R.string.quote_added), Toast.LENGTH_SHORT).show()
                }
            }

            is LibraryEvent.DeleteBook -> deleteBooks(listOf(event.book))

            is LibraryEvent.SelectBook -> _libraryState.update {
                it.copy(
                    selectedIds = if (event.bookId in it.selectedIds) it.selectedIds - event.bookId
                    else it.selectedIds + event.bookId
                )
            }

            LibraryEvent.SelectAll -> _libraryState.update { it.copy(selectedIds = it.books.keys) }

            LibraryEvent.CancelSelection -> _libraryState.update { it.copy(selectedIds = emptySet()) }

            LibraryEvent.DeleteSelectedBooks -> {
                val state = libraryState.value
                val selected = state.selectedIds.mapNotNull { state.books[it] }
                _libraryState.update { it.copy(selectedIds = emptySet()) }
                deleteBooks(selected)
            }

            is LibraryEvent.OnChangeSortOption -> {
                _libraryState.update {
                    it.copy(
                        sortOption = event.option,
                        sortAscending = event.option.defaultAscending
                    )
                }
                if (event.option == BookSortOption.DOWNLOAD_TIME) ensureDownloadTimes()
            }

            LibraryEvent.OnToggleSortDirection -> {
                _libraryState.update { it.copy(sortAscending = !it.sortAscending) }
            }
        }
    }

    /** Deletes books in the background, app-wide; the floating bar shows the progress. */
    private fun deleteBooks(books: List<Book>) {
        BookDeletionManager.delete(books, deleteBook = { booksUseCases.deleteBook(it) })
    }

    private var sectionsJob: Job? = null

    /**
     * Recounts the sections once the book list settles. Deleting a whole section changes the list
     * once per book; overlapping recounts could finish out of order and leave a stale count.
     */
    private fun scheduleSectionsReload() {
        sectionsJob?.cancel()
        sectionsJob = viewModelScope.launch {
            kotlinx.coroutines.delay(200)
            reloadSections()
        }
    }

    private suspend fun reloadSections() {
        val sections = booksUseCases.getAllCategories().toList()
        _libraryState.update {
            it.copy(sections = sections.associateBy { category -> category.id }, isLoading = false)
        }
    }

    private var downloadTimesJob: Job? = null

    /**
     * Lazily fills [LibraryState.downloadTimes] with the file last-modified time of every book that
     * doesn't have one yet. mtime never changes, so this only ever stats files once per book. Runs
     * on IO (StrictMode forbids file access on the main thread) and loops to absorb books that arrive
     * while it's working.
     */
    private fun ensureDownloadTimes() {
        if (downloadTimesJob?.isActive == true) return
        downloadTimesJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val current = _libraryState.value
                val missing = current.books.keys - current.downloadTimes.keys
                if (missing.isEmpty()) break
                val newTimes = missing.associateWith { id ->
                    val book = current.books[id] ?: return@associateWith 0L
                    File(BooksDownloadManager.getBookPath(book))
                        .takeIf { it.isFile }?.lastModified() ?: 0L
                }
                _libraryState.update { it.copy(downloadTimes = it.downloadTimes + newTimes) }
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
        _libraryState.update {
            it.copy(
                books = it.books + (book.id to book),
                isLoading = false
            )
        }
        if (_libraryState.value.sortOption == BookSortOption.DOWNLOAD_TIME) ensureDownloadTimes()
        scheduleSectionsReload()
    }

    /**
     * Adds books found in the library folder but missing from the database (files added from
     * outside, or app data cleared). Only those files are parsed, and they're saved in batches:
     * parsing every book on every launch took long with a large library, and saving them one by
     * one refreshed every book list a thousand times.
     */
    private suspend fun syncLibraryFolder() = withContext(Dispatchers.IO) {
        val dao = booksUseCases.booksDao
        val known = dao.getDownloadedBooksOnce().filter { it.pageCount > 0 }.mapTo(HashSet()) { it.id }
        val batch = ArrayList<Book>()
        BookIndexing.libraryFiles().forEach { file ->
            if (BookIndexer.bookIdOf(file) in known) return@forEach
            val category = file.parentFile?.name ?: return@forEach
            FilesBooksRepoImpl.parseBook(file, category)?.let { batch += it }
            if (batch.size >= 50) {
                dao.upsertAllKeepingUserData(batch.toList())
                batch.clear()
            }
        }
        // Scanned books (PDF) aren't in the full-text index's file list.
        FilesBooksRepoImpl.libraryPdfFiles().forEach { file ->
            val category = file.parentFile?.name ?: return@forEach
            val id = java.util.UUID.nameUUIDFromBytes((FilesBooksRepoImpl.titleOf(file) + category).toByteArray()).toString()
            if (id in known) return@forEach
            FilesBooksRepoImpl.parsePdf(file, category)?.let { batch += it }
        }
        if (batch.isNotEmpty()) dao.upsertAllKeepingUserData(batch)
    }
}
