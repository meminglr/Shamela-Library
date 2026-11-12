package com.shamela.library.data.local.files

import android.content.Context
import android.net.Uri
import android.util.Log
import android.content.Intent
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.folioreader.FolioReader
import com.shamela.library.ShamelaApp
import com.shamela.library.data.catalog.SiteSections
import com.shamela.library.domain.model.Book
import com.shamela.library.domain.model.Category
import com.shamela.library.domain.model.Quote
import com.shamela.library.domain.repo.BooksRepository
import com.shamela.library.domain.search.BookSearchMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileFilter
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import javax.inject.Qualifier


@Qualifier
annotation class FilesRepoImpl

object FilesBooksRepoImpl : BooksRepository {
    private const val TAG = "FilesBooksRepoImpl"
    private const val BASE_DOWNLOAD_DIRECTORY = "ShamelaDownloads"

    private val downloadsFolder = ShamelaApp.externalMediaDir
    private val shamelaBooks = File(downloadsFolder, BASE_DOWNLOAD_DIRECTORY)

    /** A library book is `<section>/<title>.epub`, or `<section>/<title>.pdf` for a scanned book. */
    private val bookExtensions = listOf(".epub", ".pdf")

    fun isBookFile(file: File): Boolean = file.isFile && bookExtensions.any { file.name.endsWith(it, ignoreCase = true) }

    fun titleOf(file: File): String = file.name.removeSuffix(".epub").removeSuffix(".pdf")

    fun isPdf(file: File): Boolean = file.name.endsWith(".pdf", ignoreCase = true)

    fun pdfFileOf(book: Book): File = File(shamelaBooks, "${book.categoryName}/${book.title}.pdf")

