package com.shamela.library.presentation.screens.download

import com.shamela.library.data.remote.shamela.ShamelaCatalogState
import com.shamela.library.domain.model.Book
import com.shamela.library.domain.model.Category
import com.shamela.library.domain.model.DownloadStatus
import com.shamela.library.presentation.screens.library.BooksViewType

/** The lists of the merged catalog. */
data class CatalogLists(
    val books: List<Book> = emptyList(),
    val groupedBooks: Map<Char, List<Book>> = emptyMap(),
    val sections: List<Category> = emptyList(),
    val isLoadingBooks: Boolean = false,
)

data class DownloadState(
    val booksViewType: BooksViewType = BooksViewType.Sections,
    val lists: CatalogLists = CatalogLists(),
    /** Loading the shamela.ws part of the catalog; Kütüphane 1 is listed meanwhile. */
    val shamelaCatalog: ShamelaCatalogState = ShamelaCatalogState.Idle,
    val downloadStatuses: Map<String, DownloadStatus> = emptyMap(),
    val downloadedBookIds: Set<String> = emptySet(),
)
