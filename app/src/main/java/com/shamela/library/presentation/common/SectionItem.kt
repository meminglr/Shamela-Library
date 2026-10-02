package com.shamela.library.presentation.common

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.shamela.apptheme.presentation.theme.AppFonts
import com.shamela.apptheme.presentation.theme.ShamelaIcons
import com.shamela.library.R
import com.shamela.library.ShamelaApp
import com.shamela.library.domain.model.Category


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SectionItem(
    modifier: Modifier,
    item: Category,
    highlightText: String = "",
    onClick: () -> Unit = {},
    index: Int = 0,
    count: Int = 1,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = segmentShapes(index, count),
        colors = segmentColors(),
        modifier = modifier,
        leadingContent = {
            ListLeadingTile(
                icon = ShamelaIcons.LibraryBooks,
                shape = MaterialShapes.Clover4Leaf.toShape(),
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        },
        supportingContent = {
            Text(
                text = stringResource(R.string.books_count_label, item.bookCount),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        trailingContent = {
            Icon(imageVector = ShamelaIcons.NavigateForward, contentDescription = null)
        },
    ) {
        Text(
            text = buildHighlightedString(sectionDisplayName(item.name), highlightText),
            style = AppFonts.content(MaterialTheme.typography.titleMedium),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Section (folder) names are data; only the app-created external-books folder is translated. */
@Composable
fun sectionDisplayName(name: String): String =
    if (name == ShamelaApp.EXTERNAL_BOOKS_CATEGORY) stringResource(R.string.external_books_section) else name
