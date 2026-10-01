package com.shamela.library.presentation.screens.library

import androidx.annotation.StringRes
import com.shamela.library.R

enum class BooksViewType(@StringRes val label: Int) {
    Sections(label = R.string.view_sections),
    Books(label = R.string.view_books)
}
