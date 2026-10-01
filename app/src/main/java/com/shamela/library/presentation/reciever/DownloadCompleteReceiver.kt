package com.shamela.library.presentation.reciever

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.shamela.library.data.local.files.FilesRepoImpl
import com.shamela.library.domain.usecases.books.BooksUseCases
import com.shamela.library.presentation.utils.BookIndexing
import com.shamela.library.presentation.utils.BooksDownloadManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Declared in the manifest (not registered from MainActivity), so downloads that finish while the
 * app is in the background or killed are still saved to the library and indexed for search.
 * The manifest restricts senders to the system DownloadManager.
 */
@AndroidEntryPoint
class DownloadCompleteReceiver : BroadcastReceiver() {

    @Inject
    @FilesRepoImpl
    lateinit var booksUseCases: BooksUseCases

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
        if (downloadId == -1L) return

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        scope.launch {
            try {
                val book = BooksDownloadManager.resolveCompletedDownload(downloadId, appContext)
                    ?.let { BooksDownloadManager.withMetadata(it) }
                if (book != null) {
                    booksUseCases.saveDownloadedBook(book)
                    BookIndexing.enqueue(appContext, book, replace = true)
                    BooksDownloadManager.notifyBookDownloaded(book)
                }
            } catch (e: Exception) {
                Log.e(TAG, "onReceive: failed to handle download $downloadId", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "DownloadCompleteReceiver"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
