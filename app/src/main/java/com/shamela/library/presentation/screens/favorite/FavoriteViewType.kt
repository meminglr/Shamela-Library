package com.shamela.library.presentation.screens.favorite

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.shamela.apptheme.presentation.theme.ShamelaIcons
import com.shamela.library.R

enum class FavoriteViewType(@StringRes val label: Int) {
    Quotes(label = R.string.view_quotes),
    Books(label = R.string.view_books);

    val icon: ImageVector
        get() = when (this) {
            Quotes -> ShamelaIcons.FormatQuote
            Books -> ShamelaIcons.MenuBook
        }
}
