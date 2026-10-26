package com.shamela.library.presentation

import com.shamela.library.domain.model.Quote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class QuoteIdTest {
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
