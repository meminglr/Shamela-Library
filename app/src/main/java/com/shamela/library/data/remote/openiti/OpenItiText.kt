package com.shamela.library.data.remote.openiti

import com.shamela.library.data.remote.shamela.ShamelaEpubBuilder
import com.shamela.library.data.remote.shamela.ShamelaPage
import com.shamela.library.data.remote.shamela.TocEntry

/** A book read from an OpenITI text: its pages as HTML, its headings and its header fields. */
class OpenItiBook(
    val pages: List<ShamelaPage>,
    val toc: List<TocEntry>,
    /** "#META# key: value" lines of the header. */
    val header: Map<String, String>,
)

/**
 * Reads OpenITI mARkdown (https://alraqmiyyat.github.io/mARkdown/):
 *
 * - the header (`#META# key: value` lines) ends at `#META#Header#End#`;
 * - `# ` starts a paragraph and `~~` continues it on the next line;
 * - `### |` … `### |||||` are headings of levels 1–5 (`### $` a biography, `### |EDITOR|` the
 *   editor's part, whose marker is dropped);
 * - `PageV01P005` closes page 5 of volume 1, wherever it stands in a line;
 * - `%~%` separates the hemistichs of a verse (texts from Jamiʿ al-Kutub write `% … % % … % 2`
 *   instead, with the next verse's number); `ms12` (milestones) and `@QB@`-style tags are dropped.
 *
 * A text without page markers is cut into pages of about [WORDS_PER_PAGE] words.
 */
object OpenItiText {
    private const val WORDS_PER_PAGE = 350

    private val pageMarker = Regex("""PageV(\d+)P(\d+)""")
    private val milestone = Regex("""\bms\d+\b""")
    private val tag = Regex("""@[A-Z]{2,}\d*@?""")
    private val spaces = Regex("""\s+""")
    private val verseMarks = Regex("""\s*%+\s*""")
    private val verseNumber = Regex("""^\d+$""")
    private val headingLevel = Regex("""^###\s*(\|+)""")

    private class Builder {
        val pages = mutableListOf<ShamelaPage>()
        val headings = mutableListOf<Triple<Int, String, Int>>()   // level, text, page index
        private val html = StringBuilder()
        private val paragraph = StringBuilder()
        private var paragraphOpen = false
        private var words = 0
        var sawMarker = false
        private var lastVolume = ""
        private var lastPage = 0

        fun startParagraph(text: String) {
            closeParagraph()
            paragraphOpen = true
            append(text)
        }

        fun continueParagraph(text: String) {
            if (!paragraphOpen) paragraphOpen = true
            append(text)
        }

        /** Text with page markers in it: each marker closes the page at its place. */
        private fun append(text: String) {
            var from = 0
            for (match in pageMarker.findAll(text)) {
                addWords(text.substring(from, match.range.first))
                closePage(match.groupValues[1].trimStart('0'), match.groupValues[2].trimStart('0').ifEmpty { "0" })
                from = match.range.last + 1
            }
            addWords(text.substring(from))
        }

        private fun addWords(raw: String) {
            val text = clean(raw)
            if (text.isEmpty()) return
            if (paragraph.isNotEmpty()) paragraph.append(' ')
            paragraph.append(text)
        }

        fun heading(level: Int, text: String) {
            closeParagraph()
            val title = clean(text)
            if (title.isEmpty()) return
            val tagLevel = (level + 1).coerceIn(2, 6)
            html.append("<h$tagLevel>").append(ShamelaEpubBuilder.escape(title)).append("</h$tagLevel>\n")
            headings += Triple(level, title, pages.size + 1)
        }

        fun closeParagraph(mayEndPage: Boolean = true) {
            if (paragraph.isNotEmpty()) {
                html.append("<p>").append(ShamelaEpubBuilder.escape(paragraph.toString())).append("</p>\n")
                words += paragraph.count { it == ' ' } + 1
                paragraph.clear()
            }
            paragraphOpen = false
            // Without page markers, a page ends at a paragraph once it is long enough.
            if (mayEndPage && !sawMarker && words >= WORDS_PER_PAGE) closePage("", "")
        }

