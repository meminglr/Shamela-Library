package com.shamela.library.presentation.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.shamela.apptheme.presentation.common.ListGroupHeader
import com.shamela.apptheme.presentation.theme.AppFonts

@Composable
fun CharacterHeader(modifier: Modifier = Modifier, char: Char) {
    ListGroupHeader(
        text = char.toString(),
        modifier = modifier,
        style = AppFonts.content(MaterialTheme.typography.titleSmall)
    )
}

@Composable
fun StringHeader(modifier: Modifier = Modifier, bookName: String) {
    ListGroupHeader(
        text = bookName,
        modifier = modifier,
        style = AppFonts.content(MaterialTheme.typography.titleSmall, naturalAlign = true)
    )
}
