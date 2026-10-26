package com.shamela.library.presentation.screens.library

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.shamela.apptheme.presentation.theme.ShamelaIcons
import com.shamela.library.R

enum class BooksViewType(@StringRes val label: Int) {
    Sections(label = R.string.view_sections),
    Books(label = R.string.view_books);

    val icon: ImageVector
        get() = when (this) {
            Sections -> ShamelaIcons.LibraryBooks
            Books -> ShamelaIcons.MenuBook
        }
}