        /** Ends the current page; a paragraph running over the marker goes on on the next page. */
        fun closePage(volume: String, page: String) {
            if (page.isNotEmpty()) sawMarker = true
            val open = paragraphOpen
            closeParagraph(mayEndPage = false)
            paragraphOpen = open
            if (html.isBlank()) return
            val index = pages.size + 1
            val printed = page.ifEmpty { index.toString() }
            pages += ShamelaPage(index, printed, volume, html.toString())
            html.clear()
            words = 0
            lastVolume = volume
            lastPage = printed.toIntOrNull() ?: lastPage
        }

        fun finish() {
            closeParagraph()
            // Text after the last marker (a colophon, the editor's notes): the page after it.
            if (sawMarker) closePage(lastVolume, (lastPage + 1).toString()) else closePage("", "")
        }
    }

    fun parse(lines: Sequence<String>): OpenItiBook {
        val header = LinkedHashMap<String, String>()
        val book = Builder()
        var inHeader = true
        for (raw in lines) {
            val line = raw.removePrefix("﻿").trimEnd()
            if (inHeader) {
                if (line.startsWith("#META#Header#End")) {
                    inHeader = false
                    continue
                }
                if (line.startsWith("#META#")) {
                    val field = line.removePrefix("#META#").trim()
                    val colon = field.indexOf(':')
                    if (colon > 0) header.putIfAbsent(field.substring(0, colon).trim(), field.substring(colon + 1).trim())
                    continue
                }
                if (!line.startsWith("# ") && !line.startsWith("### ")) continue
                inHeader = false   // a text without the end-of-header line
            }
            when {
                line.isBlank() -> book.closeParagraph()
                line.startsWith("###") -> {
                    val bars = headingLevel.find(line)
                    when {
                        line.contains("|EDITOR|") -> book.closeParagraph()
                        bars != null -> book.heading(bars.groupValues[1].length, line.substring(bars.range.last + 1).headingText())
                        line.removePrefix("###").trimStart().startsWith("$") ->
                            book.heading(3, line.removePrefix("###").trim().trimStart('$').headingText())
                        else -> book.heading(1, line.removePrefix("###").headingText())
                    }
                }
                line.startsWith("~~") -> book.continueParagraph(line.removePrefix("~~"))
                line.startsWith("# ") -> book.startParagraph(line.removePrefix("# "))
                line.startsWith("#") -> book.startParagraph(line.trimStart('#').substringAfter(' ', ""))
                else -> book.startParagraph(line)
            }
        }
        book.finish()
        return OpenItiBook(book.pages, toc(book.headings), header)
    }

    /** "AUTO ((باب …))" → "باب …"; a biography's "BIO_MAN" marker and such go too. */
    private fun String.headingText(): String =
        replace("AUTO", "").replace("((", "").replace("))", "").replace(Regex("""\b[A-Z_]{3,}\b"""), "")
            .trim().trimStart('$', '|').trim()

    private fun clean(text: String): String {
        val plain = text.replace(milestone, " ").replace(tag, " ").replace("%~%", "%")
        if ('%' !in plain) return plain.replace(spaces, " ").trim()
        return plain.split(verseMarks)
            .map { it.replace(spaces, " ").trim() }
            .filter { it.isNotEmpty() && !verseNumber.matches(it) }
            .joinToString(" ... ")
    }

    /** Nests flat (level, text, page) headings into a table of contents. */
    private fun toc(headings: List<Triple<Int, String, Int>>): List<TocEntry> {
        class Node(val level: Int, val text: String, val page: Int, val children: MutableList<Node> = mutableListOf())
        val roots = mutableListOf<Node>()
        val stack = ArrayDeque<Node>()
        for ((level, text, page) in headings) {
            val node = Node(level, text, page)
            while (stack.isNotEmpty() && stack.last().level >= level) stack.removeLast()
            (stack.lastOrNull()?.children ?: roots) += node
            stack.addLast(node)
        }
        fun Node.entry(): TocEntry = TocEntry(text, page, children.map { it.entry() })
        return roots.map { it.entry() }
    }
}
