package com.shamela.library.presentation.utils

import android.app.DownloadManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.shamela.library.ShamelaApp
import com.shamela.library.data.local.files.FilesBooksRepoImpl
import com.shamela.library.domain.model.Book
import com.shamela.library.domain.model.DownloadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/** A failed book download, reported to the UI so the user isn't left guessing. */
data class DownloadError(val bookTitle: String, val reason: Int)

/** One entry of the downloads panel. */
data class DownloadItem(
    val bookId: String,
    val title: String,
    val categoryName: String,
    val status: DownloadStatus,
)

/**
 * Book downloads, run by [BookDownloadWorker] through WorkManager: one unique job per book, so a
 * book is never downloaded twice at once, jobs survive the app being closed, wait for the network
 * and resume where they stopped. Every screen observes [downloadStatusFlow] (per book) or
 * [downloads] (the panel); progress is also shown in a notification ([DownloadNotifier]).
 */
object BooksDownloadManager {

    fun downloadBook(downloadUri: Uri, book: Book, bookCategory: String) {
        enqueue(downloadUri.toString(), book.copy(categoryName = bookCategory))
    }

    fun downloadSection(booksMap: Map<Book, Uri?>) {
        Log.d(TAG, "downloadSection: ${booksMap.size} books")
        booksMap.forEach { (book, uri) -> if (uri != null) enqueue(uri.toString(), book) }
    }

    interface Subscriber {
        fun onBookDownloaded(book: Book, isLastBook: Boolean)
    }

    const val TAG = "BooksDownloadManager"

    /** [DownloadError.reason] when no download link could be built (e.g. BASE_URL missing). */
    const val REASON_NO_LINK = -100
    const val REASON_NOT_FOUND = -101
    const val REASON_NETWORK = -102
    const val REASON_HTTP = -103
    const val REASON_CORRUPT = -104
    /** shamela.ws asks for a browser check (Cloudflare); the user passes it in ShamelaVerifyActivity. */
    const val REASON_SHAMELA_VERIFY = -105
    const val REASON_NO_SPACE = DownloadManager.ERROR_INSUFFICIENT_SPACE
    /** The library folder can't be created in storage. */
    const val REASON_STORAGE = -106

    private const val DOWNLOADS_FOLDER = "ShamelaDownloads"
    private const val UPDATE_INTERVAL_MS = 200L
    private const val WORK_TAG = "book-download"
    private const val TAG_ID = "id:"
    private const val TAG_TITLE = "title:"
    private const val TAG_CATEGORY = "category:"
    private const val TAG_URL = "url:"
    private const val TAG_AUTHOR = "author:"
    private const val TAG_PAGES = "pages:"

    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _statusMap = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    val downloadStatusFlow: StateFlow<Map<String, DownloadStatus>> = _statusMap.asStateFlow()

    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    /** Active and failed downloads, for the downloads panel and the progress bar. */
    val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    private val _downloadErrors = MutableSharedFlow<DownloadError>(extraBufferCapacity = 16)
    val downloadErrors: SharedFlow<DownloadError> = _downloadErrors.asSharedFlow()

    private val subscribers = CopyOnWriteArrayList<Subscriber>()

    /** Books seen downloading in this process: only their failures are announced. */
    private val seenActive = mutableSetOf<String>()
    private val announcedFailures = mutableSetOf<String>()
    /** Failed entries the user removed from the panel. */
    private val dismissed = MutableStateFlow<Set<String>>(emptySet())

    /**
     * Books whose download was tapped but isn't queued yet (its link is being looked up): shown as
     * queued at once, so the button reacts on tap even when the lookup takes a moment.
     */
    private val preparing = MutableStateFlow<Set<String>>(emptySet())

    /** Last known size per book, so an interrupted download still shows how far it got. */
    private val knownTotals = mutableMapOf<String, Long>()

    /** Which URL form the server answers to (see [urlCandidates]); null until known. */
    @Volatile
    private var doubleSlashWorks: Boolean? = null

