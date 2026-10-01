package com.shamela.library.data

import com.shamela.library.data.local.db.BooksDao
import com.shamela.library.data.local.db.QuotesDao
import com.shamela.library.domain.model.Book
import com.shamela.library.domain.model.Quote
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory DAOs with the same conflict semantics as the Room queries they replace. */
class FakeBooksDao : BooksDao {
    val books = MutableStateFlow<Map<String, Book>>(emptyMap())

    override suspend fun insertIfAbsent(book: Book): Long {
        if (books.value.containsKey(book.id)) return -1L
        books.value = books.value + (book.id to book)
        return 1L
    }

    override suspend fun updateMetadata(bookId: String, title: String, author: String, pageCount: Int, categoryName: String) {
        val book = books.value[bookId] ?: return
        books.value = books.value + (bookId to book.copy(title = title, author = author, pageCount = pageCount, categoryName = categoryName))
    }

    override suspend fun delete(bookId: String) {
        books.value = books.value - bookId
    }

    override suspend fun updateBook(bookId: String, isFavorite: Int) {
        val book = books.value[bookId] ?: return
        books.value = books.value + (bookId to book.copy(isFavorite = isFavorite == 1))
    }

    override fun getDownloadedBooks(): Flow<List<Book>> = books.map { it.values.toList() }
    override suspend fun getDownloadedBooksOnce(): List<Book> = books.value.values.toList()
    override fun getFavoriteBooks(): Flow<List<Book>> = books.map { m -> m.values.filter { it.isFavorite } }
    override suspend fun getBookById(bookId: String): Book? = books.value[bookId]
}

class FakeQuotesDao : QuotesDao {
    val quotes = MutableStateFlow<Map<String, Quote>>(emptyMap())

    override suspend fun insert(quote: Quote): Long {
        if (quotes.value.containsKey(quote.quoteId)) return -1L
        quotes.value = quotes.value + (quote.quoteId to quote)
        return 1L
    }

    override suspend fun delete(qId: String) {
        quotes.value = quotes.value - qId
    }

    override fun getAllQuotes(): Flow<List<Quote>> = quotes.map { it.values.toList() }
    override suspend fun getAllQuotesOnce(): List<Quote> = quotes.value.values.toList()
}
