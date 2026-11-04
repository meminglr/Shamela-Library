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
    /** Kütüphane 3: OpenITI texts the other two don't have, built into an EPUB on the device. */
    OpenIti(3),
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
        /**
         * Catalog title key ([CatalogMatcher.key], also of the title before a subtitle) → the
         * catalogs that have a book of that title: for scanned books, which have no catalog id.
         */
        val sourcesByTitle: Map<String, Set<BookSource>> = emptyMap(),
        /** Id of a book in more than one catalog → its copy in each of them. */
        val copies: Map<String, Map<BookSource, Book>> = emptyMap(),
    ) {
        /** The catalogs that have a book titled [title] (as a whole, or before its subtitle). */
        fun sourcesOfTitle(title: String): Set<BookSource> =
            sourcesByTitle[CatalogMatcher.key(title)] ?: sourcesByTitle[CatalogMatcher.shortKey(title)].orEmpty()

        private val libraryIdOfSiteCopy: Map<String, String> by lazy {
            siteCopyOfLibraryBook.entries.associate { (libraryId, copy) -> copy.id to libraryId }
        }

        fun libraryIdOf(siteCopyId: String): String? = libraryIdOfSiteCopy[siteCopyId]
    }

    private val _index = MutableStateFlow(Index())
    val index: StateFlow<Index> = _index.asStateFlow()

    fun of(bookId: String): Set<BookSource> = _index.value.sources[bookId].orEmpty()

    /** The book's copy in each catalog that has it (empty when only one has it). */
    fun copiesOf(bookId: String): Map<BookSource, Book> = _index.value.copies[bookId].orEmpty()

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