    fun initializeWith(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        DownloadNotifier.createChannel(appContext)
        scope.launch {
            // Every setProgress of every running job re-emits all jobs' infos (hundreds during a
            // section download), and each pass stats files, sorts and updates the notification
            // and every screen. Handle the latest infos at most every UPDATE_INTERVAL_MS;
            // conflate drops only the intermediate ones, so the final state is never missed.
            WorkManager.getInstance(appContext).getWorkInfosByTagFlow(WORK_TAG)
                .conflate()
                .collect { infos ->
                    onWorkInfos(infos)
                    delay(UPDATE_INTERVAL_MS)
                }
        }
        scope.launch {
            dismissed.collect { refresh() }
        }
        // Connectivity doesn't change any job, so re-evaluate "waiting for network" on changes.
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: android.net.Network, caps: NetworkCapabilities) {
                scope.launch { refresh() }
            }
            override fun onLost(network: android.net.Network) {
                scope.launch { refresh() }
            }
        })
    }

    private var lastInfos: List<WorkInfo> = emptyList()

    private fun refresh() = onWorkInfos(lastInfos)

    @Synchronized
    private fun onWorkInfos(infos: List<WorkInfo>) {
        lastInfos = infos
        val online = isOnline()
        val dismissedIds = dismissed.value
        // Several jobs per book can exist (a retry replaces a failed one): prefer the live one.
        val latest = infos
            .filter { it.state != WorkInfo.State.CANCELLED }
            .groupBy { it.tagValue(TAG_ID) }
            .mapNotNull { (bookId, list) ->
                bookId ?: return@mapNotNull null
                list.minByOrNull { info ->
                    when (info.state) {
                        WorkInfo.State.RUNNING -> 0
                        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> 1
                        WorkInfo.State.FAILED -> 2
                        else -> 3
                    }
                }
            }

        val items = mutableListOf<DownloadItem>()
        for (info in latest) {
            val bookId = info.tagValue(TAG_ID) ?: continue
            val status = statusOf(bookId, info, online) ?: continue
            if (status is DownloadStatus.Failed) {
                if (bookId in seenActive && announcedFailures.add(bookId)) {
                    _downloadErrors.tryEmit(DownloadError(info.tagValue(TAG_TITLE).orEmpty(), status.reason))
                }
                if (bookId in dismissedIds) continue
            } else {
                seenActive += bookId
                announcedFailures -= bookId
            }
            items += DownloadItem(
                bookId = bookId,
                title = info.tagValue(TAG_TITLE).orEmpty(),
                categoryName = info.tagValue(TAG_CATEGORY).orEmpty(),
                status = status,
            )
        }
        // Running first, then queued/waiting, then failed; stable by title within a group.
        val sorted = items.sortedWith(
            compareBy<DownloadItem>({
                when (it.status) {
                    is DownloadStatus.Downloading -> 0
                    is DownloadStatus.Waiting -> 1
                    DownloadStatus.Queued -> 2
                    else -> 3
                }
            }, { it.title })
        )
        _downloads.value = sorted
        val statuses = sorted.associate { it.bookId to it.status }
        // A job showed up for a book being prepared: its own status takes over.
        val stillPreparing = preparing.value - latest.mapNotNull { it.tagValue(TAG_ID) }.toSet()
        if (stillPreparing != preparing.value) preparing.value = stillPreparing
        _statusMap.value = statuses + (stillPreparing - statuses.keys).associateWith { DownloadStatus.Queued }
        DownloadNotifier.update(appContext, sorted)
    }

    private fun statusOf(bookId: String, info: WorkInfo, online: Boolean): DownloadStatus? {
        val partialBytes = partialFile(bookId).let { if (it.isFile) it.length() else 0L }
        return when (info.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> when {
                // Jobs wait for the network constraint: say so instead of "queued".
                !online -> DownloadStatus.Waiting(partialBytes, knownTotals[bookId] ?: -1L, noNetwork = true)
                info.runAttemptCount == 0 && partialBytes == 0L -> DownloadStatus.Queued
                else -> DownloadStatus.Waiting(partialBytes, knownTotals[bookId] ?: -1L, noNetwork = false)
            }

            WorkInfo.State.RUNNING -> {
                val progress = info.progress
                when (progress.getString(BookDownloadWorker.KEY_STATE)) {
                    BookDownloadWorker.STATE_DOWNLOADING -> {
                        val bytes = progress.getLong(BookDownloadWorker.KEY_BYTES, 0L)
                        val total = progress.getLong(BookDownloadWorker.KEY_TOTAL, -1L)
                        val pages = progress.getString(BookDownloadWorker.KEY_UNIT) == BookDownloadWorker.UNIT_PAGES
                        // Byte totals only: they size the "waiting" rows, which count bytes.
                        if (total > 0 && !pages) knownTotals[bookId] = total
                        DownloadStatus.Downloading(
                            progress = if (total > 0) ((bytes * 100) / total).toInt().coerceIn(0, 100) else 0,
                            bytes = bytes,
                            total = total,
                            bytesPerSecond = progress.getLong(BookDownloadWorker.KEY_SPEED, 0L),
                            unitIsPages = pages,
                        )
                    }
                    BookDownloadWorker.STATE_CONNECTING -> {
                        if (progress.getString(BookDownloadWorker.KEY_UNIT) == BookDownloadWorker.UNIT_PAGES) {
                            return DownloadStatus.Downloading(progress = 0, unitIsPages = true)
                        }
                        val total = knownTotals[bookId] ?: -1L
                        DownloadStatus.Downloading(
                            progress = if (total > 0) ((partialBytes * 100) / total).toInt() else 0,
                            bytes = partialBytes,
                            total = total,
                        )
                    }
                    else -> DownloadStatus.Queued
                }
            }

            WorkInfo.State.FAILED -> {
                val book = bookFromTags(info) ?: return null
                if (File(getBookPath(book)).isFile) null
                else DownloadStatus.Failed(info.outputData.getInt(BookDownloadWorker.KEY_REASON, REASON_NETWORK))
            }

            else -> null
        }
    }

    private fun enqueue(url: String, book: Book) {
        if (File(getBookPath(book)).isFile) {
            clearPreparing(book.id)
            return
        }
        dismissed.value = dismissed.value - book.id
        val request = OneTimeWorkRequestBuilder<BookDownloadWorker>()
            .setInputData(
                workDataOf(
                    BookDownloadWorker.KEY_BOOK_ID to book.id,
                    BookDownloadWorker.KEY_TITLE to book.title,
                    BookDownloadWorker.KEY_AUTHOR to book.author,
                    BookDownloadWorker.KEY_PAGES to book.pageCount,
                    BookDownloadWorker.KEY_CATEGORY to book.categoryName,
                    BookDownloadWorker.KEY_URL to url,
                )
            )
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .addTag(WORK_TAG)
            .addTag(TAG_ID + book.id)
            .addTag(TAG_TITLE + book.title)
            .addTag(TAG_CATEGORY + book.categoryName)
            .addTag(TAG_URL + url)
            .addTag(TAG_AUTHOR + book.author)
            .addTag(TAG_PAGES + book.pageCount)
            .build()
        // KEEP: tapping download on a book that is already queued or running does nothing;
        // a finished (or failed) job is replaced by a fresh one.
        WorkManager.getInstance(appContext)
            .enqueueUniqueWork(workName(book.id), ExistingWorkPolicy.KEEP, request)
    }

    private fun workName(bookId: String) = "book-download-$bookId"

    /** Where an unfinished download is kept; outside the library so it never lists as a book. */
    fun partialFile(bookId: String): File =
        File(ShamelaApp.externalMediaDir, "download-tmp/$bookId.part")

    fun cancelDownload(bookId: String) {
        WorkManager.getInstance(appContext).cancelUniqueWork(workName(bookId))
        partialFile(bookId).delete()
    }

    fun cancelAll() {
        _downloads.value.filter { it.status.isActive }.forEach { cancelDownload(it.bookId) }
    }

    /** Starts a failed (or stuck) download again, keeping the bytes already downloaded. */
    fun retry(bookId: String) {
        val info = lastInfos.firstOrNull { it.tagValue(TAG_ID) == bookId } ?: return
        val book = bookFromTags(info) ?: return
        val url = info.tagValue(TAG_URL) ?: return
        WorkManager.getInstance(appContext).cancelUniqueWork(workName(bookId))
        enqueue(url, book)
    }

    fun retryFailed() {
        _downloads.value.filter { it.status is DownloadStatus.Failed }.forEach { retry(it.bookId) }
    }

    /** Removes failed entries from the panel, for good (not only until the app restarts). */
    fun clearFailed() {
        dismissed.value = dismissed.value + _downloads.value
            .filter { it.status is DownloadStatus.Failed }
            .map { it.bookId }
        // Finished jobs stay in WorkManager's database for a day; drop them now.
        WorkManager.getInstance(appContext).pruneWork()
    }

    fun subscribe(subscriber: Subscriber) {
        subscribers.addIfAbsent(subscriber)
    }

    fun unsubscribe(subscriber: Subscriber) {
        subscribers.remove(subscriber)
    }

    fun notifyBookDownloaded(book: Book) {
        DownloadNotifier.onBookCompleted()
        val remaining = _downloads.value.count { it.status.isActive && it.bookId != book.id }
        subscribers.forEach { it.onBookDownloaded(book, remaining == 0) }
    }

    fun reportLinkUnavailable(bookTitle: String, bookId: String? = null) {
        bookId?.let(::clearPreparing)
        _downloadErrors.tryEmit(DownloadError(bookTitle, REASON_NO_LINK))
    }

    /** The download of [bookId] was tapped; it shows as queued until its job exists. */
    fun markPreparing(bookId: String) {
        if (!::appContext.isInitialized) return
        preparing.value = preparing.value + bookId
        scope.launch { refresh() }
    }

    private fun clearPreparing(bookId: String) {
        if (bookId !in preparing.value) return
        preparing.value = preparing.value - bookId
        scope.launch { refresh() }
    }

    /**
     * The original app built links as `BASE_URL + "/" + path` with a BASE_URL that already
     * ended in "/", i.e. `host//category/book.epub`. Both forms are tried (the one known to
     * work first); a 404 on one falls through to the other.
     */
    fun urlCandidates(url: String): List<String> {
        val schemeEnd = url.indexOf("://").takeIf { it >= 0 }?.plus(3) ?: return listOf(url)
        val pathStart = url.indexOf('/', schemeEnd).takeIf { it >= 0 } ?: return listOf(url)
        val host = url.substring(0, pathStart)
        val path = url.substring(pathStart).trimStart('/')
        val double = "$host//$path"
        val single = "$host/$path"
        return if (doubleSlashWorks == false) listOf(single, double) else listOf(double, single)
    }

    fun rememberWorkingUrl(original: String, working: String) {
        doubleSlashWorks = working.substringAfter("://").contains("//")
    }

    fun getBookPath(book: Book): String {
        val bookFileSubPath = "$DOWNLOADS_FOLDER/${book.categoryName}/${book.title}.epub"
        return File(ShamelaApp.externalMediaDir, bookFileSubPath).absolutePath
    }

    private fun bookFromTags(info: WorkInfo): Book? {
        return Book(
            id = info.tagValue(TAG_ID) ?: return null,
            title = info.tagValue(TAG_TITLE) ?: return null,
            author = info.tagValue(TAG_AUTHOR).orEmpty(),
            pageCount = info.tagValue(TAG_PAGES)?.toIntOrNull() ?: 0,
            categoryName = info.tagValue(TAG_CATEGORY) ?: return null,
        )
    }

    private fun WorkInfo.tagValue(prefix: String): String? =
        tags.firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix)

    private fun isOnline(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    // --- Downloads started by older versions through the system DownloadManager. ---

    /**
     * Resolves a finished system-DownloadManager job (from a version before this download
     * engine) to the book it downloaded, or null if it isn't one.
     */
    fun resolveCompletedDownload(downloadId: Long, context: Context): Book? {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
            if (!cursor.moveToFirst()) return null
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val localUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
            if (status != DownloadManager.STATUS_SUCCESSFUL || localUri == null) return null
            val file = File(Uri.parse(localUri).path ?: return null)
            val booksRoot = File(ShamelaApp.externalMediaDir, DOWNLOADS_FOLDER)
            if (file.parentFile?.parentFile?.canonicalPath != booksRoot.canonicalPath) return null
            val categoryName = file.parentFile?.name ?: return null
            val bookTitle = file.nameWithoutExtension
            return Book(
                id = UUID.nameUUIDFromBytes((bookTitle + categoryName).toByteArray()).toString(),
                title = bookTitle,
                author = "",
                pageCount = 0,
                categoryName = categoryName
            )
        }
    }

    /** Fills in author/page count for a book recovered without its metadata. */
    suspend fun withMetadata(book: Book): Book {
        if (book.pageCount > 0) return book
        return FilesBooksRepoImpl.parseBook(File(getBookPath(book)), book.categoryName) ?: book
    }
}