    /** Opens the book in the reader for its format; false if its file isn't in the library. */
    fun openEpub(
        book: Book,
        startPageHref: String = "",
        onAddQuoteToFavorite: (quote: Quote) -> Unit,
    ): Boolean {

        //TODO-> future work: if the file doesn't exist: handle it in the ui to show a  download button
        val bookFile = File(shamelaBooks, "${book.categoryName}/${book.title}.epub")
        val pdfFile = pdfFileOf(book)
        if (!bookFile.isFile && pdfFile.isFile) {
            val context = ShamelaApp.appContext
            context.startActivity(
                com.shamela.library.presentation.pdf.PdfReaderActivity.intent(context, pdfFile, book)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return true
        }
        return if (bookFile.isFile) {
            FolioReader.get().openBook(
                assetOrSdcardPath = bookFile.path,
                startPageHref = startPageHref,
                bookId = book.id,
                onAddQuoteToFavorite = { pageIndex: Int, pageHref: String, text: String ->
                    onAddQuoteToFavorite(
                        Quote(
                            text = text,
                            pageIndex = pageIndex,
                            pageHref = pageHref,
                            bookName = book.title,
                            bookId = book.id,
                        )
                    )
                }
            )
            Log.d(TAG, "openEpub: $book is a file, absolute path: ${bookFile.absoluteFile}")
            true
        } else {
            Log.w(TAG, "openEpub: $book is NOT a file, absolute path: ${bookFile.absoluteFile}")
            false
        }

    }

    override fun getCategories(): Flow<Category> = channelFlow {
        Log.d(TAG, "Loading all categories")
        try {
            if (shamelaBooks.exists() && shamelaBooks.isDirectory) {
                // Folders of shamela.ws sections are counted in the section they're shown under.
                val counts = LinkedHashMap<String, Int>()
                shamelaBooks.listFiles(FileFilter { it.isDirectory })?.forEach { folder ->
                    val section = SiteSections.mergedSectionOf(folder.name)
                    counts[section] = (counts[section] ?: 0) + (folder.listFiles { file -> isBookFile(file) }?.size ?: 0)
                }
                counts.entries.forEachIndexed { index, (section, bookCount) ->
                    if (bookCount > 0) send(Category("$index", section, bookCount))
                }
            } else {
                Log.w(TAG, "getCategories: shamelaBooks doesn't exist")
            }
        } catch (e: IOException) {
            Log.e(TAG, "getCategories: IOException [${e.message}]")
        }
    }


    override fun getBooksByCategory(categoryName: String): Flow<Book> = channelFlow {
        openShamelaFolder { categoriesFolders ->
            categoriesFolders?.let {
                categoriesFolders.filter { SiteSections.mergedSectionOf(it.name) == categoryName }.forEach { categoryFolder ->
                    val bookFiles = categoryFolder.listFiles { file -> isBookFile(file) }
                    bookFiles?.forEach { bookFile ->
                        val bookTitle = titleOf(bookFile)
                        val uuidName = bookTitle + categoryFolder.name
                        val initialBook = Book(
                            id = UUID.nameUUIDFromBytes(uuidName.toByteArray()).toString(),
                            title = bookTitle,
                            author = "-",
                            pageCount = 0,
                            categoryName = categoryFolder.name
                        )
                        send(initialBook)
                    }
                    bookFiles?.forEach { bookFile ->
                        parseBook(bookFile, categoryFolder.name)?.let { book ->
                            send(book)
                        }
                    }
                }
            }
        }
    }

    override fun searchBooksByName(categoryName: String, query: String): Flow<Book> = channelFlow {
        openShamelaFolder { categories ->
            val folders = if (categoryName == "all") categories.orEmpty()
            else categories?.filter { SiteSections.mergedSectionOf(it.name) == categoryName }.orEmpty()
            // Match on the file name first (cheap), then parse only the hits for author/page count.
            val files = folders.flatMap { folder ->
                folder.listFiles { file -> isBookFile(file) }
                    .orEmpty()
                    .map { file -> placeholderBook(file, folder.name) to file }
            }.toMap()
            val matches = BookSearchMatcher.search(files.keys, query).map { it to files.getValue(it) }
            matches.forEach { (placeholder, file) ->
                send(parseBook(file, placeholder.categoryName) ?: placeholder)
            }
        }
    }

    /** The folders on disk shown as [section]: the known source names plus any other folder mapping to it. */
    fun foldersOf(section: String): List<String> {
        val onDisk = shamelaBooks.listFiles(FileFilter { it.isDirectory })?.map { it.name }.orEmpty()
            .filter { SiteSections.mergedSectionOf(it) == section }
        return (SiteSections.foldersOf(section) + onDisk).distinct()
    }

    private fun placeholderBook(bookFile: File, categoryName: String): Book {
        val bookTitle = titleOf(bookFile)
        return Book(
            id = UUID.nameUUIDFromBytes((bookTitle + categoryName).toByteArray()).toString(),
            title = bookTitle,
            author = "-",
            pageCount = 0,
            categoryName = categoryName
        )
    }

    override fun getAllBooks(): Flow<Book> = channelFlow {
        Log.d(TAG, "Loading all books")
        openShamelaFolder { categories ->
            categories?.forEach { folder ->
                folder.listFiles { file -> isBookFile(file) }?.forEach { bookFile ->
                    val bookTitle = titleOf(bookFile)
                    val uuidName = bookTitle + folder.name
                    val initialBook = Book(
                        id = UUID.nameUUIDFromBytes(uuidName.toByteArray()).toString(),
                        title = bookTitle,
                        author = "-",
                        pageCount = 0,
                        categoryName = folder.name
                    )
                    send(initialBook)
                }
            }
            categories?.forEach { folder ->
                folder.listFiles { file -> isBookFile(file) }?.forEach { bookFile ->
                    parseBook(bookFile, folder.name)?.let { book ->
                        send(book)
                    }
                }
            }
        }
    }


    private suspend fun openShamelaFolder(onOpened: suspend (categories: List<File>?) -> Unit) {
        withContext(Dispatchers.IO) {
            try {
                if (shamelaBooks.exists() && shamelaBooks.isDirectory) {
                    val categories = shamelaBooks.listFiles(FileFilter { it.isDirectory })?.toList()
                    onOpened(categories)
                } else {
                    Log.w(TAG, "getCategories: shamelaBooks doesn't exist")
                }
            } catch (e: IOException) {
                Log.e(TAG, "getCategories: IOException [${e.message}]")
            }
        }
    }

    suspend fun deleteBookFile(book: Book): Boolean {
        val baseFolder = shamelaBooks
        val bookFilePath = "${book.categoryName}/${book.title}.epub"
        val fileToBeDeleted = File(baseFolder, bookFilePath)
        Log.d(TAG, "deleteBookFile: ${fileToBeDeleted.absoluteFile}")

        return withContext(Dispatchers.IO) {
            val delete = fileToBeDeleted.delete() or pdfFileOf(book).delete()
            // The last book of a section: remove its folder too.
            fileToBeDeleted.parentFile?.takeIf { it.list()?.isEmpty() == true }?.delete()
            if (delete) {
                Log.d(TAG, "deleteBookFile: Book Deleted Successfully")
            } else {
                Log.e(TAG, "deleteBookFile: Book Deletion Failed")
            }
            delete
        }
    }

    override suspend fun getDownloadLink(categoryName: String, bookName: String): Uri? {
        return null
    }

    suspend fun parseBook(file: File, categoryName: String): Book? {
        Log.d("Shamela", "parseBook: parsing Book: ${file.name} at $categoryName")
        if (isPdf(file)) return parsePdf(file, categoryName)

        return withContext(Dispatchers.IO) {
            FolioReader.get().parseEpub(file)?.let { (authorName, pageCount) ->
                val bookTitle = file.name.removeSuffix(".epub")
                val uuidName = bookTitle + categoryName
                Book(
                    id = UUID.nameUUIDFromBytes(uuidName.toByteArray()).toString(),
                    title = bookTitle,
                    author = authorName,
                    pageCount = pageCount,
                    categoryName = categoryName
                )
            }
        }
    }

    /** A scanned book: its page count; the author is whatever the library database has for it. */
    suspend fun parsePdf(file: File, categoryName: String, author: String = "-"): Book? = withContext(Dispatchers.IO) {
        val pages = try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { it.pageCount }
            }
        } catch (e: Exception) {
            Log.w(TAG, "parsePdf: ${file.name} is not a readable PDF: ${e.message}")
            return@withContext null
        }
        val title = titleOf(file)
        Book(
            id = UUID.nameUUIDFromBytes((title + categoryName).toByteArray()).toString(),
            title = title,
            author = author,
            pageCount = pages,
            categoryName = categoryName,
        )
    }

    /** The scanned (PDF) books of the library, which the full-text index leaves out. */
    fun libraryPdfFiles(): List<File> =
        shamelaBooks.walkTopDown().maxDepth(2).filter { it.isFile && isPdf(it) }.toList()
}
