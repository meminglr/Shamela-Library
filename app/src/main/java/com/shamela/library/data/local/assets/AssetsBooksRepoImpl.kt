package com.shamela.library.data.local.assets

import java.net.URL
import java.net.HttpURLConnection
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import com.shamela.library.BuildConfig
import com.shamela.library.data.local.assets.dto.AssetsBook
import com.shamela.library.data.local.assets.dto.AssetsBookDetail
import com.shamela.library.domain.model.Book
import com.shamela.library.domain.model.BookDetails
import com.shamela.library.domain.model.BookInfoItem
import com.shamela.library.domain.model.Category
import com.shamela.library.domain.repo.BooksRepository
import com.shamela.library.domain.search.BookSearchMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStreamReader
import java.text.Normalizer
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Qualifier

@Qualifier
annotation class AssetsRepoImpl
class AssetsBooksRepoImpl(private val context: Context) : BooksRepository {
    private val TAG = "AssetsBooksRepoImpl"
    private val gson = Gson()
    /** Parsed category files. The catalog is read-only, so each JSON is parsed once per process. */
    private val booksByCategory = ConcurrentHashMap<String, List<Book>>()

    suspend fun _getCategories(): List<Category> {
        return withContext(Dispatchers.IO) {
            try {
                val categoryNames = context.assets.list("categories")
                categoryNames?.mapIndexed { index, categoryName ->
                    val bookCount = _getBooksByCategory(categoryName).size
                    Category(index.toString(), categoryName.removeSuffix(".json"), bookCount)
                } ?: emptyList()
            } catch (e: IOException) {
                Log.e(TAG, "Error: getCategories(). ${e.message}")
                emptyList()
            }
        }
    }

    private suspend fun _getBooksByCategory(categoryName: String): List<Book> {
        val categoryNameNoSuffix = categoryName.removeSuffix(".json")
        booksByCategory[categoryNameNoSuffix]?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val fileName = "categories/$categoryNameNoSuffix.json"
                val books = context.assets.open(fileName).use { inputStream ->
                    gson.fromJson(InputStreamReader(inputStream), Array<AssetsBook>::class.java)
                }
                books.map { book ->
                    val uuidName = book.title + categoryNameNoSuffix
                    val bookID = UUID.nameUUIDFromBytes(uuidName.toByteArray()).toString()
                    Book(bookID, book.title, book.author, book.pageCount, categoryNameNoSuffix)
                }.also { booksByCategory[categoryNameNoSuffix] = it }
            } catch (e: IOException) {
                Log.e(TAG, "Error: getBooksByCategory($categoryName). ${e.message}")
                emptyList()
            }
        }
    }

    /**
     * Loads the "about the book" metadata for a single book from assets/book-details/<category>.json.
     * Resolved by (categoryName, title) so it works for browsing, search, favorites, and downloaded
     * books alike. Returns null when the category has no detail file (e.g. محاضرات مفرغة) or the book
     * isn't found.
     */
    suspend fun getBookDetails(categoryName: String, bookTitle: String): BookDetails? {
        return withContext(Dispatchers.IO) {
            try {
                val categoryNameNoSuffix = categoryName.removeSuffix(".json")
                val fileName = "book-details/$categoryNameNoSuffix.json"
                // Normalize both sides: a downloaded book's title comes from the device filename,
                // which may differ in Unicode normalization (NFC/NFD) from the JSON title.
                val target = Normalizer.normalize(bookTitle, Normalizer.Form.NFC)
                // Streamed one entry at a time, stopping at the match: detail files are up to
                // 2.5 MB, and parsing a whole one into memory on every screen open was wasteful.
                context.assets.open(fileName).use { inputStream ->
                    JsonReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                        var found: AssetsBookDetail? = null
                        reader.beginArray()
                        while (found == null && reader.hasNext()) {
                            // Gson can leave a non-null field null (an entry without a title).
                            val entry: AssetsBookDetail? = gson.fromJson(reader, AssetsBookDetail::class.java)
                            val title: String? = entry?.title
                            if (title != null && Normalizer.normalize(title, Normalizer.Form.NFC) == target) found = entry
                        }
                        found
                    }
                }?.let { detail ->
                    BookDetails(
                        authorDeathYear = detail.authorDeathYear,
                        about = detail.about.orEmpty().map { BookInfoItem(it.label, it.value) },
                        description = detail.description,
                        descriptionSource = detail.descriptionSource,
                        descriptionUrl = detail.descriptionUrl,
                        descriptionModel = detail.descriptionModel,
                        topics = detail.topics.orEmpty(),
                    )
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error: getBookDetails($categoryName, $bookTitle). ${e.message}")
                null
            }
        }
    }

    override fun getCategories(): Flow<Category> = flow {
        emitAll(_getCategories().asFlow())
    }

    override fun getBooksByCategory(categoryName: String): Flow<Book> = flow {
        emitAll(_getBooksByCategory(categoryName).asFlow())
    }

    override fun searchBooksByName(categoryName: String, query: String): Flow<Book> {
        return if (categoryName == "all") searchAllBooks(query)
        else flow {
            val matches = _getBooksByCategory(categoryName)
                .filter { BookSearchMatcher.matches(it, query) }
                .sortedByDescending { BookSearchMatcher.relevanceScore(it, query) }
            emitAll(matches.asFlow())
        }
    }

    private fun searchAllBooks(query: String) = channelFlow<Book> {
        val categoryNames = context.assets.list("categories") ?: emptyArray()
        val results = Collections.synchronizedList(mutableListOf<Book>())

        categoryNames.map { category ->
            launch(Dispatchers.IO) {
                _getBooksByCategory(category)
                    .filter { BookSearchMatcher.matches(it, query) }
                    .forEach { results.add(it) }
            }
        }.joinAll()

        results.sortedByDescending { BookSearchMatcher.relevanceScore(it, query) }
               .forEach { send(it) }
    }


    override fun getAllBooks(): Flow<Book> = flow {
        try {
            val categoryNames = context.assets.list("categories") ?: emptyArray()
            categoryNames.forEach { category ->
                val books = _getBooksByCategory(category).asFlow()
                emitAll(books)
            }

        } catch (e: IOException) {
            Log.e(TAG, "Error: getAllBooks. ${e.message}")
        }
    }

    override suspend fun getDownloadLink(
        categoryName: String,
        bookName: String
    ): Uri? {

        val baseUrl = BuildConfig.BASE_URL.trimEnd('/')
        if (baseUrl.isBlank() || baseUrl == "null") {
            Log.e(TAG, "getDownloadLink: BASE_URL is not configured in local.properties")
            return null
        }
        return try {
            val encodedPath = Uri.encode("$categoryName/$bookName.epub", "/")
            DownloadUrlStyle.resolve(baseUrl, encodedPath).toUri()
        } catch (e: Exception) {
            Log.e(
                TAG,
                "ERROR: getDownloadLink: (categoryName, bookName) = ($categoryName, $bookName)"
            )
            e.printStackTrace()
            null
        }
    }

}

