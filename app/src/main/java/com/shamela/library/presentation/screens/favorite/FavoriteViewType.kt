package com.shamela.library.presentation.screens.favorite

import androidx.annotation.StringRes
import com.shamela.library.R

enum class FavoriteViewType(@StringRes val label: Int) {
    Quotes(label = R.string.view_quotes),
    Books(label = R.string.view_books)
}
