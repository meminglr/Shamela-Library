package com.shamela.apptheme.presentation.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Sticky group header for long lists (alphabet letters, book names). */
@Composable
fun ListGroupHeader(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleSmall,
) {
    // Expressive lists: the header sits on the surface above its rounded group, not in a bar.
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Text(
            text = text,
            style = style,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 16.dp, bottom = 8.dp)
        )
    }
}

/** Title that opens a group of controls on settings-like pages (emphasized, no divider). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsSectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmallEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 4.dp)
    )
}

/** Divider between list rows, inset to line up with the row text. */
@Composable
fun ListDivider(modifier: Modifier = Modifier, startInset: Int = 16) {
    HorizontalDivider(
        modifier = modifier.padding(start = startInset.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}
