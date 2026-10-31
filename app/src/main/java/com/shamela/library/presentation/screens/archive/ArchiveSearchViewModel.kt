package com.shamela.library.presentation.screens.archive

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shamela.library.data.local.files.FilesBooksRepoImpl
import com.shamela.library.data.local.files.FilesRepoImpl
import com.shamela.library.data.remote.archive.ArchiveItem
import com.shamela.library.data.remote.archive.ArchiveOrg
import com.shamela.library.data.remote.archive.ArchiveVolume
import com.shamela.library.domain.model.Book
import com.shamela.library.domain.model.DownloadStatus
import com.shamela.library.domain.usecases.books.BooksUseCases
import com.shamela.library.presentation.utils.BooksDownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Searches archive.org for scanned books and downloads their volumes (see [ArchiveOrg]). */
@HiltViewModel
class ArchiveSearchViewModel @Inject constructor(
    handle: SavedStateHandle,
    @FilesRepoImpl private val libraryUseCases: BooksUseCases,
) : ViewModel() {

    data class State(
        val query: String = "",
        val searching: Boolean = false,
        /** Null until a search ran. */
        val results: List<ArchiveItem>? = null,
        val failed: Boolean = false,
        /** The item whose volumes are shown. */
        val openId: String? = null,
        /** Volumes per item id; an empty list: the item has no downloadable PDF. */
        val volumes: Map<String, List<ArchiveVolume>> = emptyMap(),
        val loadingVolumes: Boolean = false,
        val downloadStatuses: Map<String, DownloadStatus> = emptyMap(),
        val downloadedIds: Set<String> = emptySet(),
    )

    private val _state = MutableStateFlow(State(query = handle.get<String>("query").orEmpty()))
    val state = _state.asStateFlow()
    private var searchJob: Job? = null

    init {
        if (_state.value.query.isNotBlank()) search()
        viewModelScope.launch {
            combine(BooksDownloadManager.downloadStatusFlow, libraryUseCases.getDownloadedBooks()) { statuses, books ->
                statuses to books.mapTo(HashSet()) { it.id }
            }.collect { (statuses, ids) ->
                _state.update { it.copy(downloadStatuses = statuses, downloadedIds = ids) }
            }
        }
    }

    fun onQueryChanged(query: String) = _state.update { it.copy(query = query) }

    fun search() {
        val query = _state.value.query.trim()
        if (query.isEmpty()) return
        searchJob?.cancel()
        _state.update { it.copy(searching = true, failed = false, openId = null) }
        searchJob = viewModelScope.launch {
            val results = try {
                ArchiveOrg.search(query)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            _state.update { it.copy(searching = false, results = results ?: it.results, failed = results == null) }
        }
    }

    /** Shows (or hides) an item's volumes, loading them the first time. */
    fun toggle(item: ArchiveItem) {
        if (_state.value.openId == item.id) {
            _state.update { it.copy(openId = null) }
            return
        }
        _state.update { it.copy(openId = item.id) }
        if (item.id in _state.value.volumes) return
        _state.update { it.copy(loadingVolumes = true) }
        viewModelScope.launch {
            val volumes = try {
                ArchiveOrg.volumes(item.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            _state.update {
                it.copy(
                    loadingVolumes = false,
                    volumes = if (volumes != null) it.volumes + (item.id to volumes) else it.volumes,
                    failed = volumes == null,
                )
            }
        }
    }

    fun bookOf(item: ArchiveItem, volume: ArchiveVolume): Book =
        ArchiveOrg.bookOf(item, volume, _state.value.volumes[item.id]?.size ?: 1)

    fun download(item: ArchiveItem, volume: ArchiveVolume) {
        val book = bookOf(item, volume)
        BooksDownloadManager.markPreparing(book.id)
        BooksDownloadManager.downloadBook(Uri.parse(volume.url), book, book.categoryName)
    }

    fun downloadAll(item: ArchiveItem) {
        val state = _state.value
        state.volumes[item.id].orEmpty()
            .filter { bookOf(item, it).id !in state.downloadedIds && state.downloadStatuses[bookOf(item, it).id]?.isActive != true }
            .forEach { download(item, it) }
    }

    fun cancel(book: Book) = BooksDownloadManager.cancelDownload(book.id)

    fun open(book: Book) {
        FilesBooksRepoImpl.openEpub(book) {}
    }
}
