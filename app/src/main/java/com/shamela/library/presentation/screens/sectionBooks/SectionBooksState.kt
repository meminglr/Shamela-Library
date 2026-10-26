package com.shamela.library.presentation.screens.sectionBooks

import com.shamela.library.domain.model.Book
import com.shamela.library.domain.model.DownloadStatus
import com.shamela.library.domain.util.BookSortOption


data class SectionBooksState(
    val books: Map<String, Book> = emptyMap(),
    /** [books] in the chosen order, sorted off the main thread by the ViewModel. */
    val sortedBooks: List<Book> = emptyList(),
    val type: String = "",
    val isLoading: Boolean = true,
    val isDownloadButtonEnabled: Boolean = true,
    val downloadStatuses: Map<String, DownloadStatus> = emptyMap(),
    val downloadedBookIds: Set<String> = emptySet(),
    val sortOption: BookSortOption = BookSortOption.NAME,
    val sortAscending: Boolean = BookSortOption.NAME.defaultAscending,
    /** Book id -> download time (file last-modified), filled lazily for local sections only. */
    val downloadTimes: Map<String, Long> = emptyMap(),
    /** Ids of local books marked favorite (the files don't carry it; the database does). */
    val favoriteIds: Set<String> = emptySet(),
    val selectedIds: Set<String> = emptySet(),
    /** Every book of this local section was deleted: the screen closes. */
    val isSectionEmptied: Boolean = false,
)