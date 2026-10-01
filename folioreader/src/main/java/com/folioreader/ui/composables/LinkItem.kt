package com.folioreader.ui.composables


import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.heightIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.shamela.apptheme.presentation.theme.AppFonts
import com.shamela.apptheme.presentation.theme.ShamelaIcons
import org.readium.r2.shared.Link

/**
 * One table-of-contents entry. Rows grow with their title (long chapter names used to be cut off
 * by a fixed 48dp box), nested levels are indented, and expandable entries show a chevron.
 */
@Composable
fun LinkItem(
    item: Link,
    level: Int,
    isFirstItem: Boolean = false,
    onLinkClicked: (String?, String?) -> Unit,
) {
    var isExpanded by remember { mutableStateOf(false) }
    val hasChildren = item.children.isNotEmpty() && level != 3
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable { onLinkClicked(item.title, item.href) }
                .padding(start = (16 + 20 * level).dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.title ?: "",
                style = AppFonts.content(
                    if (level == 0) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium
                ),
                color = if (level == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (hasChildren) {
                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        imageVector = if (isExpanded) ShamelaIcons.Remove else ShamelaIcons.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = (16 + 20 * level).dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column {
                item.children.forEach {
                    if (level != 3) {
                        LinkItem(
                            item = it,
                            level = level + 1,
                            onLinkClicked = { title, href -> onLinkClicked(title, href) })
                    }
                }
            }
        }
    }
}
