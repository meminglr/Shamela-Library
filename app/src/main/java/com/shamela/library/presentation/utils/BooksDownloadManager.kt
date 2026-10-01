package com.shamela.library.presentation.utils

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.util.Log
import com.shamela.library.ShamelaApp
import com.shamela.library.data.local.files.FilesBooksRepoImpl
import com.shamela.library.domain.model.Book
import com.shamela.library.domain.model.DownloadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** A failed book download, reported to the UI so the user isn't left guessing. */
data class DownloadError(val bookTitle: String, val reason: Int)

class BooksDownloadManager(context: Context) {

    init {
        initializeWith(context)
    }

    companion object {
        private val _downloadIdMap = ConcurrentHashMap<Long, Book>()
        private val _bookIdToDownloadId = ConcurrentHashMap<String, Long>()
        private val _statusMap = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
        val downloadStatusFlow: StateFlow<Map<String, DownloadStatus>> = _statusMap.asStateFlow()
        private val _downloadErrors = MutableSharedFlow<DownloadError>(extraBufferCapacity = 16)
        val downloadErrors: SharedFlow<DownloadError> = _downloadErrors.asSharedFlow()
        private val pollerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        private var pollerJob: Job? = null
        private val subscribers = CopyOnWriteArrayList<Subscriber>()
        private lateinit var downManager: DownloadManager
        const val TAG = "BooksDownloadManager"
        const val FILE_ALREADY_EXISTS = -1L
        /** [DownloadError.reason] when no download link could be built (e.g. BASE_URL missing). */
        const val REASON_NO_LINK = -100
        private const val DOWNLOADS_FOLDER = "ShamelaDownloads"

        private fun initializeWith(context: Context) {
            if (!::downManager.isInitialized) {
                downManager = context.applicationContext
                    .getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            }
        }

        fun subscribe(subscriber: Subscriber) {
            subscribers.addIfAbsent(subscriber)
        }

        fun unsubscribe(subscriber: Subscriber) {
            subscribers.remove(subscriber)
        }

        fun notifyBookDownloaded(book: Book) {
            Log.d(TAG, "notifyBookDownloaded: book=${book.title}, remaining=${_downloadIdMap.size}")
            subscribers.forEach { it.onBookDownloaded(book, _downloadIdMap.isEmpty()) }
        }

        fun reportLinkUnavailable(bookTitle: String) {
            _downloadErrors.tryEmit(DownloadError(bookTitle, REASON_NO_LINK))
        }

        fun clearStatus(bookId: String) {
            updateStatus(bookId, DownloadStatus.NotDownloaded)
        }

        /**
         * Resolves a finished DownloadManager job to the book it downloaded, or null if it failed or
         * isn't one of our book downloads (e.g. the app-update APK). Works after process death: the
         * in-memory id→book map is only a fast path, the file location is the source of truth.
         * DownloadManager only returns this app's own downloads, so foreign ids resolve to null.
         */
        fun resolveCompletedDownload(downloadId: Long, context: Context): Book? {
            initializeWith(context)
            val knownBook = _downloadIdMap[downloadId]
            downManager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
                if (!cursor.moveToFirst()) {
                    knownBook?.let { forget(downloadId, it) }
                    return null
                }
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val localUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                if (status == DownloadManager.STATUS_FAILED) {
                    val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    knownBook?.let { reportFailure(downloadId, it, reason) }
                    return null
                }
                if (status != DownloadManager.STATUS_SUCCESSFUL || localUri == null) return null

                val file = File(Uri.parse(localUri).path ?: return null)
                val booksRoot = File(ShamelaApp.externalMediaDir, DOWNLOADS_FOLDER)
                if (file.parentFile?.parentFile?.canonicalPath != booksRoot.canonicalPath) return null

                knownBook?.let { forget(downloadId, it) }
                val categoryName = file.parentFile?.name ?: return null
                return knownBook ?: placeholderBook(file, categoryName)
            }
        }

        /** Fills in author/page count for a book recovered without the in-memory metadata. */
        suspend fun withMetadata(book: Book): Book {
            if (book.pageCount > 0) return book
            return FilesBooksRepoImpl.parseBook(File(getBookPath(book)), book.categoryName) ?: book
        }

        private fun placeholderBook(file: File, categoryName: String): Book {
            val bookTitle = file.nameWithoutExtension
            return Book(
                id = UUID.nameUUIDFromBytes((bookTitle + categoryName).toByteArray()).toString(),
                title = bookTitle,
                author = "",
                pageCount = 0,
                categoryName = categoryName
            )
        }

        private fun forget(downloadId: Long, book: Book) {
            _downloadIdMap.remove(downloadId)
            _bookIdToDownloadId.remove(book.id)
        }

