@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.shamela.library.presentation.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemColors
import androidx.compose.material3.MenuItemShapes
import androidx.compose.runtime.Composable

/**
 * Shared look of the app's Material 3 Expressive menus (DropdownMenuPopup + DropdownMenuGroup).
 *
 * Item shape by position in its group: the first item has the group's large top corners, the last
 * its large bottom corners, the rest small ones. The selected item keeps that same shape (the
 * library default turns it into a pill, which doesn't follow the group's corners).
 */
@Composable
fun menuItemShapes(index: Int, count: Int): MenuItemShapes {
    val shapes = MenuDefaults.itemShape(index, count)
    return shapes.copy(selectedShape = shapes.shape)
}

/** Selected item tinted with the app's accent instead of the default tertiary (purple). */
@Composable
fun menuSelectableItemColors(): MenuItemColors = MenuDefaults.selectableItemColors(
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
    selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
)
