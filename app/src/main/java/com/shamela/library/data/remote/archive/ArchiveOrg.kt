package com.shamela.library.data.remote.archive

import android.net.Uri
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.shamela.library.ShamelaApp
import com.shamela.library.data.remote.shamela.ShamelaCatalog
import com.shamela.library.domain.model.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** A scanned book (an archive.org "texts" item): one or more volumes, each a PDF. */
data class ArchiveItem(
    val id: String,
    val title: String,
    val creator: String,
    val year: String,
    val downloads: Int,
)

data class ArchiveVolume(
    /** 1-based, in file name order. */
    val number: Int,
    val fileName: String,
    val url: String,
    val size: Long,
)

/**
 * Kütüphane 4: scanned books searched on archive.org (https://archive.org/advancedsearch.php and
 * https://archive.org/metadata/<id>). Each volume is saved as a PDF among the scanned books.
 *
 * archive.org usually keeps two PDFs of a scan: the original, and a smaller `_text.pdf` made from
 * it (a quarter of the size, as legible); the smaller one is downloaded when there is one.
 */
object ArchiveOrg {
    private const val BASE = "https://archive.org"

    /** Titles past this many characters are cut, to keep the file name short enough. */
    private const val MAX_TITLE = 90

    suspend fun search(query: String): List<ArchiveItem> {
        val q = "(${query.trim()}) AND mediatype:texts"
        val url = "$BASE/advancedsearch.php?q=${Uri.encode(q)}" +
            listOf("identifier", "title", "creator", "date", "downloads").joinToString("") { "&fl[]=$it" } +
            "&sort[]=downloads+desc&rows=60&output=json"
        val docs = getJson(url).asJsonObject.getAsJsonObject("response")?.getAsJsonArray("docs") ?: return emptyList()
        return docs.mapNotNull { element ->
            val doc = element.asJsonObject
            val id = doc.text("identifier") ?: return@mapNotNull null
            ArchiveItem(
                id = id,
                title = doc.text("title") ?: id,
                creator = doc.text("creator").orEmpty(),
                year = doc.text("date")?.take(4).orEmpty(),
                downloads = doc.get("downloads")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0,
            )
        }
    }

    /** The item's PDF volumes, or none when it has no PDF anyone may download. */
    suspend fun volumes(id: String): List<ArchiveVolume> {
        val files = getJson("$BASE/metadata/${Uri.encode(id)}").asJsonObject.getAsJsonArray("files") ?: return emptyList()
        val pdfs = files.map { it.asJsonObject }
            .filter { file ->
                val name = file.text("name").orEmpty()
                name.endsWith(".pdf", ignoreCase = true) && file.text("private") != "true"
            }
            .associate { it.text("name")!! to (it.text("size")?.toLongOrNull() ?: 0L) }
        // One PDF per scan: the smaller "_text.pdf" when the item has it next to the original.
        val chosen = pdfs.keys.groupBy { it.removeSuffix("_text.pdf").removeSuffix(".pdf") }
            .toSortedMap(naturalOrder)
            .values.map { names -> names.firstOrNull { it.endsWith("_text.pdf") } ?: names.first() }
        return chosen.mapIndexed { i, name ->
            ArchiveVolume(i + 1, name, "$BASE/download/${Uri.encode(id)}/${Uri.encode(name, "/")}", pdfs.getValue(name))
        }
    }

    /** Whether [url] is a volume link made by [volumes]. */
    fun isVolumeLink(url: String): Boolean = url.startsWith("$BASE/download/") && url.endsWith(".pdf", ignoreCase = true)

    /** The library book a volume is saved as: "<title>" or "<title> - ج 2", among the scanned books. */
    fun bookOf(item: ArchiveItem, volume: ArchiveVolume, volumeCount: Int): Book {
        val base = ShamelaCatalog.safeTitle(item.title).take(MAX_TITLE).trim().ifEmpty { item.id }
        val title = if (volumeCount > 1) "$base - ج ${volume.number}" else base
        val category = ShamelaApp.SCANNED_BOOKS_CATEGORY
        return Book(
            id = UUID.nameUUIDFromBytes((title + category).toByteArray()).toString(),
            title = title,
            author = item.creator,
            pageCount = 0,
            categoryName = category,
        )
    }

    /** "vol 2" before "vol 10". */
    private val naturalOrder = Comparator<String> { a, b ->
        val chunks = Regex("""\d+|\D+""")
        val x = chunks.findAll(a).map { it.value }.toList()
        val y = chunks.findAll(b).map { it.value }.toList()
        for (i in 0 until minOf(x.size, y.size)) {
            val c = if (x[i][0].isDigit() && y[i][0].isDigit()) x[i].toBigInteger().compareTo(y[i].toBigInteger())
            else x[i].compareTo(y[i])
            if (c != 0) return@Comparator c
        }
        x.size - y.size
    }

    /** A field that may be a string, a number or a list of strings ("creator" often is). */
    private fun JsonObject.text(name: String): String? {
        val value: JsonElement = get(name) ?: return null
        return when {
            value.isJsonArray -> value.asJsonArray.joinToString("، ") { it.asString }
            value.isJsonPrimitive -> value.asString
            else -> null
        }?.trim()?.takeIf { it.isNotEmpty() }
    }

    private suspend fun getJson(url: String): JsonElement = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${connection.responseCode}")
            connection.inputStream.reader().use { JsonParser.parseReader(it) }
        } finally {
            connection.disconnect()
        }
    }
}
