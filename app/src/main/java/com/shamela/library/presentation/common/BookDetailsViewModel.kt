package com.shamela.library.presentation.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shamela.library.data.local.assets.AssetsBooksRepoImpl
import com.shamela.library.data.local.assets.AssetsRepoImpl
import com.shamela.library.data.local.files.FilesBooksRepoImpl
import com.shamela.library.data.local.files.FilesRepoImpl
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
    @AssetsRepoImpl private val remoteBooksUseCases: BooksUseCases,
    private val shamelaBooksRepo: ShamelaBooksRepoImpl,
    @ShamelaRepoImpl private val shamelaBooksUseCases: BooksUseCases,
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
            // A malformed detail file (JsonSyntaxException isn't an IOException) shows "no details"
            // instead of crashing the screen.
            val details = try {
                if (fromShamela) shamelaBooksRepo.getBookDetails(book.categoryName, book.title)
                else assetsBooksRepo.getBookDetails(book.categoryName, book.title)
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
            ) { statusMap, downloadedList ->
                val downloaded = downloadedList.any { it.id == book.id }
                downloaded to statusMap[book.id]
            }.collect { (downloaded, status) ->
                _state.update {
                    it.copy(
                        isDownloaded = downloaded,
                        isDownloading = !downloaded && status?.isActive == true,
                        downloadStatus = if (downloaded) null else status,
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

    fun openBook() {
        val book = book ?: return
        FilesBooksRepoImpl.openEpub(
            book = book,
            onAddQuoteToFavorite = { quote -> viewModelScope.launch { quotesUseCases.saveQuote(quote) } },
        )
    }
}
