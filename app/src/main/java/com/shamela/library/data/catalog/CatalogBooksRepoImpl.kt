package com.shamela.library.data.catalog

import android.net.Uri
import android.util.Log
import com.shamela.library.data.local.assets.AssetsBooksRepoImpl
import com.shamela.library.data.remote.openiti.OpenItiBooksRepoImpl
import com.shamela.library.data.remote.shamela.ShamelaBookEntry
import com.shamela.library.data.remote.shamela.ShamelaBooksRepoImpl
import com.shamela.library.data.remote.shamela.ShamelaCatalogData
import com.shamela.library.domain.model.Book
import com.shamela.library.domain.model.Category
import com.shamela.library.domain.repo.BooksRepository
import com.shamela.library.domain.util.SectionTaxonomy
import com.shamela.library.domain.search.BookSearchMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Qualifier

@Qualifier
annotation class CatalogRepoImpl

/**
 * The one catalog the download screens show: Kütüphane 1 (ready EPUBs), shamela.ws and OpenITI
 * together, in Kütüphane 1's sections, which are also the folders of the user's library.
 *
 * - A book in both is listed once, as its Kütüphane 1 entry, and downloads from there.
 * - A book only on shamela.ws is listed in the section matching its site section
 *   ([SiteSections]) but keeps its own section name, so it downloads, is saved and gets its
 *   details exactly as from the shamela.ws catalog.
 * - Kütüphane 3 (OpenITI) holds only books the other two lack; one that either of them has since
 *   gained is left out here too.
 *
 * Until the shamela.ws catalog is on the device, only Kütüphane 1 is listed; the screens rebuild
 * their lists when it arrives.
 */
