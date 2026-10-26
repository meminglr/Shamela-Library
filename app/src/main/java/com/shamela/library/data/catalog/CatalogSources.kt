package com.shamela.library.data.catalog

import com.shamela.library.domain.model.Book
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The two catalogs a book can come from; [number] is what the app shows for it. */
enum class BookSource(val number: Int) {
    /** Kütüphane 1: the app's catalog of ready EPUBs. */
    Library(1),
    /** Kütüphane 2: shamela.ws, built into an EPUB on the device. */
    Site(2),
}

/**
 * Which catalog(s) each book id belongs to, for the source numbers on book rows and details. Ids are
 * those of [com.shamela.library.domain.model.Book], which a downloaded book keeps, so the numbers
 * show in the library too. Filled by [CatalogBooksRepoImpl] once it has matched the catalogs.
 */
object CatalogSources {
    data class Index(
        val sources: Map<String, Set<BookSource>> = emptyMap(),
        /**
         * Id of a Kütüphane 1 book → the same book from shamela.ws. The merged catalog lists the
         * Kütüphane 1 entry, but the user may have downloaded the shamela.ws copy (saved under its
         * own section and title) before the catalogs were merged.
         */
        val siteCopyOfLibraryBook: Map<String, Book> = emptyMap(),
    ) {
        private val libraryIdOfSiteCopy: Map<String, String> by lazy {
            siteCopyOfLibraryBook.entries.associate { (libraryId, copy) -> copy.id to libraryId }
        }

        fun libraryIdOf(siteCopyId: String): String? = libraryIdOfSiteCopy[siteCopyId]
    }

    private val _index = MutableStateFlow(Index())
    val index: StateFlow<Index> = _index.asStateFlow()

    fun of(bookId: String): Set<BookSource> = _index.value.sources[bookId].orEmpty()

    /** The shamela.ws copy of a Kütüphane 1 book, if the two catalogs share it. */
    fun siteCopyOf(bookId: String): Book? = _index.value.siteCopyOfLibraryBook[bookId]

    internal fun publish(index: Index) {
        _index.value = index
    }

    /**
     * [downloadedIds] plus the listed id of each downloaded book that the merged catalog lists under
     * the other catalog's id (a shamela.ws copy of a book listed from Kütüphane 1).
     */
    fun withListedIds(downloadedIds: Set<String>): Set<String> {
        val index = _index.value
        if (index.siteCopyOfLibraryBook.isEmpty()) return downloadedIds
        return downloadedIds + downloadedIds.mapNotNull { index.libraryIdOf(it) }
    }
}
