package com.shamela.library.data.local.backup

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.shamela.library.data.local.db.BooksDao
import com.shamela.library.data.local.db.QuotesDao
import com.shamela.library.domain.model.Quote
import java.io.InputStream
import java.io.OutputStream

/**
 * Exports/imports the user's own data (favorite books and saved quotes) as JSON, so it survives a
 * reinstall or a new phone. Book files are not included; they can be downloaded again.
 */
class LibraryBackup(
    private val booksDao: BooksDao,
    private val quotesDao: QuotesDao,
) {
    private val gson = Gson()

    data class ImportResult(val imported: Int, val skipped: Int)

    class InvalidBackupException(cause: Throwable? = null) : Exception(cause)

    private data class BackupFile(
        val format: String? = null,
        val version: Int = 0,
        val favorites: List<BackupBook>? = null,
        val quotes: List<BackupQuote>? = null,
    )

    private data class BackupBook(val id: String, val title: String, val categoryName: String)

    private data class BackupQuote(
        val text: String,
        val pageIndex: Int,
        val pageHref: String,
        val bookName: String,
        val bookId: String,
    )

    suspend fun export(output: OutputStream): Int {
        val favorites = booksDao.getDownloadedBooksOnce()
            .filter { it.isFavorite }
            .map { BackupBook(it.id, it.title, it.categoryName) }
        val quotes = quotesDao.getAllQuotesOnce()
            .map { BackupQuote(it.text, it.pageIndex, it.pageHref, it.bookName, it.bookId) }
        output.bufferedWriter().use { writer ->
            gson.toJson(BackupFile(FORMAT, VERSION, favorites, quotes), writer)
        }
        return favorites.size + quotes.size
    }

    /**
     * Restores favorites and quotes for books that are currently downloaded. Entries for books
     * that aren't downloaded are counted as skipped (quotes need their book row to exist).
     */
    suspend fun import(input: InputStream): ImportResult {
        val backup = try {
            input.bufferedReader().use { gson.fromJson(it, BackupFile::class.java) }
        } catch (e: JsonSyntaxException) {
            throw InvalidBackupException(e)
        }
        if (backup?.format != FORMAT) throw InvalidBackupException()

        val downloadedIds = booksDao.getDownloadedBooksOnce().map { it.id }.toSet()
        var imported = 0
        var skipped = 0
        backup.favorites.orEmpty().forEach { book ->
            if (book.id in downloadedIds) {
                booksDao.updateBook(book.id, 1)
                imported++
            } else skipped++
        }
        backup.quotes.orEmpty().forEach { quote ->
            if (quote.bookId in downloadedIds) {
                quotesDao.insert(
                    Quote(
                        text = quote.text,
                        pageIndex = quote.pageIndex,
                        pageHref = quote.pageHref,
                        bookName = quote.bookName,
                        bookId = quote.bookId,
                    )
                )
                imported++
            } else skipped++
        }
        return ImportResult(imported, skipped)
    }

    companion object {
        private const val FORMAT = "shamela-library-backup"
        private const val VERSION = 1
    }
}
