package com.shamela.library.data.remote.openiti

import com.shamela.library.data.local.files.StorageDirs
import com.shamela.library.data.remote.shamela.ShamelaBookMeta
import com.shamela.library.data.remote.shamela.ShamelaEpubBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/** The text isn't (any longer) where the catalog says. */
class OpenItiNotFoundException(url: String) : IOException("not found: $url")

/**
 * Builds the EPUB of a Kütüphane 3 book into [output]: downloads its OpenITI text (resuming a
 * partial download), reads it with [OpenItiText] and writes it with [ShamelaEpubBuilder]. Throws
 * [OpenItiNotFoundException] or another IOException on failure; the partial text is kept then, so
 * the next attempt goes on from where this one stopped.
 */
class OpenItiBookBuilder(cacheDir: File) {
    private val textDir = File(cacheDir, "openiti-texts")

    suspend fun build(
        entry: OpenItiEntry,
        output: File,
        onProgress: suspend (done: Long, total: Long) -> Unit,
    ) {
        val text = download(entry, onProgress)
        withContext(Dispatchers.IO) {
            val book = text.bufferedReader().useLines { OpenItiText.parse(it) }
            if (book.pages.isEmpty()) throw IOException("empty text: ${entry.id}")
            val meta = ShamelaBookMeta(
                id = 0,
                aboutHtml = aboutHtml(entry, book.header),
                toc = book.toc,
                pageChapters = emptyMap(),
                totalPages = book.pages.size,
            )
            output.parentFile?.let { StorageDirs.ensure(it) }
            val tmp = File(output.path + ".building")
            ShamelaEpubBuilder.build(
                tmp, 0, entry.title, entry.author, meta, book.pages,
                identifier = "urn:openiti:${entry.id}",
                source = entry.url,
                publisher = "OpenITI (openiti.org)",
            )
            if (output.exists()) output.delete()
            if (!tmp.renameTo(output)) {
                tmp.copyTo(output, overwrite = true)
                tmp.delete()
            }
        }
        text.delete()
    }

    fun discard(entry: OpenItiEntry) {
        textFile(entry).delete()
        File(textFile(entry).path + ".part").delete()
    }

    private fun textFile(entry: OpenItiEntry) = File(textDir, entry.id.replace(Regex("""[^\w.-]"""), "_") + ".txt")

    private suspend fun download(entry: OpenItiEntry, onProgress: suspend (Long, Long) -> Unit): File =
        withContext(Dispatchers.IO) {
            val done = textFile(entry)
            if (done.isFile) return@withContext done
            textDir.mkdirs()
            val partial = File(done.path + ".part")
            var existing = if (partial.isFile) partial.length() else 0L
            val connection = URL(entry.url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.setRequestProperty("Accept-Encoding", "identity")
                if (existing > 0) connection.setRequestProperty("Range", "bytes=$existing-")
                val total = when (val code = connection.responseCode) {
                    HttpURLConnection.HTTP_NOT_FOUND, HttpURLConnection.HTTP_GONE ->
                        throw OpenItiNotFoundException(entry.url)
                    HttpURLConnection.HTTP_PARTIAL ->
                        connection.getHeaderField("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: -1L
                    HttpURLConnection.HTTP_OK -> {
                        existing = 0L   // the server ignored the range: start over
                        connection.contentLengthLong
                    }
                    416 -> {
                        // Already complete.
                        partial.renameTo(done)
                        return@withContext done
                    }
                    else -> throw IOException("HTTP $code for ${entry.url}")
                }
                var bytes = existing
                var lastReport = 0L
                connection.inputStream.use { input ->
                    FileOutputStream(partial, existing > 0).use { out ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                            bytes += read
                            val now = System.currentTimeMillis()
                            if (now - lastReport >= 300) {
                                lastReport = now
                                onProgress(bytes, total)
                            }
                        }
                    }
                }
                if (total > 0 && bytes < total) throw IOException("connection ended at $bytes of $total")
                onProgress(bytes, bytes)
                if (!partial.renameTo(done)) throw IOException("can't keep ${done.name}")
                done
            } finally {
                connection.disconnect()
            }
        }

    private fun aboutHtml(entry: OpenItiEntry, header: Map<String, String>): String {
        val lines = OpenItiBooksRepoImpl.aboutOf(entry).map { it.label to it.value }.toMutableList()
        // The fields of the Shamela card that the text's header may carry (texts taken from Shamela).
        for (label in listOf("المحقق", "الناشر", "الطبعة", "عدد الأجزاء")) {
            val value = header[label]?.takeIf { it.isNotBlank() } ?: continue
            if (lines.none { it.first == label && it.second == value }) lines.add(lines.size - 1, label to value)
        }
        return lines.joinToString("") { (label, value) ->
            "<p><span class=\"title\">${ShamelaEpubBuilder.escape(label)}:</span> ${ShamelaEpubBuilder.escape(value)}</p>"
        } + "<p>${ShamelaEpubBuilder.escape("نص مفتوح من مشروع OpenITI (openiti.org)، يُبنى كتابًا على الجهاز.")}</p>"
    }
}
