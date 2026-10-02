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
import com.shamela.library.domain.model.Quote


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QuoteItem(
    modifier: Modifier,
    item: Quote,
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
                icon = ShamelaIcons.AutoStories,
                shape = MaterialShapes.Sunny.toShape(),
                container = MaterialTheme.colorScheme.tertiaryContainer,
                content = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        },
        // Reader page indexes are zero-based; people count pages from one.
        supportingContent = {
            Text(
                text = stringResource(R.string.page_number_label, item.pageIndex + 1),
                style = MaterialTheme.typography.labelMedium,
            )
        },
        trailingContent = {
            Icon(imageVector = ShamelaIcons.NavigateForward, contentDescription = null)
        },
    ) {
        Text(
            text = item.text,
            maxLines = 4,
            style = AppFonts.content(MaterialTheme.typography.bodyLarge),
            overflow = TextOverflow.Ellipsis
        )
    }
}
