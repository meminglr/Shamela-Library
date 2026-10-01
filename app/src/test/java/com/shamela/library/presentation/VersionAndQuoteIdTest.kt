package com.shamela.library.presentation

import com.shamela.library.domain.model.Quote
import com.shamela.library.presentation.screens.about.VersionComparator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionAndQuoteIdTest {
    @Test
    fun versionComparison_isNumeric() {
        assertTrue(VersionComparator.isNewer("1.10", "1.9"))
        assertTrue(VersionComparator.isNewer("v1.4", "1.3"))
        assertTrue(VersionComparator.isNewer("2", "1.9.9"))
        assertFalse(VersionComparator.isNewer("1.3", "1.3"))
        assertFalse(VersionComparator.isNewer("1.3", "1.3.0"))
        assertFalse(VersionComparator.isNewer("1.2", "1.3"))
    }

    @Test
    fun sameTextInDifferentBooks_getsDifferentQuoteIds() {
        val a = Quote("الحمد لله", 1, "p1.xhtml", "أ", "book-a")
        val b = Quote("الحمد لله", 1, "p1.xhtml", "ب", "book-b")
        assertNotEquals(a.quoteId, b.quoteId)
    }

    @Test
    fun sameQuote_getsStableId() {
        assertEquals(
            Quote("نص", 1, "p1.xhtml", "أ", "book-a").quoteId,
            Quote("نص", 1, "p1.xhtml", "أ", "book-a").quoteId
        )
    }
}
