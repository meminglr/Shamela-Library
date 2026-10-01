package com.shamela.library.data

import com.shamela.library.data.local.backup.LibraryBackup
import com.shamela.library.domain.model.Book
import com.shamela.library.domain.model.Quote
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class LibraryBackupTest {
    private val downloaded = Book("b1", "كتاب الصلاة", "مؤلف", 10, "الفقه", isFavorite = true)
    private val notDownloaded = Book("b2", "كتاب آخر", "مؤلف", 10, "الفقه", isFavorite = true)

    private suspend fun exportFrom(books: List<Book>, quotes: List<Quote>): ByteArray {
        val booksDao = FakeBooksDao().apply { books.forEach { insertIfAbsent(it) } }
        val quotesDao = FakeQuotesDao().apply { quotes.forEach { insert(it) } }
        return ByteArrayOutputStream().also { LibraryBackup(booksDao, quotesDao).export(it) }.toByteArray()
    }

    @Test
    fun roundTrip_restoresFavoritesAndQuotesForDownloadedBooks() = runTest {
        val quote = Quote("نص", 3, "p3.xhtml", downloaded.title, downloaded.id)
        val orphanQuote = Quote("نص آخر", 1, "p1.xhtml", notDownloaded.title, notDownloaded.id)
        val backup = exportFrom(listOf(downloaded, notDownloaded), listOf(quote, orphanQuote))

        // A fresh install where only b1 has been downloaded again.
        val booksDao = FakeBooksDao().apply { insertIfAbsent(downloaded.copy(isFavorite = false)) }
        val quotesDao = FakeQuotesDao()
        val result = LibraryBackup(booksDao, quotesDao).import(ByteArrayInputStream(backup))

        assertEquals(2, result.imported) // favorite b1 + its quote
        assertEquals(2, result.skipped)  // favorite b2 + its quote
        assertTrue(booksDao.getBookById("b1")!!.isFavorite)
        assertEquals(listOf(quote.quoteId), quotesDao.getAllQuotesOnce().map { it.quoteId })
    }

    @Test(expected = LibraryBackup.InvalidBackupException::class)
    fun unrelatedJson_isRejected() = runTest {
        LibraryBackup(FakeBooksDao(), FakeQuotesDao()).import(ByteArrayInputStream("""{"a":1}""".toByteArray()))
    }

    @Test(expected = LibraryBackup.InvalidBackupException::class)
    fun garbage_isRejected() = runTest {
        LibraryBackup(FakeBooksDao(), FakeQuotesDao()).import(ByteArrayInputStream("not json".toByteArray()))
    }
}
