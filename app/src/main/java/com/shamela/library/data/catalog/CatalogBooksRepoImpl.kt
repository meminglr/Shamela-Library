package com.shamela.library.data.catalog

import android.net.Uri
import android.util.Log
import com.shamela.library.data.local.assets.AssetsBooksRepoImpl
import com.shamela.library.data.remote.shamela.ShamelaBookEntry
import com.shamela.library.data.remote.shamela.ShamelaBooksRepoImpl
import com.shamela.library.data.remote.shamela.ShamelaCatalogData
import com.shamela.library.domain.model.Book
import com.shamela.library.domain.model.Category
import com.shamela.library.domain.repo.BooksRepository
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
 * The one catalog the download screens show: Kütüphane 1 (ready EPUBs) and shamela.ws together,
 * in Kütüphane 1's sections, which are also the folders of the user's library.
 *
 * - A book in both is listed once, as its Kütüphane 1 entry, and downloads from there.
 * - A book only on shamela.ws is listed in the section matching its site section
 *   ([SiteSections]) but keeps its own section name, so it downloads, is saved and gets its
 *   details exactly as from the shamela.ws catalog.
 *
 * Until the shamela.ws catalog is on the device, only Kütüphane 1 is listed; the screens rebuild
 * their lists when it arrives.
 */
class CatalogBooksRepoImpl(
    private val library: AssetsBooksRepoImpl,
    private val site: ShamelaBooksRepoImpl,
) : BooksRepository {

    private class Merged(
        val siteData: ShamelaCatalogData?,
        val categories: List<Category>,
        val booksBySection: Map<String, List<Book>>,
        /** "category/title" of every Kütüphane 1 book: those download from Kütüphane 1. */
        val libraryKeys: Set<String>,
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
        val books = libraryBooks.groupByTo(LinkedHashMap()) { it.categoryName }
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
        CatalogSources.publish(CatalogSources.Index(sources, siteCopyOfLibraryBook))

        val known = libraryCategories.mapTo(HashSet()) { it.name }
        val categories = libraryCategories.map { it.copy(bookCount = books[it.name]?.size ?: 0) } +
            books.keys.filter { it !in known }.mapIndexed { i, name ->
                Category("site-$i", name, books.getValue(name).size)
            }
        Log.d(
            TAG, "merged ${libraryBooks.size} + ${siteBooks.size} books: ${matches.size} shared, " +
                "${siteBooks.size - matchedSite.size} only on the site, in ${System.currentTimeMillis() - started} ms"
        )
        return Merged(
            siteData = siteData,
            categories = categories,
            booksBySection = books,
            libraryKeys = libraryBooks.mapTo(HashSet()) { "${it.categoryName}/${it.title}" },
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
        val matches = withContext(Dispatchers.Default) {
            books.map { it to BookSearchMatcher.relevanceScore(it, query) }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }
                .map { it.first }
        }
        emitAll(matches.asFlow())
    }

    override fun getAllBooks(): Flow<Book> = flow {
        emitAll(merged().booksBySection.values.flatten().asFlow())
    }

    /** From Kütüphane 1 when it has the book, otherwise built from shamela.ws. */
    override suspend fun getDownloadLink(categoryName: String, bookName: String): Uri? =
        if ("$categoryName/$bookName" in merged().libraryKeys) library.getDownloadLink(categoryName, bookName)
        else site.getDownloadLink(categoryName, bookName)

    private companion object {
        const val TAG = "CatalogBooksRepo"
        val LIBRARY_ONLY = setOf(BookSource.Library)
        val SITE_ONLY = setOf(BookSource.Site)
        val BOTH = setOf(BookSource.Library, BookSource.Site)
    }
}

/** Where shamela.ws's 40 sections go among Kütüphane 1's sections. */
internal object SiteSections {
    private const val FALLBACK = "علوم أخرى"

    private val toLibrary = mapOf(
        "العقيدة" to "العقيدة",
        "الفرق والردود" to "العقيدة",
        "التفسير" to "التفاسير",
        "علوم القرآن وأصول التفسير" to "علوم القرآن",
        "التجويد والقراءات" to "علوم القرآن",
        "كتب السنة" to "متون الحديث",
        "شروح الحديث" to "شروح الحديث",
        "التخريج والأطراف" to "كتب التخريج والزوائد",
        "العلل والسؤلات الحديثية" to "العلل والسؤالات",
        "علوم الحديث" to "علوم الحديث",
        "أصول الفقه" to "أصول الفقه والقواعد الفقهية",
        "علوم الفقه والقواعد الفقهية" to "أصول الفقه والقواعد الفقهية",
        "المنطق" to "علوم أخرى",
        "الفقه الحنفي" to "فقه حنفي",
        "الفقه المالكي" to "فقه مالكي",
        "الفقه الشافعي" to "فقه شافعي",
        "الفقه الحنبلي" to "فقه حنبلي",
        "الفقه العام" to "فقه عام",
        "مسائل فقهية" to "فقه عام",
        "الفرائض والوصايا" to "فقه عام",
        "السياسة الشرعية والقضاء" to "السياسة الشرعية والقضاء",
        "الفتاوى" to "الفتاوى",
        "الرقائق والآداب والأذكار" to "الرقاق والآداب والأذكار",
        "السيرة النبوية" to "السيرة والشمائل",
        "التاريخ" to "التاريخ",
        "التراجم والطبقات" to "التراجم والطبقات",
        "الأنساب" to "الأنساب",
        "البلدان والرحلات" to "البلدان والجغرافيا والرحلات",
        "كتب اللغة" to "كتب اللغة",
        "الغريب والمعاجم" to "الغريب والمعاجم ولغة الفقه",
        "النحو والصرف" to "النحو والصرف",
        "الأدب" to "الأدب والبلاغة",
        "العروض والقوافي" to "الأدب والبلاغة",
        "الشعر ودواوينه" to "الأدب والبلاغة",
        "البلاغة" to "الأدب والبلاغة",
        "الجوامع" to "الجوامع والمجلات ونحوها",
        "فهارس الكتب والأدلة" to "فهارس الكتب والأدلة",
        "الطب" to "علوم أخرى",
        "كتب عامة" to "كتب إسلامية عامة",
        "علوم أخرى" to "علوم أخرى",
    )

    /** A section the site adds later goes to "other sciences" until it is mapped here. */
    fun librarySectionOf(siteSection: String): String = toLibrary[siteSection] ?: FALLBACK
}