/**
 * The original app built links as `BASE_URL + "/" + path` with a BASE_URL that already ended in
 * "/", i.e. `host//category/book.epub`, and that is the form known to work against the book server.
 * The single-slash form is tried too, in case the server is ever fixed. The first form that answers
 * is remembered (also across launches), so a whole section is not probed book by book and the first
 * download after a restart doesn't wait for a probe.
 */
internal object DownloadUrlStyle {
    @Volatile
    private var doubleSlash: Boolean? = null

    @Volatile
    private var prefs: android.content.SharedPreferences? = null

    /** Loads the remembered form (reads disk: call off the main thread). */
    fun init(context: Context) {
        if (prefs != null) return
        val stored = context.getSharedPreferences("book_server", Context.MODE_PRIVATE)
        if (doubleSlash == null && stored.contains(KEY)) doubleSlash = stored.getBoolean(KEY, true)
        prefs = stored
    }

    val isKnown: Boolean get() = doubleSlash != null

    private const val KEY = "double_slash"

    suspend fun resolve(baseUrl: String, encodedPath: String): String {
        fun url(double: Boolean) = if (double) "$baseUrl//$encodedPath" else "$baseUrl/$encodedPath"
        doubleSlash?.let { return url(it) }
        val chosen = withContext(Dispatchers.IO) {
            when {
                exists(url(true)) -> true
                exists(url(false)) -> false
                else -> null
            }
        }
        if (chosen != null) {
            doubleSlash = chosen
            prefs?.edit()?.putBoolean(KEY, chosen)?.apply()
        }
        // Unreachable right now (offline): use the original app's form.
        return url(chosen ?: true)
    }

    private fun exists(url: String): Boolean = try {
        com.shamela.library.data.remote.BookServerConnection.open(url) {
            requestMethod = "HEAD"
            connectTimeout = 8000
            readTimeout = 8000
            instanceFollowRedirects = true
        }.run {
            val ok = responseCode in 200..299
            disconnect()
            ok
        }
    } catch (e: Exception) {
        Log.w("DownloadUrlStyle", "probe failed for $url: ${e.message}")
        false
    }
}

/**
 * Gets the book server ready at app start, in the background: loads the remembered connection route
 * and link form, and if either is still unknown (first launch, or a new install) finds them with a
 * probe of one catalog book. The first tap on "download" then starts at once instead of waiting for
 * that discovery (a blocked try, then the way around the SNI filter, then the link probe).
 */
object BookServerWarmup {
    private const val TAG = "BookServerWarmup"

    fun start(context: Context) {
        val app = context.applicationContext
        Thread({
            try {
                com.shamela.library.data.remote.BookServerConnection.init(app)
                DownloadUrlStyle.init(app)
                if (DownloadUrlStyle.isKnown && com.shamela.library.data.remote.BookServerConnection.isRouteKnown) {
                    return@Thread
                }
                val baseUrl = BuildConfig.BASE_URL.trimEnd('/')
                if (baseUrl.isBlank() || baseUrl == "null") return@Thread
                val sample = sampleBookPath(app) ?: return@Thread
                kotlinx.coroutines.runBlocking { DownloadUrlStyle.resolve(baseUrl, sample) }
            } catch (e: Exception) {
                Log.w(TAG, "warm-up failed: ${e.message}")
            }
        }, TAG).apply { isDaemon = true }.start()
    }

    /** The encoded "category/title.epub" of the first book of the first catalog section. */
    private fun sampleBookPath(context: Context): String? {
        val category = context.assets.list("categories")?.sorted()?.firstOrNull() ?: return null
        val books = context.assets.open("categories/$category").use { input ->
            Gson().fromJson(InputStreamReader(input), Array<AssetsBook>::class.java)
        }
        val title = books.firstOrNull()?.title ?: return null
        return Uri.encode("${category.removeSuffix(".json")}/$title.epub", "/")
    }
}
