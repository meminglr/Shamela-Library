package com.shamela.library.data.remote.openiti

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import com.shamela.library.domain.model.Book
import com.shamela.library.domain.model.BookDetails
import com.shamela.library.domain.model.BookInfoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.UUID

/** One book of Kütüphane 3: an OpenITI text that neither of the other catalogs has. */
data class OpenItiEntry(
    /** OpenITI version URI, e.g. `0909IbnMabrid.Ightirab.ShamAY0034288-ara1`. */
    val id: String,
    val title: String,
    val author: String,
    val death: Int?,
    /** The Kütüphane 1 section it is listed (and saved) under. */
    val section: String,
    /** The raw text on GitHub. */
    val url: String,
    val words: Int,
    /** Editor, publisher, edition, as OpenITI records them ("·"-separated). */
    val edition: String,
)

/**
 * Kütüphane 3: books of the OpenITI corpus (openiti.org), listed from `assets/openiti/catalog.json`
 * (made by tools/openiti_catalog.py, already without the books the other catalogs have). A book is
 * "downloaded" by fetching its text and building its EPUB on the device ([OpenItiBookBuilder]),
 * which the download worker does for links of the form `openiti://book/<version URI>`.
 */
class OpenItiBooksRepoImpl(private val context: Context) {
    private val mutex = Mutex()

    @Volatile
    private var cached: List<OpenItiEntry>? = null

    suspend fun entries(): List<OpenItiEntry> = cached ?: mutex.withLock {
        cached ?: withContext(Dispatchers.IO) {
            try {
                context.assets.open(CATALOG).reader().use { Gson().fromJson(it, Array<OpenItiEntry>::class.java) }.toList()
            } catch (e: Exception) {
                Log.e(TAG, "unreadable catalog: ${e.message}")
                emptyList()
            }
        }.also { cached = it }
    }

    suspend fun find(categoryName: String, title: String): OpenItiEntry? {
        val target = Normalizer.normalize(title, Normalizer.Form.NFC)
        return entries().firstOrNull {
            it.section == categoryName && Normalizer.normalize(it.title, Normalizer.Form.NFC) == target
        }
    }

    suspend fun findById(id: String): OpenItiEntry? = entries().firstOrNull { it.id == id }

    suspend fun getDownloadLink(categoryName: String, bookName: String): Uri? =
        find(categoryName, bookName)?.let { linkOf(it) }

    suspend fun getBookDetails(categoryName: String, bookTitle: String): BookDetails? =
        find(categoryName, bookTitle)?.let { BookDetails(authorDeathYear = it.death, about = aboutOf(it)) }

    companion object {
        private const val TAG = "OpenItiBooksRepo"
        private const val CATALOG = "openiti/catalog.json"
        const val SCHEME = "openiti"

        // Version URIs are letters, digits, dots and dashes: safe in a link as they are.
        fun linkOf(entry: OpenItiEntry): Uri = Uri.parse("$SCHEME://book/${entry.id}")

        /** The version URI in an `openiti://book/<id>` link, or null for other links. */
        fun bookIdOf(url: String): String? =
            url.takeIf { it.startsWith("$SCHEME://book/") }?.substringAfter("$SCHEME://book/")

        /** Same id as [com.shamela.library.data.local.files.FilesBooksRepoImpl.parseBook] gives the file. */
        fun toBook(entry: OpenItiEntry) = Book(
            id = UUID.nameUUIDFromBytes((entry.title + entry.section).toByteArray()).toString(),
            title = entry.title,
            author = entry.author,
            pageCount = 0,
            categoryName = entry.section,
        )

        /** The book card: what the catalog knows (the text's own header adds to it in the EPUB). */
        fun aboutOf(entry: OpenItiEntry): List<BookInfoItem> = listOfNotNull(
            BookInfoItem("الكتاب", entry.title),
            entry.author.takeIf { it.isNotBlank() }?.let {
                BookInfoItem("المؤلف", if (entry.death != null) "$it (ت ${entry.death} هـ)" else it)
            },
            entry.edition.takeIf { it.isNotBlank() }?.let { BookInfoItem("الطبعة", it) },
            entry.words.takeIf { it > 0 }?.let { BookInfoItem("عدد الكلمات", "%,d".format(it)) },
            BookInfoItem("المصدر", "OpenITI · ${entry.id}"),
        )
    }
}
