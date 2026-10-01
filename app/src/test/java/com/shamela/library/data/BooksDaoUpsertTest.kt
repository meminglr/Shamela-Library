package com.shamela.library.data

import com.shamela.library.domain.model.Book
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BooksDaoUpsertTest {
    private val dao = FakeBooksDao()
    private val book = Book("id-1", "كتاب", "مؤلف", 120, "قسم")

    @Test
    fun rescanningALibraryFile_keepsFavoriteFlag() = runTest {
        dao.upsertKeepingUserData(book)
        dao.updateBook(book.id, 1)

        // What LibraryViewModel does on every start: re-save the parsed file (isFavorite = false).
        dao.upsertKeepingUserData(book.copy(isFavorite = false))

        assertTrue(dao.getBookById(book.id)!!.isFavorite)
    }

    @Test
    fun placeholderMetadata_doesNotOverwriteParsedMetadata() = runTest {
        dao.upsertKeepingUserData(book)
        dao.upsertKeepingUserData(book.copy(author = "", pageCount = 0))

        val saved = dao.getBookById(book.id)!!
        assertEquals("مؤلف", saved.author)
        assertEquals(120, saved.pageCount)
    }

    @Test
    fun realMetadata_isRefreshed() = runTest {
        dao.upsertKeepingUserData(book.copy(author = "", pageCount = 0))
        dao.upsertKeepingUserData(book)

        assertEquals(120, dao.getBookById(book.id)!!.pageCount)
    }
}
