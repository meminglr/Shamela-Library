package com.shamela.library.presentation.utils

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.shamela.library.R
import com.shamela.library.ShamelaApp
import com.shamela.library.data.local.files.FilesBooksRepoImpl
import com.shamela.library.data.local.files.FilesRepoImpl
import com.shamela.library.domain.model.Book
import com.shamela.library.domain.usecases.books.BooksUseCases
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Copies the books of a picked folder (the previous app's library) in the background, with one
 * progress notification, so it keeps going when the user leaves the screen or the app. Books are
 * saved to the library in batches (each save refreshes every book list), and preparing them for
 * search starts once, after the copying is done, instead of competing with it.
 */
class LibraryImportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        @FilesRepoImpl
        fun booksUseCases(): BooksUseCases
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val treeUri = inputData.getString(KEY_TREE_URI)?.let(Uri::parse) ?: return@withContext Result.failure()
        val resolver = applicationContext.contentResolver
        val dao = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
            .booksUseCases().booksDao
        val title = applicationContext.getString(R.string.import_notif_title)

        report(title, 0, 0)
        val books = LibraryImport.findBooks(resolver, treeUri)
        val libraryRoot = File(ShamelaApp.externalMediaDir, "ShamelaDownloads")
        var imported = 0
        var existing = 0
        var failed = 0
        val batch = ArrayList<Book>()
        var lastReport = 0L

        books.forEachIndexed { index, found ->
            if (isStopped) return@forEachIndexed
            val now = SystemClock.elapsedRealtime()
            if (now - lastReport > 500) {
                lastReport = now
                report(title, index, books.size, found.fileName.removeSuffix(".epub"))
            }
            val result = LibraryImport.copy(resolver, found, libraryRoot)
            val book = result?.let { FilesBooksRepoImpl.parseBook(it.file, found.category) }
            if (result == null || book == null) {
                failed++
                if (result?.copied == true) result.file.delete()
            } else {
                if (result.copied) imported++ else existing++
                batch += book
                if (batch.size >= BATCH) {
                    dao.upsertAllKeepingUserData(batch.toList())
                    batch.clear()
                }
            }
        }
        if (batch.isNotEmpty()) dao.upsertAllKeepingUserData(batch)
        File(ShamelaApp.externalMediaDir, "import-tmp").deleteRecursively()
        BookIndexing.indexLibrary(applicationContext)
        Result.success(
            workDataOf(KEY_IMPORTED to imported, KEY_EXISTING to existing, KEY_FAILED to failed, KEY_TOTAL to books.size)
        )
    }

    private suspend fun report(title: String, done: Int, total: Int, current: String? = null) {
        setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to total))
        val notification = LibraryTasksNotifier.progress(applicationContext, title, current, done, total)
        runCatching { setForeground(LibraryTasksNotifier.foregroundInfo(LibraryTasksNotifier.IMPORT_ID, notification)) }
    }

    companion object {
        const val WORK_NAME = "library-import"
        private const val BATCH = 50
        private const val KEY_TREE_URI = "tree"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_IMPORTED = "imported"
        const val KEY_EXISTING = "existing"
        const val KEY_FAILED = "failed"

        fun start(context: Context, treeUri: Uri) {
            val request = OneTimeWorkRequestBuilder<LibraryImportWorker>()
                .setInputData(workDataOf(KEY_TREE_URI to treeUri.toString()))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
