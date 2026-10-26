package com.shamela.library.presentation.screens.download

import com.shamela.library.data.remote.shamela.ShamelaCatalogState
import com.shamela.library.domain.model.Book
import com.shamela.library.domain.model.Category
import com.shamela.library.domain.model.DownloadStatus
import com.shamela.library.presentation.screens.library.BooksViewType

/** Where the download screen lists books from. */
enum class DownloadSource {
    /** The app's own catalog of ready EPUBs (R2). */
    Library,
    /** shamela.ws: books are built on the device from the site's pages. */
    Shamela,
}

/** The lists of one source. */
data class CatalogLists(
    val books: List<Book> = emptyList(),
    val groupedBooks: Map<Char, List<Book>> = emptyMap(),
    val sections: List<Category> = emptyList(),
    val isLoadingBooks: Boolean = false,
)

data class DownloadState(
    val source: DownloadSource = DownloadSource.Library,
    val booksViewType: BooksViewType = BooksViewType.Sections,
    val library: CatalogLists = CatalogLists(),
    val shamela: CatalogLists = CatalogLists(),
    val shamelaCatalog: ShamelaCatalogState = ShamelaCatalogState.Idle,
    val downloadStatuses: Map<String, DownloadStatus> = emptyMap(),
    val downloadedBookIds: Set<String> = emptySet(),
) {
    val lists: CatalogLists get() = if (source == DownloadSource.Shamela) shamela else library
}
