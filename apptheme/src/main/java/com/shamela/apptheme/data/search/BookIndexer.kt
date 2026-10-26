package com.shamela.apptheme.data.search

import android.content.Context
import android.util.Log
import com.shamela.apptheme.data.db.DatabaseHelper
import com.shamela.apptheme.data.util.ArabicNormalizer
import com.shamela.apptheme.domain.model.BookPage
import org.jsoup.Jsoup
import org.jsoup.parser.Parser.xmlParser
import com.shamela.apptheme.data.util.parseEpubAndClose
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile

/**
 * Builds the full-text-search index of one book: reads its chapters, normalizes the Arabic text
 * and stores it in [DatabaseHelper]. Callers run books one at a time; the work is CPU- and
 * memory-heavy, and many books at once would starve the UI and the database.
 */
object BookIndexer {
    private const val TAG = "BookIndexer"

    /** The search index's id for the book at [file] (`<category>/<title>.epub`). */
    fun bookIdOf(file: File): String {
        val title = file.name.removeSuffix(".epub")
        val category = file.parentFile?.name ?: "unknown"
        return UUID.nameUUIDFromBytes((title + category).toByteArray()).toString()
    }

    /**
     * Indexes the book at [path]; returns false when the file is missing or unreadable. Reads the
     * book on the caller's thread, so a background queue can run it at low priority.
     */
    suspend fun index(context: Context, path: String): Boolean {
        val file = File(path)
        if (!file.isFile) return false
        val title = file.name.removeSuffix(".epub")
        val category = file.parentFile?.name ?: "unknown"
        val bookId = bookIdOf(file)
        val pages = readPages(file).map { (href, content) ->
            BookPage(href = href, content = content, bookId = bookId, category = category, bookTitle = title)
        }
        if (pages.isEmpty()) return false
        val database = DatabaseHelper.getInstance(context)
        database.insertBookPages(pages)
        // Deleted while we were reading it: don't leave orphan pages in search results.
        if (!file.isFile) database.deleteBookPages(bookId)
        return true
    }

    private fun readPages(file: File): Map<String, String> {
        val hrefs = try {
            parseEpubAndClose(file.path) { box -> box?.publication?.readingOrder?.mapNotNull { it.href }.orEmpty() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse EPUB ${file.name}: ${e.message}")
            return emptyMap()
        }
        val normalizer = ArabicNormalizer()
        val pages = LinkedHashMap<String, String>()
        try {
            ZipFile(file).use { zip ->
                hrefs.forEach { href ->
                    val entry = zip.getEntry(href.removePrefix("/")) ?: return@forEach
                    if (!entry.name.endsWith(".xhtml")) return@forEach
                    zip.getInputStream(entry).use { input ->
                        val document = Jsoup.parse(input, "UTF-8", "", xmlParser())
                        pages[href] = normalizer.normalize(document.text())
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading ${file.name}: ${e.message}")
        }
        return pages
    }
}
