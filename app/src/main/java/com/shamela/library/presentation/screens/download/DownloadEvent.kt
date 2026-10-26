package com.shamela.library.presentation.screens.download

import com.shamela.library.domain.model.Book
import com.shamela.library.presentation.screens.library.BooksViewType


sealed class DownloadEvent{
    class OnChangeViewType(val newBooksViewType: BooksViewType): DownloadEvent()
    object LoadUserBooks: DownloadEvent()
    object LoadUserSections: DownloadEvent()
    /** Fetch the shamela.ws catalog again (menu, or retry after a failure). */
    object RefreshShamelaCatalog: DownloadEvent()
    class OnClickDownloadBook(val book: Book): DownloadEvent()
    class OnClickCancelDownload(val bookId: String): DownloadEvent()
}