        private fun reportFailure(downloadId: Long, book: Book, reason: Int) {
            // Whoever removes the entry first (poller or receiver) reports it, so it's reported once.
            if (_downloadIdMap.remove(downloadId) == null) return
            _bookIdToDownloadId.remove(book.id)
            updateStatus(book.id, DownloadStatus.NotDownloaded)
            downManager.remove(downloadId)
            Log.w(TAG, "download failed: ${book.title}, reason=$reason")
            _downloadErrors.tryEmit(DownloadError(book.title, reason))
        }

        fun getBookPath(book: Book): String {
            val downloadsFolder = ShamelaApp.externalMediaDir
            val bookFileSubPath = "$DOWNLOADS_FOLDER/${book.categoryName}/${book.title}.epub"
            return File(downloadsFolder, bookFileSubPath).absolutePath
        }

        fun cancelDownload(bookId: String) {
            val downloadId = _bookIdToDownloadId[bookId] ?: return
            _downloadIdMap.remove(downloadId)
            _bookIdToDownloadId.remove(bookId)
            downManager.remove(downloadId)
            updateStatus(bookId, DownloadStatus.NotDownloaded)
        }

        private fun updateStatus(bookId: String, status: DownloadStatus) {
            _statusMap.update { current ->
                if (status == DownloadStatus.NotDownloaded) current - bookId
                else current + (bookId to status)
            }
        }

        private fun ensurePollerRunning() {
            if (pollerJob?.isActive == true) return
            pollerJob = pollerScope.launch {
                while (_downloadIdMap.isNotEmpty()) {
                    val ids = _downloadIdMap.keys.toLongArray()
                    if (ids.isEmpty()) break
                    val cursor = downManager.query(DownloadManager.Query().setFilterById(*ids))
                    cursor.use {
                        while (it.moveToNext()) {
                            val dlId = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                            val downloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                            val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                            val book = _downloadIdMap[dlId] ?: continue
                            val progress = if (total > 0) ((downloaded * 100) / total).toInt() else 0

                            when (status) {
                                DownloadManager.STATUS_RUNNING,
                                DownloadManager.STATUS_PENDING,
                                DownloadManager.STATUS_PAUSED ->
                                    updateStatus(book.id, DownloadStatus.Downloading(progress))

                                DownloadManager.STATUS_SUCCESSFUL ->
                                    updateStatus(book.id, DownloadStatus.Downloading(100))

                                DownloadManager.STATUS_FAILED -> {
                                    val reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                                    reportFailure(dlId, book, reason)
                                }
                            }
                        }
                    }
                    delay(500)
                }
            }
        }

        private fun enqueue(downloadUri: Uri, book: Book): Long? {
            val bookFileSubPath = "$DOWNLOADS_FOLDER/${book.categoryName}/${book.title}.epub"
            val request = DownloadManager.Request(downloadUri)
                .setTitle(ShamelaApp.appLabel)
                .setDescription(book.title)
                .setMimeType("application/epub+zip")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationUri(Uri.fromFile(File(ShamelaApp.externalMediaDir, bookFileSubPath)))
            return try {
                val downloadId = downManager.enqueue(request)
                _downloadIdMap[downloadId] = book
                _bookIdToDownloadId[book.id] = downloadId
                updateStatus(book.id, DownloadStatus.Downloading(0))
                downloadId
            } catch (e: Exception) {
                Log.e(TAG, "enqueue failed for ${book.title}", e)
                _downloadErrors.tryEmit(DownloadError(book.title, DownloadManager.ERROR_UNKNOWN))
                null
            }
        }
    }

    fun downloadBook(downloadUri: Uri, book: Book, bookCategory: String): Long {
        val target = book.copy(categoryName = bookCategory)
        val isFileAlreadyDownloaded = File(getBookPath(target)).isFile
        if (isFileAlreadyDownloaded || _bookIdToDownloadId.containsKey(book.id)) return FILE_ALREADY_EXISTS
        val downloadId = enqueue(downloadUri, target) ?: return FILE_ALREADY_EXISTS
        ensurePollerRunning()
        return downloadId
    }

    fun downloadSection(booksMap: Map<Book, Uri?>) {
        Log.d(TAG, "downloadSection: ${booksMap.size} books")
        booksMap.forEach { (book, uri) ->
            if (uri != null && !File(getBookPath(book)).isFile && !_bookIdToDownloadId.containsKey(book.id)) {
                enqueue(uri, book)
            }
        }
        ensurePollerRunning()
    }

    fun cancelBookDownload(downloadId: Long) {
        val book = _downloadIdMap.remove(downloadId) ?: return
        _bookIdToDownloadId.remove(book.id)
        downManager.remove(downloadId)
        updateStatus(book.id, DownloadStatus.NotDownloaded)
    }

    interface Subscriber {
        fun onBookDownloaded(book: Book, isLastBook: Boolean)
    }
}
