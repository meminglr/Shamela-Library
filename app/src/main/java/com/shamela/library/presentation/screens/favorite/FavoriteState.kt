package com.shamela.library.presentation.screens.favorite

import com.shamela.library.domain.model.Book
import com.shamela.library.domain.model.Quote


data class FavoriteState(
    val favoriteBooks: List<Book> = emptyList(),
    val favoriteQuotes: List<Quote> = emptyList(),
    /** Each list's empty state shows only once that list has loaded (no flash before the first read). */
    val booksLoaded: Boolean = false,
    val quotesLoaded: Boolean = false,
    val viewType: FavoriteViewType = FavoriteViewType.Quotes,
)
