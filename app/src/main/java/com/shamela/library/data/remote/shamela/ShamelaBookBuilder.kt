package com.shamela.library.data.remote.shamela

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Builds the EPUB of a shamela.ws book into [output]: reads it with [ShamelaBookScraper] (resuming
 * from its page cache), then writes it with [ShamelaEpubBuilder]. Throws [ShamelaChallengeException],
 * [ShamelaNotFoundException] or another IOException on failure; the page cache is kept then, so the
 * next attempt goes on from where this one stopped.
 */
class ShamelaBookBuilder(cacheDir: File) {
    private val scraper = ShamelaBookScraper(File(cacheDir, "shamela-pages"))

    suspend fun build(
        bookId: Int,
        title: String,
        author: String,
        output: File,
        onProgress: suspend (done: Int, total: Int) -> Unit,
    ) {
        val meta = scraper.readMeta(bookId)
        val pages = scraper.readPages(bookId, meta.totalPages, onProgress)
        withContext(Dispatchers.IO) {
            output.parentFile?.let { com.shamela.library.data.local.files.StorageDirs.ensure(it) }
            val tmp = File(output.path + ".building")
            ShamelaEpubBuilder.build(tmp, bookId, title, author, meta, pages)
            if (output.exists()) output.delete()
            if (!tmp.renameTo(output)) {
                tmp.copyTo(output, overwrite = true)
                tmp.delete()
            }
        }
        scraper.clearCache(bookId)
    }

    fun discard(bookId: Int) = scraper.clearCache(bookId)
}
