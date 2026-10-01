package com.shamela.library.domain.usecases.books

import android.content.Context
import com.shamela.apptheme.data.db.DatabaseHelper
import com.shamela.library.data.local.db.BooksDao
import com.shamela.library.data.local.files.FilesBooksRepoImpl
import com.shamela.library.domain.model.Book
import com.shamela.library.presentation.utils.BookIndexing

class DeleteBook(private val dao: BooksDao, private val context: Context) {
    suspend operator fun invoke(book: Book): Boolean {
        // Stop a still-running indexing job first, or it would re-insert the pages we delete below.
        BookIndexing.cancel(context, book.id)
        deleteBookFromLocalDB(book.id, dao)
        deleteBookPages(book.id)
        return deleteBookFromLocalStorage(book)
    }

    private suspend fun deleteBookFromLocalDB(bookId: String, dao: BooksDao) {
        dao.delete(bookId)
    }

    private suspend fun deleteBookPages(bookId: String) {
        DatabaseHelper.getInstance(context).deleteBookPages(bookId)
    }

    private suspend fun deleteBookFromLocalStorage(book: Book): Boolean {
        return FilesBooksRepoImpl.deleteBookFile(book)
    }
}
