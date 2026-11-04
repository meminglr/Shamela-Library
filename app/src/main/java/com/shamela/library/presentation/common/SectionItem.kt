package com.shamela.library.presentation.common

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.shamela.apptheme.presentation.common.Revealed
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
    // Built in its turn as the page appears (ProgressiveReveal), not with every other row at once.
    Revealed(SectionRowHeight) {
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
}

/** Section (folder) names are data; only the app-created folders (external, scanned books) are translated. */
@Composable
fun sectionDisplayName(name: String): String = when (name) {
    ShamelaApp.EXTERNAL_BOOKS_CATEGORY -> stringResource(R.string.external_books_section)
    ShamelaApp.SCANNED_BOOKS_CATEGORY -> stringResource(R.string.scanned_books_section)
    else -> name
}

/**
 * Sections under subject headings (Fiqh, Hadith, …), alphabetical within each; each group is its
 * own segmented block.
 */
fun androidx.compose.foundation.lazy.LazyListScope.groupedSectionItems(
    sections: Collection<Category>,
    onClick: (Category) -> Unit,
) {
    com.shamela.library.domain.util.SectionGroup.group(sections).forEach { (group, items) ->
        item(key = "group-${group.name}") {
            SectionGroupHeader(
                title = stringResource(group.label),
                bookCount = items.sumOf { it.bookCount },
                modifier = Modifier.animateItem(),
            )
        }
        items(items.size, key = { items[it].id }) { index ->
            val category = items[index]
            SectionItem(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .animateItem(),
                item = category,
                onClick = { onClick(category) },
                index = index,
                count = items.size,
            )
        }
    }
}

@Composable
private fun SectionGroupHeader(title: String, bookCount: Int, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Row(
        modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.books_count_label, bookCount),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** About a row's height, for the space it keeps until it is built. */
private val SectionRowHeight = 76.dp
