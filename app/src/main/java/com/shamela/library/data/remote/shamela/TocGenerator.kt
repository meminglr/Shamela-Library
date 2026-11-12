package com.shamela.library.data.remote.shamela

import org.jsoup.Jsoup

/** A page as the generator needs it: [html] is the page body, [printed] the number of the print. */
data class TocSourcePage(val index: Int, val printed: String, val part: String, val html: String)

/**
 * Makes a table of contents for a book whose source has none (or hardly any): first from headings
 * found in the text (`سورة …`, `كتاب …`, `باب …`, `فصل …`, …), and when the text has too few of them,
 * from volumes and page ranges, which at least beat a flat list of every page.
 */
object TocGenerator {
    /** Books whose own index has fewer entries than this count as having none. */
    const val MIN_SITE_ENTRIES = 3

    private const val MAX_HEADING_WORDS = 9
    private const val MAX_HEADING_CHARS = 70
    private const val RANGE_PAGES = 25

    /** First word of a heading → its level (1 = top). */
    private val levels = mapOf(
        "كتاب" to 1, "قسم" to 1, "جزء" to 1, "سورة" to 1, "مقدمة" to 1, "خاتمة" to 1,
        "باب" to 2,
        "فصل" to 3, "مبحث" to 3, "مطلب" to 3, "تتمة" to 3,
    )
    private val brackets = Regex("""^[\[\(«{\s]+|[\]\)»}\s]+$""")
    private val spaces = Regex("""\s+""")
    private val marks = Regex("""[ً-ٰٟـ]""")

    fun flatSize(toc: List<TocEntry>): Int = toc.sumOf { 1 + flatSize(it.children) }

    fun generate(pages: List<TocSourcePage>): List<TocEntry> {
        val sorted = pages.sortedBy { it.index }
        if (sorted.isEmpty()) return emptyList()
        val found = headings(sorted)
        val enough = found.size >= 3 && found.size * 60 >= sorted.size
        return if (enough) nest(found) else ranges(sorted)
    }

    private class Heading(val level: Int, val text: String, val page: Int)

    private fun headings(pages: List<TocSourcePage>): List<Heading> {
        val all = ArrayList<Heading>()
        for (page in pages) {
            val body = Jsoup.parseBodyFragment(page.html).body()
            body.select(".hamesh, .page-footer, script, style").remove()
            val lines = body.select("p").map { it.text() }.ifEmpty { body.wholeText().lines() }
            for (line in lines) heading(line)?.let { all += Heading(it.first, it.second, page.index) }
        }
        // A line repeated on many pages is a running header, not a chapter.
        val repeats = all.groupingBy { it.text }.eachCount()
        val limit = maxOf(5, pages.size / 20)
        return all.filter { repeats.getValue(it.text) <= limit }
    }

    /** (level, text) when [line] is a short, stand-alone heading line. */
    internal fun heading(line: String): Pair<Int, String>? {
        val text = line.replace(brackets, "").replace(spaces, " ").trim()
        if (text.isEmpty() || text.length > MAX_HEADING_CHARS) return null
        val words = text.split(' ')
        if (words.size > MAX_HEADING_WORDS) return null
        val first = words.first().trimEnd(':', '،', '.').replace(marks, "").removePrefix("ال")
        val level = levels[first] ?: return null
        // "باب" alone or followed by a name is a heading; a sentence ending in a full stop is not.
        if (text.endsWith(".") && words.size > 3) return null
        return level to text.trimEnd(':', '.', ' ')
    }

    private class Node(val level: Int, val text: String, val page: Int, val children: MutableList<Node> = mutableListOf())

    private fun nest(headings: List<Heading>): List<TocEntry> {
        val roots = mutableListOf<Node>()
        val stack = ArrayDeque<Node>()
        for (h in headings) {
            val node = Node(h.level, h.text, h.page)
            while (stack.isNotEmpty() && stack.last().level >= h.level) stack.removeLast()
            (stack.lastOrNull()?.children ?: roots) += node
            stack.addLast(node)
        }
        fun Node.entry(): TocEntry = TocEntry(text, page, children.map { it.entry() })
        return roots.map { it.entry() }
    }

    /** One entry per volume (when there are several), each split into ranges of [RANGE_PAGES] pages. */
    private fun ranges(pages: List<TocSourcePage>): List<TocEntry> {
        fun rangesOf(group: List<TocSourcePage>): List<TocEntry> = group.chunked(RANGE_PAGES).map { chunk ->
            val from = chunk.first().printed
            val to = chunk.last().printed
            TocEntry(if (from == to) "صفحة $from" else "صفحة $from – $to", chunk.first().index)
        }
        val parts = pages.map { it.part }.filter { it.isNotEmpty() }.distinct()
        if (parts.size < 2) return rangesOf(pages)
        return pages.groupBy { it.part }.map { (part, group) ->
            val children = rangesOf(group)
            TocEntry(if (part.isEmpty()) "—" else "الجزء $part", group.first().index, if (children.size > 1) children else emptyList())
        }
    }
}
