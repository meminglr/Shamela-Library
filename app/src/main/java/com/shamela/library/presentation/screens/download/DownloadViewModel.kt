package com.shamela.library.presentation.screens.download


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shamela.library.data.catalog.CatalogRepoImpl
import com.shamela.library.data.catalog.CatalogSources
import com.shamela.library.data.remote.shamela.ShamelaBooksRepoImpl
import com.shamela.library.data.remote.shamela.ShamelaCatalogState
import com.shamela.library.domain.usecases.books.BooksUseCases
import com.shamela.library.domain.util.BooksGroupingUtil
import com.shamela.library.presentation.ShamelaVerifyActivity
import com.shamela.library.presentation.screens.library.BooksViewType
import com.shamela.library.presentation.utils.BooksDownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class DownloadViewModel @Inject constructor(
    @CatalogRepoImpl private val booksUseCases: BooksUseCases,
    private val shamelaRepo: ShamelaBooksRepoImpl,
) : ViewModel() {
    private val _downloadState = MutableStateFlow(DownloadState())
    val downloadState = _downloadState.asStateFlow()

    fun onEvent(event: DownloadEvent) {
        when (event) {
            is DownloadEvent.OnChangeViewType -> {
                _downloadState.update { it.copy(booksViewType = event.newBooksViewType) }
                loadCurrent()
            }

            DownloadEvent.LoadUserSections -> {
                if (_downloadState.value.lists.sections.isEmpty())
                    viewModelScope.launch {
                        withContext(Dispatchers.IO) {
                            val sections = booksUseCases.getAllCategories().toList()
                            _downloadState.update { it.copy(lists = it.lists.copy(sections = sections)) }
                        }
                    }
            }

            DownloadEvent.LoadUserBooks -> {
                val lists = _downloadState.value.lists
                if (lists.books.isEmpty() && !lists.isLoadingBooks) {
                    _downloadState.update { it.copy(lists = it.lists.copy(isLoadingBooks = true)) }
                    viewModelScope.launch(Dispatchers.IO) {
                        // Publish the list once, fully sorted. Publishing per-category snapshots made
                        // rows jump under the user's finger and download the wrong book.
                        val allBooks = booksUseCases.getAllBooks().toList()
                        val grouped = BooksGroupingUtil.groupByFirstChar(allBooks)
                        _downloadState.update {
                            it.copy(lists = it.lists.copy(books = allBooks, groupedBooks = grouped, isLoadingBooks = false))
                        }
                    }
                }
            }

            DownloadEvent.RefreshShamelaCatalog -> {
                viewModelScope.launch(Dispatchers.IO) {
                    shamelaRepo.refreshCatalog()
                    reloadLists()
                }
            }

            is DownloadEvent.OnClickDownloadBook -> {
                BooksDownloadManager.markPreparing(event.book.id)
                viewModelScope.launch {
                    booksUseCases.getDownloadUri(event.book.categoryName, event.book.title)?.let { uri ->
                        BooksDownloadManager.downloadBook(
                            downloadUri = uri,
                            book = event.book,
                            bookCategory = event.book.categoryName
                        )
                    } ?: BooksDownloadManager.reportLinkUnavailable(event.book.title, event.book.id)
                }
            }

            is DownloadEvent.OnClickCancelDownload -> {
                BooksDownloadManager.cancelDownload(event.bookId)
            }
        }
    }

    private fun loadCurrent() {
        when (_downloadState.value.booksViewType) {
            BooksViewType.Sections -> onEvent(DownloadEvent.LoadUserSections)
            BooksViewType.Books -> onEvent(DownloadEvent.LoadUserBooks)
        }
    }

    /** The catalog changed (shamela.ws part arrived or was refreshed): list it again. */
    private fun reloadLists() {
        _downloadState.update { it.copy(lists = CatalogLists()) }
        loadCurrent()
    }

    init {
        loadCurrent()
        viewModelScope.launch {
            var previous: ShamelaCatalogState? = null
            shamelaRepo.catalogState.collect { catalog ->
                _downloadState.update { it.copy(shamelaCatalog = catalog) }
                if (catalog is ShamelaCatalogState.Ready && previous != null && previous !is ShamelaCatalogState.Ready) {
                    reloadLists()
                }
                previous = catalog
            }
        }
        // The shamela.ws part: from the device, or fetched in the background the first time.
        viewModelScope.launch(Dispatchers.IO) {
            if (shamelaRepo.peekCatalog() == null) shamelaRepo.ensureCatalog()
        }
        // Passed shamela.ws's browser check: load the catalog if that is what had failed.
        viewModelScope.launch {
            ShamelaVerifyActivity.verified.collect {
                if (_downloadState.value.shamelaCatalog is ShamelaCatalogState.Failed) {
                    launch(Dispatchers.IO) { shamelaRepo.ensureCatalog() }
                }
            }
        }
        viewModelScope.launch {
            combine(
                BooksDownloadManager.downloadStatusFlow,
                booksUseCases.getDownloadedBooks(),
                CatalogSources.index,
            ) { statusMap, downloadedList, _ ->
                Pair(statusMap, CatalogSources.withListedIds(downloadedList.map { it.id }.toSet()))
            }
                // Matching every downloaded book against the catalogs is too much for the main thread
                // (it ran in the first frames of the screen's opening animation).
                .flowOn(Dispatchers.Default)
                .collect { (statusMap, downloadedIds) ->
                _downloadState.update {
                    it.copy(downloadStatuses = statusMap, downloadedBookIds = downloadedIds)
                }
            }
        }
    }


}
