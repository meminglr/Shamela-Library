package com.shamela.library.presentation.utils

import com.shamela.library.data.local.files.StorageDirs
import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.shamela.library.data.local.files.FilesBooksRepoImpl
import com.shamela.library.data.local.files.FilesRepoImpl
import com.shamela.library.data.remote.archive.ArchiveOrg
import com.shamela.library.data.remote.openiti.OpenItiBookBuilder
import com.shamela.library.data.remote.openiti.OpenItiBooksRepoImpl
import com.shamela.library.data.remote.openiti.OpenItiNotFoundException
import com.shamela.library.data.remote.shamela.ShamelaBookBuilder
import com.shamela.library.data.remote.shamela.ShamelaBooksRepoImpl
import com.shamela.library.data.remote.shamela.ShamelaChallengeException
import com.shamela.library.data.remote.shamela.ShamelaNotFoundException
import com.shamela.library.domain.model.Book
import com.shamela.library.domain.usecases.books.BooksUseCases
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads one book. Unlike the system DownloadManager this reports every state to the app
 * (queued, connecting, bytes and speed, waiting for the network), and survives interruptions:
 * the partial file is kept and the next attempt asks the server only for the missing bytes
 * (HTTP Range). WorkManager re-runs the worker when the connection comes back (network
 * constraint) or after a growing delay (exponential backoff) for server errors.
 */
class BookDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        @FilesRepoImpl
        fun booksUseCases(): BooksUseCases

        fun openItiRepo(): OpenItiBooksRepoImpl
    }

    private sealed class Transfer {
        object Done : Transfer()
        object NotFound : Transfer()
        object Retry : Transfer()
        data class Fatal(val reason: Int) : Transfer()
    }

    override suspend fun doWork(): Result {
        val book = Book(
            id = inputData.getString(KEY_BOOK_ID) ?: return Result.failure(),
            title = inputData.getString(KEY_TITLE) ?: return Result.failure(),
            author = inputData.getString(KEY_AUTHOR).orEmpty(),
            pageCount = inputData.getInt(KEY_PAGES, 0),
            categoryName = inputData.getString(KEY_CATEGORY) ?: return Result.failure(),
        )
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        // A volume of a scanned book (archive.org) is saved as a PDF.
        val scanned = ArchiveOrg.isVolumeLink(url)
        val destination = File(if (scanned) BooksDownloadManager.getPdfPath(book) else BooksDownloadManager.getBookPath(book))
        if (destination.isFile) return finish(book, destination)

        setProgress(workDataOf(KEY_STATE to STATE_QUEUED))
        ShamelaBooksRepoImpl.bookIdOf(url)?.let { shamelaId ->
            return shamelaSlots.withPermit { build(book, shamelaId, destination) }
        }
        OpenItiBooksRepoImpl.bookIdOf(url)?.let { openItiId ->
            return slots.withPermit { buildOpenIti(book, openItiId, destination) }
        }
        return slots.withPermit { download(book, url, destination, scanned) }
    }

    /** A shamela.ws book: its EPUB is built on the device from the site's pages. */
    private suspend fun build(book: Book, shamelaId: Int, destination: File): Result {
        setProgress(workDataOf(KEY_STATE to STATE_CONNECTING, KEY_UNIT to UNIT_PAGES))
        val partial = BooksDownloadManager.partialFile(book.id)
        return try {
            ShamelaBookBuilder(applicationContext.cacheDir).build(
                bookId = shamelaId,
                title = book.title,
                author = book.author,
                output = partial,
            ) { done, total ->
                setProgress(
                    workDataOf(
                        KEY_STATE to STATE_DOWNLOADING,
                        KEY_UNIT to UNIT_PAGES,
                        KEY_BYTES to done.toLong(),
                        KEY_TOTAL to total.toLong(),
                    )
                )
            }
            // The built book stays in download-tmp if its folder can't be made: a retry reuses it.
            if (!StorageDirs.ensure(destination.parentFile!!, applicationContext)) {
                return Result.failure(workDataOf(KEY_REASON to BooksDownloadManager.REASON_STORAGE))
            }
            if (destination.exists()) destination.delete()
            if (!partial.renameTo(destination)) {
                partial.copyTo(destination, overwrite = true)
                partial.delete()
            }
            finish(book, destination)
        } catch (e: ShamelaChallengeException) {
            Result.failure(workDataOf(KEY_REASON to BooksDownloadManager.REASON_SHAMELA_VERIFY))
        } catch (e: ShamelaNotFoundException) {
            fail(BooksDownloadManager.REASON_NOT_FOUND, partial)
        } catch (e: IOException) {
            Log.w(TAG, "shamela book $shamelaId: ${e.message}")
            if ((partial.parentFile?.usableSpace ?: Long.MAX_VALUE) < 5_000_000) {
                fail(BooksDownloadManager.REASON_NO_SPACE, partial)
            } else retryOrFail(BooksDownloadManager.REASON_NETWORK)
        }
    }

    /** A Kütüphane 3 book: its OpenITI text is downloaded, then built into an EPUB on the device. */
    private suspend fun buildOpenIti(book: Book, openItiId: String, destination: File): Result {
        val entry = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
            .openItiRepo().findById(openItiId)
            ?: return Result.failure(workDataOf(KEY_REASON to BooksDownloadManager.REASON_NOT_FOUND))
        setProgress(workDataOf(KEY_STATE to STATE_CONNECTING))
        val partial = BooksDownloadManager.partialFile(book.id)
        if (!StorageDirs.ensure(partial.parentFile!!, applicationContext)) {
            return Result.failure(workDataOf(KEY_REASON to BooksDownloadManager.REASON_STORAGE))
        }
        val builder = OpenItiBookBuilder(applicationContext.cacheDir)
        return try {
            builder.build(entry, partial) { done, total ->
                setProgress(workDataOf(KEY_STATE to STATE_DOWNLOADING, KEY_BYTES to done, KEY_TOTAL to total))
            }
            if (!StorageDirs.ensure(destination.parentFile!!, applicationContext)) {
                return Result.failure(workDataOf(KEY_REASON to BooksDownloadManager.REASON_STORAGE))
            }
            if (destination.exists()) destination.delete()
            if (!partial.renameTo(destination)) {
                partial.copyTo(destination, overwrite = true)
                partial.delete()
            }
            finish(book, destination)
        } catch (e: OpenItiNotFoundException) {
            builder.discard(entry)
            fail(BooksDownloadManager.REASON_NOT_FOUND, partial)
        } catch (e: IOException) {
            Log.w(TAG, "openiti book $openItiId: ${e.message}")
            if ((partial.parentFile?.usableSpace ?: Long.MAX_VALUE) < 5_000_000) {
                fail(BooksDownloadManager.REASON_NO_SPACE, partial)
            } else retryOrFail(BooksDownloadManager.REASON_NETWORK)
        }
    }

    private suspend fun download(book: Book, url: String, destination: File, scanned: Boolean = false): Result {
        val partial = BooksDownloadManager.partialFile(book.id)
        if (!StorageDirs.ensure(partial.parentFile!!, applicationContext)) {
            return Result.failure(workDataOf(KEY_REASON to BooksDownloadManager.REASON_STORAGE))
        }
        setProgress(workDataOf(KEY_STATE to STATE_CONNECTING, KEY_BYTES to partial.length()))

        var transferred = false
        // The link-form and connection workarounds are for the book server only.
        val candidates = if (scanned) listOf(url) else BooksDownloadManager.urlCandidates(url)
        for (candidate in candidates) {
            when (val result = withContext(Dispatchers.IO) { transfer(candidate, partial, plain = scanned) }) {
                Transfer.Done -> {
                    if (!scanned) BooksDownloadManager.rememberWorkingUrl(url, candidate)
                    transferred = true
                }
                Transfer.NotFound -> continue
                Transfer.Retry -> return retryOrFail(BooksDownloadManager.REASON_NETWORK)
                is Transfer.Fatal -> return fail(result.reason, partial)
            }
            break
        }
        if (!transferred) return fail(BooksDownloadManager.REASON_NOT_FOUND, partial)

        if (!StorageDirs.ensure(destination.parentFile!!, applicationContext)) {
            return Result.failure(workDataOf(KEY_REASON to BooksDownloadManager.REASON_STORAGE))
        }
        if (destination.exists()) destination.delete()
        if (!partial.renameTo(destination)) {
            // Different volume or a race: fall back to copying.
            partial.copyTo(destination, overwrite = true)
            partial.delete()
        }
        return finish(book, destination)
    }

    private suspend fun transfer(url: String, partial: File, plain: Boolean = false): Transfer {
        var existing = if (partial.isFile) partial.length() else 0L
        val configure: HttpURLConnection.() -> Unit = {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("Accept-Encoding", "identity")
            if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
        }
        val connection = try {
            // Connects here, working around operators that reset connections to the book server.
            if (plain) (URL(url).openConnection() as HttpURLConnection).apply(configure).apply { connect() }
            else com.shamela.library.data.remote.BookServerConnection.open(url, configure)
        } catch (e: IOException) {
            Log.w(TAG, "connect failed: ${e.message}")
            return Transfer.Retry
        }
        try {
            val code = try {
                connection.responseCode
            } catch (e: IOException) {
                Log.w(TAG, "connect failed: ${e.message}")
                return Transfer.Retry
            }
            val total: Long
            when (code) {
                HttpURLConnection.HTTP_NOT_FOUND, HttpURLConnection.HTTP_GONE -> return Transfer.NotFound
                416 -> {
                    // The partial file is already complete (or the server changed the file).
                    val size = connection.getHeaderField("Content-Range")?.substringAfter('/')?.toLongOrNull()
                    if (size != null && size == existing) return Transfer.Done
                    partial.delete()
                    return Transfer.Retry
                }
                HttpURLConnection.HTTP_PARTIAL -> {
                    total = connection.getHeaderField("Content-Range")?.substringAfter('/')?.toLongOrNull()
                        ?: (existing + connection.contentLengthLong).takeIf { connection.contentLengthLong >= 0 }
                        ?: -1L
                }
                HttpURLConnection.HTTP_OK -> {
                    // The server ignored the range: start over.
                    existing = 0L
                    total = connection.contentLengthLong
                }
                429, in 500..599 -> return Transfer.Retry
                else -> return Transfer.Fatal(BooksDownloadManager.REASON_HTTP)
            }

            if (total > 0) {
                val free = partial.parentFile?.usableSpace ?: Long.MAX_VALUE
                if (free < (total - existing) + 1_000_000) return Transfer.Fatal(BooksDownloadManager.REASON_NO_SPACE)
            }

            var bytes = existing
            var lastReport = 0L
            var windowStart = SystemClock.elapsedRealtime()
            var windowBytes = 0L
            var speed = 0L
            connection.inputStream.use { input ->
                FileOutputStream(partial, existing > 0).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = try {
                            input.read(buffer)
                        } catch (e: IOException) {
                            Log.w(TAG, "read interrupted at $bytes: ${e.message}")
                            return Transfer.Retry
                        }
                        if (read < 0) break
                        try {
                            output.write(buffer, 0, read)
                        } catch (e: IOException) {
                            return Transfer.Fatal(BooksDownloadManager.REASON_NO_SPACE)
                        }
                        bytes += read
                        windowBytes += read
                        val now = SystemClock.elapsedRealtime()
                        if (now - windowStart >= 1000) {
                            speed = windowBytes * 1000 / (now - windowStart)
                            windowStart = now
                            windowBytes = 0
                        }
                        if (now - lastReport >= 300) {
                            lastReport = now
                            setProgress(
                                workDataOf(
                                    KEY_STATE to STATE_DOWNLOADING,
                                    KEY_BYTES to bytes,
                                    KEY_TOTAL to total,
                                    KEY_SPEED to speed,
                                )
                            )
                        }
                    }
                }
            }
            // A connection that ended early looks like a normal end of stream; check the size.
            if (total > 0 && bytes < total) return Transfer.Retry
            setProgress(workDataOf(KEY_STATE to STATE_DOWNLOADING, KEY_BYTES to bytes, KEY_TOTAL to bytes))
            return Transfer.Done
        } finally {
            connection.disconnect()
        }
    }

    private fun retryOrFail(reason: Int): Result =
        if (runAttemptCount + 1 >= MAX_ATTEMPTS) Result.failure(workDataOf(KEY_REASON to reason))
        else Result.retry()

    private fun fail(reason: Int, partial: File): Result {
        partial.delete()
        return Result.failure(workDataOf(KEY_REASON to reason))
    }

    /** Adds the downloaded book to the library and the search index. */
    private suspend fun finish(book: Book, file: File): Result {
        // A PDF has no author inside: the one the download was started with stays.
        val parsed = FilesBooksRepoImpl.parseBook(file, book.categoryName)
            ?.let { if (FilesBooksRepoImpl.isPdf(file)) it.copy(author = book.author) else it }
        if (parsed == null) {
            // Not a readable EPUB (e.g. an error page saved as a file).
            file.delete()
            return Result.failure(workDataOf(KEY_REASON to BooksDownloadManager.REASON_CORRUPT))
        }
        val useCases = EntryPointAccessors
            .fromApplication(applicationContext, Dependencies::class.java)
            .booksUseCases()
        useCases.saveDownloadedBook(parsed)
        BookIndexing.enqueue(applicationContext, parsed, replace = true)
        BooksDownloadManager.notifyBookDownloaded(parsed)
        return Result.success()
    }

    companion object {
        private const val TAG = "BookDownloadWorker"

        /** Books downloading at the same time; the rest wait in the queue. */
        private val slots = Semaphore(3)

        /** shamela.ws books built at the same time: one, to stay gentle on the site. */
        private val shamelaSlots = Semaphore(1)

        /** Attempts for server errors; network loss doesn't count (the worker waits for it). */
        const val MAX_ATTEMPTS = 12

        const val KEY_BOOK_ID = "bookId"
        const val KEY_TITLE = "title"
        const val KEY_AUTHOR = "author"
        const val KEY_PAGES = "pages"
        const val KEY_CATEGORY = "category"
        const val KEY_URL = "url"

        const val KEY_STATE = "state"
        const val KEY_BYTES = "bytes"
        const val KEY_TOTAL = "total"
        const val KEY_SPEED = "speed"
        const val KEY_REASON = "reason"
        /** [UNIT_PAGES] when bytes/total count pages (a shamela.ws book being built). */
        const val KEY_UNIT = "unit"
        const val UNIT_PAGES = "pages"

        const val STATE_QUEUED = "queued"
        const val STATE_CONNECTING = "connecting"
        const val STATE_DOWNLOADING = "downloading"
    }
}
