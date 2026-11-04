package com.shamela.library.presentation.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shamela.library.data.local.assets.AssetsBooksRepoImpl
import com.shamela.library.data.catalog.BookSource
import com.shamela.library.data.catalog.CatalogBooksRepoImpl
import com.shamela.library.data.catalog.CatalogRepoImpl
import com.shamela.library.data.catalog.CatalogSources
import com.shamela.library.data.local.files.FilesBooksRepoImpl
import com.shamela.library.data.local.files.FilesRepoImpl
import com.shamela.library.data.remote.openiti.OpenItiBooksRepoImpl
import com.shamela.library.data.remote.shamela.ShamelaBooksRepoImpl
import com.shamela.library.data.remote.shamela.ShamelaRepoImpl
import com.shamela.library.domain.model.Book
import com.shamela.library.domain.model.BookDetails
import com.shamela.library.domain.model.DownloadStatus
import com.shamela.library.domain.usecases.books.BooksUseCases
import com.shamela.library.domain.usecases.quotes.QuotesUseCases
import com.shamela.library.presentation.utils.BooksDownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import com.shamela.library.presentation.utils.ErrorReporter
import javax.inject.Inject

/**
 * Backs the book-details screen: loads the "about the book" metadata (from the enriched
 * assets/book-details JSON) and drives the bottom action button — reflecting download/downloaded
 * state and triggering download or open. Reuses the same [BooksDownloadManager] + use-cases as the
 * library/section screens, so state stays consistent app-wide (saving to Room is done by the
 * download-complete receiver, independent of this screen).
 */
