@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.shamela.library.presentation.common

import com.shamela.library.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.shamela.apptheme.presentation.theme.ShamelaIcons
import com.shamela.library.domain.util.BookSortOption

/**
 * A sort icon button that opens a dropdown letting the user pick a [BookSortOption] and toggle
 * the sort direction. Stateless: the caller owns [sortOption]/[ascending] and reacts to callbacks.
 *
 * [availableOptions] lets a screen hide options that don't apply (e.g. DOWNLOAD_TIME for remote
 * books that have no local file).
 */
@Composable
fun BookSortMenu(
    sortOption: BookSortOption,
    ascending: Boolean,
    onOptionSelected: (BookSortOption) -> Unit,
    onToggleDirection: () -> Unit,
    modifier: Modifier = Modifier,
    availableOptions: List<BookSortOption> = BookSortOption.values().toList(),
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(imageVector = ShamelaIcons.Sort, contentDescription = stringResource(R.string.sort_books))
        }
        // Material 3 Expressive menu: the options in one group (the current one tinted, with a
        // check), the direction toggle in a second.
        DropdownMenuPopup(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, 2)) {
                MenuDefaults.Label {
                    Text(
                        text = stringResource(R.string.sort_by),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                availableOptions.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        selected = option == sortOption,
                        onClick = {
                            onOptionSelected(option)
                            expanded = false
                        },
                        text = { Text(text = stringResource(option.label)) },
                        // The label is the group's first row, so the options start from the middle.
                        shapes = menuItemShapes(index + 1, availableOptions.size + 1),
                        // Space where the check goes, so every label starts at the same place.
                        leadingIcon = { Spacer(Modifier.size(24.dp)) },
                        selectedLeadingIcon = { Icon(ShamelaIcons.Check, contentDescription = null) },
                        colors = menuSelectableItemColors(),
                    )
                }
            }
            Spacer(Modifier.height(MenuDefaults.GroupSpacing))
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(1, 2)) {
                // Toggling direction keeps the menu open so the change is visible.
                DropdownMenuItem(
                    onClick = onToggleDirection,
                    text = {
                        Text(text = stringResource(if (ascending) R.string.sort_ascending else R.string.sort_descending))
                    },
                    shape = menuItemShapes(0, 1).shape,
                    leadingIcon = {
                        Icon(
                            imageVector = if (ascending) ShamelaIcons.ArrowUpward else ShamelaIcons.ArrowDownward,
                            contentDescription = null
                        )
                    }
                )
            }
        }
    }
}