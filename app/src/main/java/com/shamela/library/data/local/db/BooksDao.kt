package com.shamela.library.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.shamela.library.domain.model.Book
import kotlinx.coroutines.flow.Flow

@Dao
interface BooksDao {

    /**
     * Never use REPLACE for books: REPLACE deletes the existing row first, which resets
     * [Book.isFavorite] and cascades into the Quote table, wiping the user's quotes.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(book: Book): Long

    @Query(
        "UPDATE downloadedBooks SET title = :title, author = :author, pageCount = :pageCount, " +
            "categoryName = :categoryName WHERE id = :bookId"
    )
    suspend fun updateMetadata(
        bookId: String,
        title: String,
        author: String,
        pageCount: Int,
        categoryName: String,
    )

    /** Inserts the book, or refreshes its metadata while keeping favorite state and quotes. */
    @Transaction
    suspend fun upsertKeepingUserData(book: Book) {
        if (insertIfAbsent(book) == -1L) {
            val existing = getBookById(book.id) ?: return
            // Don't overwrite real metadata with placeholders from a not-yet-parsed file.
            val hasRealMetadata = book.pageCount > 0
            updateMetadata(
                bookId = book.id,
                title = book.title,
                author = if (hasRealMetadata) book.author else existing.author,
                pageCount = if (hasRealMetadata) book.pageCount else existing.pageCount,
                categoryName = book.categoryName,
            )
        }
    }

    @Query("DELETE FROM downloadedBooks WHERE id = :bookId")
    suspend fun delete(bookId: String)

    @Query("UPDATE downloadedBooks SET isFavorite = :isFavorite WHERE id = :bookId")
    suspend fun updateBook(bookId: String, isFavorite: Int)

    @Query("SELECT * FROM downloadedBooks")
    fun getDownloadedBooks(): Flow<List<Book>>

    @Query("SELECT * FROM downloadedBooks")
    suspend fun getDownloadedBooksOnce(): List<Book>

    @Query("SELECT * FROM downloadedBooks WHERE isFavorite = 1  ")
    fun getFavoriteBooks(): Flow<List<Book>>

    @Query("SELECT * FROM downloadedBooks WHERE id = :bookId")
    suspend fun getBookById(bookId: String): Book?
}
