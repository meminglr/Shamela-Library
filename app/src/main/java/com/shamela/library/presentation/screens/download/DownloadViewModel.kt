package com.shamela.library.presentation.screens.download


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shamela.library.data.local.assets.AssetsRepoImpl
import com.shamela.library.data.remote.shamela.ShamelaBooksRepoImpl
import com.shamela.library.data.remote.shamela.ShamelaCatalogState
import com.shamela.library.data.remote.shamela.ShamelaRepoImpl
import com.shamela.library.domain.model.Book
import com.shamela.library.domain.model.DownloadStatus
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class DownloadViewModel @Inject constructor(
    @AssetsRepoImpl private val booksUseCases: BooksUseCases,
    @ShamelaRepoImpl private val shamelaBooksUseCases: BooksUseCases,
    private val shamelaRepo: ShamelaBooksRepoImpl,
) : ViewModel() {
    private val _downloadState = MutableStateFlow(DownloadState())
    val downloadState = _downloadState.asStateFlow()

    private fun useCasesOf(source: DownloadSource) =
        if (source == DownloadSource.Shamela) shamelaBooksUseCases else booksUseCases

    private fun updateLists(source: DownloadSource, change: (CatalogLists) -> CatalogLists) =
        _downloadState.update {
            if (source == DownloadSource.Shamela) it.copy(shamela = change(it.shamela))
            else it.copy(library = change(it.library))
        }

    fun onEvent(event: DownloadEvent) {
        when (event) {
            is DownloadEvent.OnChangeViewType -> {
                _downloadState.update { it.copy(booksViewType = event.newBooksViewType) }
                loadCurrent()
            }

            is DownloadEvent.OnChangeSource -> {
                _downloadState.update { it.copy(source = event.source) }
                loadCurrent()
            }

            DownloadEvent.LoadUserSections -> {
                val source = _downloadState.value.source
                if (_downloadState.value.lists.sections.isEmpty())
                    viewModelScope.launch {
                        withContext(Dispatchers.IO) {
                            val sections = useCasesOf(source).getAllCategories().toList()
                            updateLists(source) { it.copy(sections = sections) }
                        }
                    }
            }

            DownloadEvent.LoadUserBooks -> {
                val source = _downloadState.value.source
                val lists = _downloadState.value.lists
                if (lists.books.isEmpty() && !lists.isLoadingBooks) {
                    updateLists(source) { it.copy(isLoadingBooks = true) }
                    viewModelScope.launch(Dispatchers.IO) {
                        // Publish the list once, fully sorted. Publishing per-category snapshots made
                        // rows jump under the user's finger and download the wrong book.
                        val allBooks = useCasesOf(source).getAllBooks().toList()
                        val grouped = BooksGroupingUtil.groupByFirstChar(allBooks)
                        updateLists(source) {
                            it.copy(books = allBooks, groupedBooks = grouped, isLoadingBooks = false)
                        }
                    }
                }
            }

            DownloadEvent.RefreshShamelaCatalog -> {
                viewModelScope.launch(Dispatchers.IO) {
                    shamelaRepo.refreshCatalog()
                    // Lists are rebuilt from the new catalog.
                    _downloadState.update { it.copy(shamela = CatalogLists()) }
                    if (_downloadState.value.source == DownloadSource.Shamela) loadCurrent()
                }
            }

            is DownloadEvent.OnClickDownloadBook -> {
                val useCases = useCasesOf(_downloadState.value.source)
                BooksDownloadManager.markPreparing(event.book.id)
                viewModelScope.launch {
                    useCases.getDownloadUri(event.book.categoryName, event.book.title)?.let { uri ->
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

    init {
        loadCurrent()
        viewModelScope.launch {
            shamelaRepo.catalogState.collect { catalog ->
                _downloadState.update { it.copy(shamelaCatalog = catalog) }
            }
        }
        // Passed shamela.ws's browser check: load the catalog if that is what had failed.
        viewModelScope.launch {
            ShamelaVerifyActivity.verified.collect {
                if (_downloadState.value.shamelaCatalog is ShamelaCatalogState.Failed) {
                    _downloadState.update { it.copy(shamela = CatalogLists()) }
                    if (_downloadState.value.source == DownloadSource.Shamela) loadCurrent()
                }
            }
        }
        viewModelScope.launch {
            combine(
                BooksDownloadManager.downloadStatusFlow,
                booksUseCases.getDownloadedBooks()
            ) { statusMap, downloadedList ->
                Pair(statusMap, downloadedList.map { it.id }.toSet())
            }.collect { (statusMap, downloadedIds) ->
                _downloadState.update {
                    it.copy(downloadStatuses = statusMap, downloadedBookIds = downloadedIds)
                }
            }
        }
    }


}
