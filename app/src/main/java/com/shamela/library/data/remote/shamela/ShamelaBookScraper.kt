package com.shamela.library.data.remote.shamela

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.File

/** One entry of a book's table of contents; [page] is the site's page index (1-based). */
data class TocEntry(val text: String, val page: Int, val children: List<TocEntry> = emptyList())

/** What the book's index page tells about it. */
data class ShamelaBookMeta(
    val id: Int,
    /** The book card (inner HTML of the index page, without its search box and table of contents). */
    val aboutHtml: String,
    val toc: List<TocEntry>,
    /** Chapter titles starting on each page index, to turn them into headings. */
    val pageChapters: Map<Int, List<String>>,
    val totalPages: Int,
)

/** One page of a book: [index] is the site's page index, [printed] the page number of the print. */
data class ShamelaPage(val index: Int, val printed: String, val part: String, val html: String)

/**
 * Reads a book from shamela.ws: its index page (`/book/<id>`), then every page (`/book/<id>/<n>`).
 * A Kotlin port of the scraper of yshalsager/shamela-epub-exporter (GPL-3.0).
 *
 * Pages are kept in [cacheDir] as they arrive, so a retried or resumed download only fetches the
 * pages it is missing.
 */
class ShamelaBookScraper(private val cacheDir: File) {

    suspend fun readMeta(bookId: Int): ShamelaBookMeta {
        val base = "${ShamelaHttp.BASE}/book/$bookId"
        val index = Jsoup.parse(ShamelaHttp.get(base), base)
        val first = Jsoup.parse(ShamelaHttp.get("$base/1"), "$base/1")
        return parseMeta(bookId, index, first)
    }

    /** All pages, from the cache or the site; [onProgress] gets (pages ready, total). */
    suspend fun readPages(
        bookId: Int,
        totalPages: Int,
        onProgress: suspend (Int, Int) -> Unit,
    ): List<ShamelaPage> = coroutineScope {
        val dir = File(cacheDir, bookId.toString()).apply { mkdirs() }
        val pages = arrayOfNulls<ShamelaPage>(totalPages + 1)
        for (n in 1..totalPages) pages[n] = readCached(dir, n)
        var ready = pages.count { it != null }
        onProgress(ready, totalPages)

        val missing = (1..totalPages).filter { pages[it] == null }
        // A few pages at a time, like the exporter's batches: fast enough, gentle on the site.
        for (batch in missing.chunked(BATCH)) {
            ensureActive()
            batch.map { n ->
                async(Dispatchers.IO) {
                    val url = "${ShamelaHttp.BASE}/book/$bookId/$n"
                    val page = parsePage(n, Jsoup.parse(ShamelaHttp.get(url), url))
                    writeCached(dir, page)
                    page
                }
            }.awaitAll().forEach { pages[it.index] = it }
            ready += batch.size
            onProgress(ready, totalPages)
        }
        (1..totalPages).mapNotNull { pages[it] }
    }

    fun clearCache(bookId: Int) {
        File(cacheDir, bookId.toString()).deleteRecursively()
    }

    // Cache: line 1 = printed page number, line 2 = part (volume), the rest = the page's HTML.
    private suspend fun readCached(dir: File, n: Int): ShamelaPage? = withContext(Dispatchers.IO) {
        val file = File(dir, "$n.html")
        if (!file.isFile) return@withContext null
        val text = file.readText()
        val first = text.indexOf('\n')
        val second = text.indexOf('\n', first + 1)
        if (first < 0 || second < 0) null
        else ShamelaPage(n, text.substring(0, first), text.substring(first + 1, second), text.substring(second + 1))
    }

    private fun writeCached(dir: File, page: ShamelaPage) {
        val tmp = File(dir, "${page.index}.tmp")
        tmp.writeText("${page.printed.replace('\n', ' ')}\n${page.part.replace('\n', ' ')}\n${page.html}")
        tmp.renameTo(File(dir, "${page.index}.html"))
    }

    companion object {
        private const val BATCH = 3

        internal fun parseMeta(bookId: Int, index: Document, first: Document): ShamelaBookMeta {
            val content = index.selectFirst(".nass") ?: throw ShamelaNotFoundException("book $bookId")
            val tocBoxes = content.select("div.betaka-index")
            tocBoxes.forEach { box -> box.select("a[href^=javascript], a[href=#]").remove() }

            var tocItems: List<Element> = tocBoxes.flatMap { it.select("h4 + ul > li") }
            var tocSource: Element? = tocBoxes.firstOrNull()
            if (tocItems.isEmpty()) {
                first.selectFirst(".s-nav")?.let { nav ->
                    nav.select("a[href^=javascript], a[href=#]").remove()
                    tocItems = nav.select("ul > li").filter { li -> li.parents().none { it.normalName() == "li" } }
                    tocSource = nav
                }
            }
            val toc = parseToc(tocItems, mutableSetOf())

            val chapterAnchors = tocSource?.select("ul a[href*=/book/]")?.takeIf { it.isNotEmpty() }
                ?: content.select("ul a[href*=/book/]")
            val pageChapters = mutableMapOf<Int, MutableList<String>>()
            chapterAnchors.forEach { a ->
                val page = pageOf(a.attr("href"))
                val text = a.text().trim()
                if (page > 0 && text.isNotEmpty()) pageChapters.getOrPut(page) { mutableListOf() } += text
            }

            val about = content.clone()
            about.select("div.text-left, div.betaka-index").remove()

            val lastLink = first.selectFirst("input#fld_goto_bottom + a + a")?.attr("href").orEmpty()
            val total = pageOf(lastLink).takeIf { it > 0 } ?: 1

            return ShamelaBookMeta(bookId, about.html(), toc, pageChapters, total)
        }

        private fun parseToc(items: List<Element>, seen: MutableSet<String>): List<TocEntry> =
            items.mapNotNull { li ->
                val link = li.children().firstOrNull { it.normalName() == "a" } ?: li.selectFirst("a")
                val href = link?.attr("href").orEmpty()
                if (href.isEmpty() || href.startsWith("javascript") || href.startsWith("#")) return@mapNotNull null
                val entry = TocEntry(link!!.text().trim(), pageOf(href))
                if (!seen.add("${entry.page}:${entry.text}")) return@mapNotNull null
                val subList = li.children().firstOrNull { it.normalName() == "ul" }
                val children = subList?.children()?.filter { it.normalName() == "li" }.orEmpty()
                if (children.isEmpty()) entry else entry.copy(children = parseToc(children, seen))
            }

        internal fun parsePage(index: Int, doc: Document): ShamelaPage {
            val content = doc.selectFirst(".nass") ?: return ShamelaPage(index, index.toString(), "", "")
            content.select("a.btn_tag").remove()
            content.select("span").forEach { if (it.text().isBlank() && it.childrenSize() == 0) it.remove() }
            content.select("p[style]").forEach { it.removeAttr("style") }
            val printed = doc.selectFirst("input#fld_goto_bottom")?.attr("value")?.trim()
                ?.takeIf { it.isNotEmpty() } ?: index.toString()
            val part = doc.selectFirst("input#fld_part_top")?.attr("value")?.trim().orEmpty()
            return ShamelaPage(index, printed, part, content.html())
        }

        /** The page index at the end of a book link (`…/book/12836/14#p1` → 14). */
        internal fun pageOf(href: String): Int =
            pageLink.find(href)?.groupValues?.get(1)?.toIntOrNull() ?: 0

        private val pageLink = Regex("""/book/\d+/(\d+)""")
    }
}
