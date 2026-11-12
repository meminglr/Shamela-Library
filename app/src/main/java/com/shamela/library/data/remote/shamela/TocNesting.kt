package com.shamela.library.data.remote.shamela

/**
 * shamela.ws serves a book's contents as one flat list, while the desktop Shamela (and so
 * Kütüphane 1) keeps it nested. This rebuilds the nesting from the entry titles: a `باب` goes
 * under the `كتاب` before it, a `فصل` under the `باب`, a `مبحث` under the `فصل`, and so on; the
 * front and back matter (`مقدمة`، `تمهيد`، `خاتمة` …) stays at the top. Entries without such a word
 * become children of the last structural entry. A list that is already nested, or has no
 * structural words at all, comes back unchanged.
 */
object TocNesting {
    private val ranks = mapOf(
        "كتاب" to 1, "قسم" to 1, "جزء" to 1, "سورة" to 1, "مجلد" to 1,
        "باب" to 2,
        "فصل" to 3,
        "مبحث" to 4,
        "مطلب" to 5,
        "فرع" to 6, "مسالة" to 6,
    )
    private val topLevel = setOf(
        "مقدمة", "تمهيد", "تقديم", "خاتمة", "فهرس", "فهارس", "مراجع", "مصادر", "ملحق", "ملاحق", "تصدير", "مدخل",
    )
    private val lead = Regex("""^[\s\[\(«{\d٠-٩.\-–—:*•)\]»}]+""")
    private val marks = Regex("""[ً-ٰٟـ]""")

    private sealed interface Kind {
        data class Ranked(val rank: Int) : Kind
        data object Top : Kind
        data object Plain : Kind
    }

    private fun kindOf(title: String): Kind {
        val cleaned = title.replace(marks, "").replace(lead, "")
        val first = cleaned.split(' ', ':', '،').firstOrNull().orEmpty()
            .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ى', 'ي')
            .removePrefix("ال")
        ranks[first]?.let { return Kind.Ranked(it) }
        if (first in topLevel) return Kind.Top
        return Kind.Plain
    }

    private class Node(val entry: TocEntry, val rank: Int, val children: MutableList<Node> = mutableListOf())

    fun nest(toc: List<TocEntry>): List<TocEntry> {
        if (toc.size < 3 || toc.any { it.children.isNotEmpty() }) return toc
        val kinds = toc.map { kindOf(it.text) }
        if (kinds.none { it is Kind.Ranked }) return toc

        val roots = mutableListOf<Node>()
        val stack = ArrayDeque<Node>()
        for ((entry, kind) in toc.zip(kinds)) {
            when (kind) {
                is Kind.Ranked -> {
                    while (stack.isNotEmpty() && (stack.last().rank >= kind.rank || stack.last().rank == TOP)) stack.removeLast()
                    val node = Node(entry, kind.rank)
                    (stack.lastOrNull()?.children ?: roots) += node
                    stack.addLast(node)
                }
                Kind.Top -> {
                    stack.clear()
                    val node = Node(entry, TOP)
                    roots += node
                    stack.addLast(node)
                }
                Kind.Plain -> {
                    val parent = stack.lastOrNull()
                    // Matter right after the front matter's heading stays beside it, not under it.
                    if (parent == null || parent.rank == TOP) {
                        if (parent != null) stack.clear()
                        roots += Node(entry, PLAIN)
                    } else parent.children += Node(entry, PLAIN)
                }
            }
        }
        fun Node.build(): TocEntry = entry.copy(children = children.map { it.build() })
        return roots.map { it.build() }
    }

    private const val TOP = 0
    private const val PLAIN = 100
}
