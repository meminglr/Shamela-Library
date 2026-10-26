package com.shamela.library.data.remote.shamela

import android.net.Uri
import androidx.core.net.toUri
import com.shamela.library.domain.model.Book
import com.shamela.library.domain.model.BookDetails
import com.shamela.library.domain.model.Category
import com.shamela.library.domain.repo.BooksRepository
import com.shamela.library.domain.search.BookSearchMatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.text.Normalizer
import java.util.UUID
import javax.inject.Qualifier

@Qualifier
annotation class ShamelaRepoImpl

/**
 * The shamela.ws source: browse and search its catalog ([ShamelaCatalog]); a book is "downloaded"
 * by building its EPUB on the device ([ShamelaBookBuilder]), which the download worker does for
 * links of the form `shamela://book/<id>`.
 */
class ShamelaBooksRepoImpl(private val catalog: ShamelaCatalog) : BooksRepository {

    val catalogState: StateFlow<ShamelaCatalogState> get() = catalog.state

    suspend fun refreshCatalog() {
        catalog.get(refresh = true)
    }

    /** Loads the catalog if needed; the screens show [catalogState] meanwhile. */
    suspend fun ensureCatalog() {
        catalog.get()
    }

    /** The catalog if it is on the device already (see [ShamelaCatalog.peek]). */
    suspend fun peekCatalog(): ShamelaCatalogData? = catalog.peek()

    override fun getCategories(): Flow<Category> = flow {
        val data = catalog.get() ?: return@flow
        val counts = data.books.groupingBy { it.category }.eachCount()
        data.categories.forEach { category ->
            emit(Category(category.id.toString(), category.name, counts[category.name] ?: 0))
        }
    }

    override fun getBooksByCategory(categoryName: String): Flow<Book> = flow {
        val data = catalog.get() ?: return@flow
        emitAll(data.books.filter { it.category == categoryName }.map(::toBook).asFlow())
    }

    override fun searchBooksByName(categoryName: String, query: String): Flow<Book> = flow {
        val data = catalog.get() ?: return@flow
        val matches = data.books.asSequence()
            .filter { categoryName == "all" || it.category == categoryName }
            .map(::toBook)
            .filter { BookSearchMatcher.matches(it, query) }
            .sortedByDescending { BookSearchMatcher.relevanceScore(it, query) }
            .toList()
        emitAll(matches.asFlow())
    }

    override fun getAllBooks(): Flow<Book> = flow {
        val data = catalog.get() ?: return@flow
        emitAll(data.books.map(::toBook).asFlow())
    }

    override suspend fun getDownloadLink(categoryName: String, bookName: String): Uri? =
        find(categoryName, bookName)?.let { "$SCHEME://book/${it.id}".toUri() }

    suspend fun getBookDetails(categoryName: String, bookTitle: String): BookDetails? =
        find(categoryName, bookTitle)?.let { entry ->
            BookDetails(authorDeathYear = entry.authorDeathYear, about = entry.about)
        }

    suspend fun find(categoryName: String, title: String): ShamelaBookEntry? {
        val data = catalog.get() ?: return null
        val target = Normalizer.normalize(title, Normalizer.Form.NFC)
        return data.books.firstOrNull {
            it.category == categoryName && Normalizer.normalize(it.title, Normalizer.Form.NFC) == target
        }
    }

    suspend fun findById(id: Int): ShamelaBookEntry? = catalog.get()?.books?.firstOrNull { it.id == id }

    companion object {
        const val SCHEME = "shamela"

        /** The shamela.ws book number in a `shamela://book/<id>` link, or null for other links. */
        fun bookIdOf(url: String): Int? =
            url.takeIf { it.startsWith("$SCHEME://book/") }?.substringAfterLast('/')?.toIntOrNull()

        /** Same id as [com.shamela.library.data.local.files.FilesBooksRepoImpl.parseBook] gives the file. */
        fun toBook(entry: ShamelaBookEntry) = Book(
            id = UUID.nameUUIDFromBytes((entry.title + entry.category).toByteArray()).toString(),
            title = entry.title,
            author = entry.author,
            pageCount = entry.pageCount,
            categoryName = entry.category,
        )
    }
}
