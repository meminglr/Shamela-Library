package com.shamela.library.presentation.pdf

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.shamela.library.ShamelaApp
import com.shamela.library.data.local.files.FilesBooksRepoImpl
import com.shamela.library.data.local.files.StorageDirs
import com.shamela.library.data.remote.shamela.ShamelaCatalog
import com.shamela.library.domain.model.Book
import com.shamela.library.domain.usecases.books.BooksUseCases
import com.shamela.library.presentation.utils.BooksDownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Adds a PDF from elsewhere (picked in settings, or shared from another app) to the scanned books. */
object PdfImport {
    sealed class Result {
        data class Added(val book: Book) : Result()
        data class AlreadyThere(val book: Book) : Result()
        object Failed : Result()
    }

    fun isPdf(context: Context, uri: Uri): Boolean =
        context.contentResolver.getType(uri) == "application/pdf" ||
            displayName(context, uri).endsWith(".pdf", ignoreCase = true)

    fun displayName(context: Context, uri: Uri): String =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "PDF"

    suspend fun import(context: Context, uri: Uri, useCases: BooksUseCases): Result = withContext(Dispatchers.IO) {
        val title = ShamelaCatalog.safeTitle(displayName(context, uri).removeSuffix(".pdf").removeSuffix(".PDF"))
            .ifEmpty { "PDF" }
        val folder = File(ShamelaApp.externalMediaDir, "ShamelaDownloads/${ShamelaApp.SCANNED_BOOKS_CATEGORY}")
        if (!StorageDirs.ensure(folder, context)) return@withContext Result.Failed
        val destination = File(folder, "$title.pdf")
        if (destination.isFile) {
            FilesBooksRepoImpl.parsePdf(destination, ShamelaApp.SCANNED_BOOKS_CATEGORY)?.let {
                return@withContext Result.AlreadyThere(it)
            }
        }
        val partial = File(folder, ".$title.pdf.part")
        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input -> partial.outputStream().use { input.copyTo(it) } }
        }.getOrNull() != null
        // Only a PDF Android can draw is kept.
        val readable = copied && FilesBooksRepoImpl.parsePdf(partial, ShamelaApp.SCANNED_BOOKS_CATEGORY) != null
        val book = if (readable && partial.renameTo(destination)) {
            FilesBooksRepoImpl.parsePdf(destination, ShamelaApp.SCANNED_BOOKS_CATEGORY)
        } else null
        if (book == null) {
            partial.delete()
            return@withContext Result.Failed
        }
        useCases.saveDownloadedBook(book)
        BooksDownloadManager.notifyBookAdded(book)
        Result.Added(book)
    }
}
