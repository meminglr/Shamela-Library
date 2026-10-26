package com.shamela.library.data.remote.shamela

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** The shamela.ws catalog as stored on the device. */
data class ShamelaCatalogData(
    val fetchedAt: Long,
    val categories: List<ShamelaCategory>,
    val books: List<ShamelaBookEntry>,
)

/** Where loading the catalog stands, for the Şamile screens. */
sealed class ShamelaCatalogState {
    object Idle : ShamelaCatalogState()
    data class Loading(val done: Int, val total: Int) : ShamelaCatalogState()
    object Ready : ShamelaCatalogState()
    /** [needsVerification]: shamela.ws asks for a browser check (see ShamelaVerifyActivity). */
    data class Failed(val needsVerification: Boolean) : ShamelaCatalogState()
}

/**
 * The list of shamela.ws books: fetched once from the site (home page + one page per category,
 * which lists all of its books) and kept in `filesDir/shamela/catalog.json` until refreshed.
 */
class ShamelaCatalog(context: Context) {
    private val file = File(context.filesDir, "shamela/catalog.json")
    private val gson = Gson()
    private val mutex = Mutex()

    @Volatile
    private var cached: ShamelaCatalogData? = null

    private val _state = MutableStateFlow<ShamelaCatalogState>(ShamelaCatalogState.Idle)
    val state: StateFlow<ShamelaCatalogState> = _state.asStateFlow()

    init {
        ShamelaHttp.init(context)
    }

    /** The catalog, loading it from disk or (first time, or [refresh]) from shamela.ws. */
    suspend fun get(refresh: Boolean = false): ShamelaCatalogData? = mutex.withLock {
        if (!refresh) {
            cached?.let { return it }
            readFromDisk()?.let {
                cached = it
                _state.value = ShamelaCatalogState.Ready
                return it
            }
        }
        try {
            val fresh = download()
            writeToDisk(fresh)
            cached = fresh
            _state.value = ShamelaCatalogState.Ready
            fresh
        } catch (e: ShamelaChallengeException) {
            _state.value = ShamelaCatalogState.Failed(needsVerification = true)
            cached
        } catch (e: Exception) {
            Log.e(TAG, "catalog download failed: ${e.message}")
            _state.value = ShamelaCatalogState.Failed(needsVerification = false)
            cached
        }
    }

    /**
     * The catalog if it is already on the device, without going to shamela.ws. Doesn't wait for a
     * download in progress, so a list can be shown from the other catalog meanwhile.
     */
    suspend fun peek(): ShamelaCatalogData? {
        cached?.let { return it }
        return readFromDisk()?.also {
            cached = it
            if (_state.value is ShamelaCatalogState.Idle) _state.value = ShamelaCatalogState.Ready
        }
    }

    private suspend fun download(): ShamelaCatalogData = coroutineScope {
        _state.value = ShamelaCatalogState.Loading(0, 0)
        val categories = ShamelaCatalogParser.parseCategories(ShamelaHttp.get("${ShamelaHttp.BASE}/"))
        if (categories.isEmpty()) error("no categories on the home page")
        val done = AtomicInteger(0)
        _state.value = ShamelaCatalogState.Loading(0, categories.size)
        // Two pages at a time: quick enough, and gentle on the site.
        val slots = Semaphore(2)
        val books = categories.map { category ->
            async(Dispatchers.IO) {
                slots.withPermit {
                    val html = ShamelaHttp.get("${ShamelaHttp.BASE}/category/${category.id}")
                    ShamelaCatalogParser.parseCategoryBooks(html, category.name).also {
                        _state.value = ShamelaCatalogState.Loading(done.incrementAndGet(), categories.size)
                    }
                }
            }
        }.awaitAll().flatten()
        ShamelaCatalogData(System.currentTimeMillis(), categories, disambiguate(books))
    }

    private suspend fun readFromDisk(): ShamelaCatalogData? = withContext(Dispatchers.IO) {
        try {
            if (!file.isFile) null
            else file.reader().use { gson.fromJson(it, ShamelaCatalogData::class.java) }
                ?.takeIf { it.books.isNotEmpty() }
        } catch (e: Exception) {
            Log.e(TAG, "unreadable catalog file: ${e.message}")
            null
        }
    }

    private suspend fun writeToDisk(data: ShamelaCatalogData) = withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writer().use { gson.toJson(data, it) }
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
    }

    companion object {
        private const val TAG = "ShamelaCatalog"

        /**
         * A downloaded book is a file named after its title in its category folder, so two books
         * with the same title in one category would share a file: those get their number appended.
         * Characters that can't be in a file name are replaced too.
         */
        internal fun disambiguate(books: List<ShamelaBookEntry>): List<ShamelaBookEntry> {
            val counts = books.groupingBy { it.category to safeTitle(it.title) }.eachCount()
            return books.map { book ->
                val title = safeTitle(book.title)
                if ((counts[book.category to title] ?: 0) > 1) book.copy(title = "$title (${book.id})")
                else book.copy(title = title)
            }
        }

        private val unsafe = Regex("""[\\/:*?"<>|]""")

        internal fun safeTitle(title: String): String =
            title.replace(unsafe, "-").replace(Regex("""\s+"""), " ").trim().trimEnd('.')
    }
}