@HiltViewModel
class BookDetailsViewModel @Inject constructor(
    private val assetsBooksRepo: AssetsBooksRepoImpl,
    private val catalogRepo: CatalogBooksRepoImpl,
    @CatalogRepoImpl private val remoteBooksUseCases: BooksUseCases,
    private val shamelaBooksRepo: ShamelaBooksRepoImpl,
    @ShamelaRepoImpl private val shamelaBooksUseCases: BooksUseCases,
    private val openItiBooksRepo: OpenItiBooksRepoImpl,
    @FilesRepoImpl private val localBooksUseCases: BooksUseCases,
    private val quotesUseCases: QuotesUseCases,
) : ViewModel() {

    data class State(
        val isLoading: Boolean = true,
        val details: BookDetails? = null,
        val isDownloaded: Boolean = false,
        val isDownloading: Boolean = false,
        /** Live download state (queued, progress, waiting, failed), null when idle. */
        val downloadStatus: DownloadStatus? = null,
        /** The catalog(s) the book is in: Kütüphane 1, shamela.ws, both, or OpenITI; empty if none. */
        val sources: Set<BookSource> = emptySet(),
        /** The book's copies in the other catalogs, each downloadable on its own. */
        val otherCopies: List<Copy> = emptyList(),
    )

    data class Copy(
        val source: BookSource,
        val book: Book,
        val isDownloaded: Boolean = false,
        val status: DownloadStatus? = null,
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    private var book: Book? = null
    /** The book is from the shamela.ws catalog: its card and download come from there. */
    private var fromShamela = false

    fun load(book: Book, fromShamela: Boolean = false) {
        if (this.book?.id == book.id) return   // already bound (guards recomposition)
        this.book = book
        this.fromShamela = fromShamela

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            catalogRepo.warmUp()
            val sources = CatalogSources.of(book.id)
            // A shamela.ws book (only on the site, or the site copy of a shared one, e.g. downloaded
            // before the catalogs were merged) has its card on the site.
            val siteBook = BookSource.Site in sources &&
                (BookSource.Library !in sources || CatalogSources.index.value.libraryIdOf(book.id) != null)
            if (siteBook) this@BookDetailsViewModel.fromShamela = true
            _state.update { it.copy(sources = sources) }
            // A malformed detail file (JsonSyntaxException isn't an IOException) shows "no details"
            // instead of crashing the screen.
            val details = try {
                when {
                    BookSource.OpenIti in sources -> openItiBooksRepo.getBookDetails(book.categoryName, book.title)
                    this@BookDetailsViewModel.fromShamela -> shamelaBooksRepo.getBookDetails(book.categoryName, book.title)
                    else -> assetsBooksRepo.getBookDetails(book.categoryName, book.title)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ErrorReporter.report("BookDetailsViewModel", "details of ${book.title} failed", e)
                null
            }
            _state.update { it.copy(isLoading = false, details = details) }
        }

        // Reactively track download/downloaded state for this single book.
        viewModelScope.launch {
            combine(
                BooksDownloadManager.downloadStatusFlow,
                localBooksUseCases.getDownloadedBooks(),
                CatalogSources.index,
            ) { statusMap, downloadedList, _ ->
                val siteCopy = CatalogSources.siteCopyOf(book.id)?.id
                val downloadedIds = downloadedList.mapTo(HashSet()) { it.id }
                val downloaded = book.id in downloadedIds || siteCopy in downloadedIds
                // The same book in the other catalogs (each with its own download state).
                val others = CatalogSources.copiesOf(book.id)
                    .filterValues { it.id != book.id }
                    .map { (source, copy) -> Copy(source, copy, copy.id in downloadedIds, statusMap[copy.id]) }
                    .sortedBy { it.source.number }
                Triple(downloaded, statusMap[book.id], others)
            }.collect { (downloaded, status, others) ->
                _state.update {
                    it.copy(
                        isDownloaded = downloaded,
                        isDownloading = !downloaded && status?.isActive == true,
                        downloadStatus = if (downloaded) null else status,
                        otherCopies = others,
                    )
                }
            }
        }
    }

    fun downloadBook() {
        val book = book ?: return
        // Optimistic: disable the button immediately so a double-tap can't fire two downloads
        // during the (suspend) download-uri lookup, before the Downloading status arrives.
        _state.update { it.copy(isDownloading = true) }
        BooksDownloadManager.markPreparing(book.id)
        viewModelScope.launch {
            val useCases = if (fromShamela) shamelaBooksUseCases else remoteBooksUseCases
            val uri = useCases.getDownloadUri(book.categoryName, book.title)
            if (uri != null) {
                BooksDownloadManager.downloadBook(uri, book, book.categoryName)
            } else {
                _state.update { it.copy(isDownloading = false) }
                BooksDownloadManager.reportLinkUnavailable(book.title, book.id)
            }
        }
    }

    fun cancelDownload() {
        book?.let { BooksDownloadManager.cancelDownload(it.id) }
    }

    /** Downloads the book's copy from another catalog, as a book of its own in the library. */
    fun downloadCopy(copy: Copy) {
        BooksDownloadManager.markPreparing(copy.book.id)
        viewModelScope.launch {
            val uri = catalogRepo.getDownloadLinkFrom(copy.source, copy.book)
            if (uri != null) BooksDownloadManager.downloadBook(uri, copy.book, copy.book.categoryName)
            else BooksDownloadManager.reportLinkUnavailable(copy.book.title, copy.book.id)
        }
    }

    fun cancelCopy(copy: Copy) = BooksDownloadManager.cancelDownload(copy.book.id)

    fun openCopy(copy: Copy) {
        FilesBooksRepoImpl.openEpub(book = copy.book, onAddQuoteToFavorite = { quote ->
            viewModelScope.launch { quotesUseCases.saveQuote(quote) }
            Unit
        })
    }

    fun openBook() {
        val book = book ?: return
        val saveQuote = { quote: com.shamela.library.domain.model.Quote ->
            viewModelScope.launch { quotesUseCases.saveQuote(quote) }
            Unit
        }
        // The downloaded file may be the shamela.ws copy of a book listed from Kütüphane 1.
        FilesBooksRepoImpl.openEpub(book = book, onAddQuoteToFavorite = saveQuote) ||
            CatalogSources.siteCopyOf(book.id)?.let { FilesBooksRepoImpl.openEpub(book = it, onAddQuoteToFavorite = saveQuote) } == true
    }
}
