package com.shamela.library.domain.util

import com.shamela.library.domain.model.Book

object BooksGroupingUtil {
    /** Bucket for titles that don't start with a letter (digits, punctuation, empty). */
    const val OTHER = '#'

    /**
     * Groups books by the first letter of their title for the alphabet headers. Alef forms
     * (آ أ إ ٱ) share one "ا" bucket, and titles starting with digits or symbols go to [OTHER],
     * listed last, instead of producing one-off headers like "4" or "_".
     */
    fun groupByFirstChar(books: List<Book>): Map<Char, List<Book>> {
        return books.sortedBy { it.title }
            .groupBy { bucketOf(it.title) }
            .toSortedMap(compareBy<Char> { it == OTHER }.thenBy { it })
    }

    fun bucketOf(title: String): Char {
        val first = title.trimStart().firstOrNull() ?: return OTHER
        return when {
            first in "آأإٱ" -> 'ا'
            first.isLetter() -> first
            else -> OTHER
        }
    }
}
