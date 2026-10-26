package com.shamela.library.presentation.screens.library

import com.shamela.library.domain.model.Book
import com.shamela.library.domain.model.Category
import com.shamela.library.domain.util.BookSortOption


data class LibraryState(
    val booksViewType: BooksViewType = BooksViewType.Sections,
    val isLoading: Boolean = true,
    val books: Map<String, Book> = emptyMap(),
    val sections: Map<String,Category> = emptyMap(),
    /** Ids of the selected books (ids, not Book objects: a favorite toggle mustn't drop a selection). */
    val selectedIds: Set<String> = emptySet(),
    /** [books] in the chosen order, sorted off the main thread by the ViewModel. */
    val sortedBooks: List<Book> = emptyList(),
    val sortOption: BookSortOption = BookSortOption.NAME,
    val sortAscending: Boolean = BookSortOption.NAME.defaultAscending,
    /** Book id -> download time (file last-modified), filled lazily when sorting by download time. */
    val downloadTimes: Map<String, Long> = emptyMap(),
)