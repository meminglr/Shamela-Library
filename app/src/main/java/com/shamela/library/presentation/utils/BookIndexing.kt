package com.shamela.library.presentation.utils

import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.shamela.apptheme.data.db.DatabaseHelper
import com.shamela.apptheme.data.search.BookIndexer
import com.shamela.apptheme.presentation.worker.BookPreparationWorker
import com.shamela.library.R
import com.shamela.library.ShamelaApp
import com.shamela.library.domain.model.Book
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

/**
 * Full-text-search indexing of downloaded books, through one library-wide queue: a single job
 * indexes the books that have no index yet, one at a time, on a low-priority thread, with one
 * progress notification. (One job per book made a 1000-book import start 1000 jobs at once, which
 * froze the app and posted a notification per book.)
 */
object BookIndexing {
    private const val TAG = "BookIndexing"
    private const val WORK_NAME = "index-library"

    /** Indexes [book]; with [replace], also when it already has an index (re-downloaded file). */
    fun enqueue(context: Context, book: Book, replace: Boolean = false) {
        // A scanned book has no text to index.
        if (BooksDownloadManager.getBookPath(book).endsWith(".pdf")) return
        val paths = if (replace) arrayOf(BooksDownloadManager.getBookPath(book)) else emptyArray()
        start(context, paths)
    }

    /** Indexes every book in the library that has no index yet. */
    fun indexLibrary(context: Context) = start(context, emptyArray())

    private fun start(context: Context, forcedPaths: Array<String>) {
        val request = OneTimeWorkRequestBuilder<LibraryIndexWorker>()
            .setInputData(workDataOf(LibraryIndexWorker.KEY_PATHS to forcedPaths))
            .build()
        // A running job finishes its pass first; the appended one then picks up what's new (and
        // finds nothing to do if the running job already got to it).
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /**
     * Drops the per-book jobs older versions queued (up to one per book), which would otherwise
     * all start at once, and indexes those books through the queue instead.
     */
    fun migrateOldJobs(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(BookPreparationWorker::class.java.name)
    }

    /**
     * Indexes every downloaded book whose file exists but has no pages in the search index — e.g. a
     * download that finished while the app was killed, or an index lost to an older app version.
     */
    fun indexMissing(context: Context) {
        migrateOldJobs(context)
        indexLibrary(context)
    }

    internal fun libraryFiles(): List<File> =
        File(ShamelaApp.externalMediaDir, "ShamelaDownloads")
            .walkTopDown()
            .maxDepth(2)
            .filter { it.isFile && it.name.endsWith(".epub", ignoreCase = true) }
            .toList()

    /** The low-priority thread books are read on, so scrolling and reading stay smooth. */
    internal val dispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            runnable.run()
        }, "book-indexer")
    }.asCoroutineDispatcher()

    internal fun log(message: String) = Log.d(TAG, message)
}

class LibraryIndexWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(BookIndexing.dispatcher) {
        val database = DatabaseHelper.getInstance(applicationContext)
        val forced = inputData.getStringArray(KEY_PATHS).orEmpty().toMutableSet()
        val attempted = HashSet<String>()
        var done = 0
        var lastNotified = 0L

        // Books can arrive while we work (a download finishes); look again until nothing is left.
        while (!isStopped) {
            val indexed = database.indexedBookIds()
            val pending = BookIndexing.libraryFiles().filter { file ->
                file.path !in attempted && (file.path in forced || BookIndexer.bookIdOf(file) !in indexed)
            }
            if (pending.isEmpty()) break
            val total = done + pending.size
            BookIndexing.log("indexing ${pending.size} books")
            for (file in pending) {
                if (isStopped) break
                val now = SystemClock.elapsedRealtime()
                if (lastNotified == 0L || now - lastNotified > 1000) {
                    lastNotified = now
                    val notification = LibraryTasksNotifier.progress(
                        applicationContext,
                        applicationContext.getString(R.string.index_notif_title),
                        file.name.removeSuffix(".epub"),
                        done,
                        total,
                    )
                    // Not allowed from the background on some versions; then it just runs as a normal job.
                    runCatching {
                        setForeground(LibraryTasksNotifier.foregroundInfo(LibraryTasksNotifier.INDEX_ID, notification))
                    }
                }
                attempted += file.path
                forced -= file.path
                runCatching { BookIndexer.index(applicationContext, file.path) }
                    .onFailure { BookIndexing.log("failed ${file.name}: ${it.message}") }
                done++
            }
        }
        Result.success()
    }

    companion object {
        const val KEY_PATHS = "paths"
    }
}