class CatalogBooksRepoImpl(
    private val library: AssetsBooksRepoImpl,
    private val site: ShamelaBooksRepoImpl,
    private val openIti: OpenItiBooksRepoImpl,
) : BooksRepository {

    private class Merged(
        val siteData: ShamelaCatalogData?,
        val categories: List<Category>,
        val booksBySection: Map<String, List<Book>>,
        /** "category/title" of every Kütüphane 1 book: those download from Kütüphane 1. */
        val libraryKeys: Set<String>,
        /** "category/title" of every listed Kütüphane 3 book: those are built from OpenITI. */
        val openItiKeys: Set<String>,
    )

    private val mutex = Mutex()
    @Volatile
    private var merged: Merged? = null

    private suspend fun merged(): Merged = mutex.withLock {
        val siteData = site.peekCatalog()
        merged?.takeIf { it.siteData === siteData }?.let { return it }
        withContext(Dispatchers.Default) { build(siteData) }.also { merged = it }
    }

    /** Matches the catalogs now, so the source numbers are ready before a list is opened. */
    suspend fun warmUp() {
        merged()
    }

    private suspend fun build(siteData: ShamelaCatalogData?): Merged {
        val started = System.currentTimeMillis()
        val libraryCategories = library.getCategories().toList()
        val libraryBooks = library.getAllBooks().toList()
        val siteBooks = siteData?.books.orEmpty()

        val matches = CatalogMatcher.match(
            libraryBooks, siteBooks,
            libraryTitle = { it.title }, libraryAuthor = { it.author },
            siteTitle = { it.title }, siteAuthor = { it.author },
        )
        val matchedSite = HashMap<ShamelaBookEntry, Book>()
        matches.forEach { (book, entry) -> matchedSite.putIfAbsent(entry, book) }

        val sources = HashMap<String, Set<BookSource>>(libraryBooks.size + siteBooks.size)
        val siteCopyOfLibraryBook = HashMap<String, Book>()
        val books = libraryBooks.groupByTo(LinkedHashMap()) { SectionTaxonomy.canonicalOf(it.categoryName) }
        for (book in libraryBooks) {
            sources[book.id] = if (book in matches) BOTH else LIBRARY_ONLY
        }
        for (entry in siteBooks) {
            val siteBook = ShamelaBooksRepoImpl.toBook(entry)
            val libraryBook = matchedSite[entry]
            if (libraryBook != null) {
                sources[siteBook.id] = BOTH
                siteCopyOfLibraryBook[libraryBook.id] = siteBook
            } else if (siteBook.id in sources) {
                // Same title in a same-named section as a Kütüphane 1 book (by another author): it
                // would share that book's id and file, so the Kütüphane 1 book is listed alone.
                continue
            } else {
                sources[siteBook.id] = SITE_ONLY
                books.getOrPut(SiteSections.librarySectionOf(entry.category)) { mutableListOf() }.add(siteBook)
            }
        }

        val openItiBooks = openIti.entries().map(OpenItiBooksRepoImpl::toBook)
        // Matched against each catalog on its own: a book in both Kütüphane 1 and 2 would otherwise
        // be two candidates, and so no match, and never show all three numbers.
        val inLibrary = CatalogMatcher.match(
            openItiBooks, libraryBooks,
            libraryTitle = { it.title }, libraryAuthor = { it.author },
            siteTitle = { it.title }, siteAuthor = { it.author },
        )
        val inSite = CatalogMatcher.match(
            openItiBooks, siteBooks.map(ShamelaBooksRepoImpl::toBook),
            libraryTitle = { it.title }, libraryAuthor = { it.author },
            siteTitle = { it.title }, siteAuthor = { it.author },
        )
        val elsewhere = inLibrary.keys + inSite.keys
        // Every copy of a book found in more than one catalog, and the numbers of all of them on each.
        val copies = HashMap<String, MutableMap<BookSource, Book>>()
        fun link(a: Pair<BookSource, Book>, b: Pair<BookSource, Book>) {
            val group = copies[a.second.id] ?: java.util.EnumMap(BookSource::class.java)
            copies[b.second.id]?.takeIf { it !== group }?.let { group.putAll(it) }
            group[a.first] = a.second
            group[b.first] = b.second
            group.values.forEach { copies[it.id] = group }
        }
        matches.forEach { (libraryBook, entry) -> link(BookSource.Library to libraryBook, BookSource.Site to ShamelaBooksRepoImpl.toBook(entry)) }
        inLibrary.forEach { (openItiBook, libraryBook) -> link(BookSource.OpenIti to openItiBook, BookSource.Library to libraryBook) }
        inSite.forEach { (openItiBook, siteBook) -> link(BookSource.OpenIti to openItiBook, BookSource.Site to siteBook) }
        copies.forEach { (id, group) -> sources[id] = group.keys.toSet() }
        val openItiKeys = HashSet<String>()
        for (book in openItiBooks) {
            if (book in elsewhere || book.id in sources) continue
            sources[book.id] = OPENITI_ONLY
            books.getOrPut(SectionTaxonomy.canonicalOf(book.categoryName)) { mutableListOf() }.add(book)
            openItiKeys += "${book.categoryName}/${book.title}"
        }
        val sourcesByTitle = HashMap<String, MutableSet<BookSource>>()
        fun addTitle(title: String, source: BookSource) {
            for (key in setOf(CatalogMatcher.key(title), CatalogMatcher.shortKey(title))) {
                if (key.isNotEmpty()) sourcesByTitle.getOrPut(key) { java.util.EnumSet.noneOf(BookSource::class.java) }.add(source)
            }
        }
        libraryBooks.forEach { addTitle(it.title, BookSource.Library) }
        siteBooks.forEach { addTitle(it.title, BookSource.Site) }
        openItiBooks.forEach { addTitle(it.title, BookSource.OpenIti) }
        CatalogSources.publish(CatalogSources.Index(sources, siteCopyOfLibraryBook, sourcesByTitle, copies))

        // One section per canonical name: the auto-numbered editions are counted in their section.
        val known = LinkedHashMap<String, Category>()
        libraryCategories.forEach { known.putIfAbsent(SectionTaxonomy.canonicalOf(it.name), it) }
        val categories = known.map { (name, original) -> Category(original.id, name, books[name]?.size ?: 0) } +
            books.keys.filter { it !in known }.mapIndexed { i, name ->
                Category("site-$i", name, books.getValue(name).size)
            }
        Log.d(
            TAG, "merged ${libraryBooks.size} + ${siteBooks.size} + ${openItiBooks.size} books: ${matches.size} shared, " +
                "${siteBooks.size - matchedSite.size} only on the site, ${openItiKeys.size} from OpenITI, " +
                "${copies.values.distinct().count { it.size == 3 }} in all three, " +
                "in ${System.currentTimeMillis() - started} ms"
        )
        return Merged(
            siteData = siteData,
            categories = categories,
            booksBySection = books,
            libraryKeys = libraryBooks.mapTo(HashSet()) { "${it.categoryName}/${it.title}" },
            openItiKeys = openItiKeys,
        )
    }

    override fun getCategories(): Flow<Category> = flow {
        emitAll(merged().categories.asFlow())
    }

    override fun getBooksByCategory(categoryName: String): Flow<Book> = flow {
        emitAll(merged().booksBySection[categoryName].orEmpty().asFlow())
    }

    override fun searchBooksByName(categoryName: String, query: String): Flow<Book> = flow {
        val sections = merged().booksBySection
        val books = if (categoryName == "all") sections.values.flatten() else sections[categoryName].orEmpty()
        val matches = withContext(Dispatchers.Default) { BookSearchMatcher.search(books, query) }
        emitAll(matches.asFlow())
    }

    override fun getAllBooks(): Flow<Book> = flow {
        emitAll(merged().booksBySection.values.flatten().asFlow())
    }

    /** The link of a book's copy in [source] (see [CatalogSources.copiesOf]). */
    suspend fun getDownloadLinkFrom(source: BookSource, book: Book): Uri? = when (source) {
        BookSource.Library -> library.getDownloadLink(book.categoryName, book.title)
        BookSource.Site -> site.getDownloadLink(book.categoryName, book.title)
        BookSource.OpenIti -> openIti.getDownloadLink(book.categoryName, book.title)
    }

    /** From Kütüphane 1 when it has the book, built from OpenITI for a Kütüphane 3 book, else from shamela.ws. */
    override suspend fun getDownloadLink(categoryName: String, bookName: String): Uri? {
        val key = "$categoryName/$bookName"
        val merged = merged()
        return when (key) {
            in merged.libraryKeys -> library.getDownloadLink(categoryName, bookName)
            in merged.openItiKeys -> openIti.getDownloadLink(categoryName, bookName)
            else -> site.getDownloadLink(categoryName, bookName)
        }
    }

    private companion object {
        const val TAG = "CatalogBooksRepo"
        val LIBRARY_ONLY = setOf(BookSource.Library)
        val SITE_ONLY = setOf(BookSource.Site)
        val BOTH = setOf(BookSource.Library, BookSource.Site)
        val OPENITI_ONLY = setOf(BookSource.OpenIti)
    }
}

/**
 * Where the sections of every catalog go among the app's sections; the table itself is
 * [SectionTaxonomy]. shamela.ws's 40 sections and the " - مرقم آليا" editions are folded into
 * Kütüphane 1's sections.
 */
internal object SiteSections {
    /** The section a catalog section is listed under. */
    fun librarySectionOf(sourceSection: String): String = SectionTaxonomy.canonicalOf(sourceSection)

    /**
     * The section a library folder is shown under: a shamela.ws book is saved in a folder named
     * after its site section ("التفسير"), an auto-numbered edition in "<section> - مرقم آليا"; the
     * library shows them as one section. Other folders keep their name.
     */
    fun mergedSectionOf(folder: String): String = SectionTaxonomy.canonicalOf(folder)

    /** The library folders shown as [section]: itself and the names merged into it. */
    fun foldersOf(section: String): List<String> = SectionTaxonomy.sourcesOf(section)
}
