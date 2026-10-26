package com.shamela.apptheme.presentation.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.shamela.apptheme.data.search.BookIndexer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Indexes one book. Kept only so jobs queued by older versions can still be read; new indexing
 * goes through the app's single library-wide queue, which indexes books one at a time.
 */
class BookPreparationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val path = inputData.getString(EPUB_FILE_PATH) ?: return Result.failure()
        withContext(Dispatchers.IO) { BookIndexer.index(applicationContext, path) }
        return Result.success()
    }

    companion object {
        const val EPUB_FILE_PATH = "EPUB_FILE_PATH"
    }
}
