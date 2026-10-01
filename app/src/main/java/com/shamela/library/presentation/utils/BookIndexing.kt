package com.shamela.library.presentation.utils

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.shamela.apptheme.data.db.DatabaseHelper
import com.shamela.apptheme.presentation.worker.BookPreparationWorker
import com.shamela.library.domain.model.Book
import java.io.File

/**
 * Full-text-search indexing of downloaded books. One unique WorkManager job per book, so a book is
 * never indexed twice concurrently and its job can be cancelled when the book is deleted.
 */
object BookIndexing {
    private const val TAG = "BookIndexing"

    private fun workName(bookId: String) = "prepare-book-$bookId"

    fun enqueue(context: Context, book: Book, replace: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<BookPreparationWorker>()
            .setInputData(workDataOf(BookPreparationWorker.EPUB_FILE_PATH to BooksDownloadManager.getBookPath(book)))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(book.id),
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun cancel(context: Context, bookId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(bookId))
    }

    /**
     * Indexes every downloaded book whose file exists but has no pages in the search index — e.g. a
     * download that finished while the app was killed, or an index lost to an older app version.
     */
    suspend fun indexMissing(context: Context, books: List<Book>) {
        val database = DatabaseHelper.getInstance(context)
        books.forEach { book ->
            if (File(BooksDownloadManager.getBookPath(book)).isFile && !database.hasPages(book.id)) {
                Log.d(TAG, "indexMissing: indexing ${book.title}")
                enqueue(context, book)
            }
        }
    }
}
