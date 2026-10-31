package com.shamela.library.presentation.pdf

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.shamela.library.R
import com.shamela.library.data.local.files.FilesBooksRepoImpl
import com.shamela.library.data.local.files.FilesRepoImpl
import com.shamela.library.domain.usecases.books.BooksUseCases
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receives a PDF shared from another app ("Share" or "Open with"): adds it to the scanned books and
 * opens it. No screen of its own: a toast says what happened.
 */
@AndroidEntryPoint
class PdfShareActivity : ComponentActivity() {

    @Inject
    @FilesRepoImpl
    lateinit var booksUseCases: BooksUseCases

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.shamela.apptheme.presentation.util.AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = sharedUri(intent) ?: return finish()
        lifecycleScope.launch {
            when (val result = PdfImport.import(applicationContext, uri, booksUseCases)) {
                is PdfImport.Result.Added -> {
                    Toast.makeText(this@PdfShareActivity, R.string.pdf_added_to_scanned, Toast.LENGTH_LONG).show()
                    open(result.book)
                }
                is PdfImport.Result.AlreadyThere -> open(result.book)
                PdfImport.Result.Failed ->
                    Toast.makeText(this@PdfShareActivity, R.string.pdf_unreadable, Toast.LENGTH_LONG).show()
            }
            finish()
        }
    }

    private fun open(book: com.shamela.library.domain.model.Book) {
        startActivity(PdfReaderActivity.intent(this, FilesBooksRepoImpl.pdfFileOf(book), book))
    }

    private fun sharedUri(intent: Intent): Uri? = when (intent.action) {
        Intent.ACTION_SEND ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
        Intent.ACTION_VIEW -> intent.data
        else -> null
    }
}
