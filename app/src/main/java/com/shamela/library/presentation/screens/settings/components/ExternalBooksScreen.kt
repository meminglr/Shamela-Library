@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.shamela.library.presentation.screens.settings.components

import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.OutlinedCard
import com.shamela.apptheme.presentation.common.SettingsSectionTitle
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shamela.apptheme.presentation.theme.AppFonts
import com.shamela.apptheme.presentation.theme.AppTheme
import com.shamela.apptheme.presentation.theme.ShamelaIcons
import com.shamela.apptheme.presentation.theme.colors.Green
import com.shamela.apptheme.presentation.util.ShamelaPrev
import com.shamela.library.R

@Composable
fun ColumnScope.ExternalBooksScreen(
    onClickSelectBook: () -> Unit,
    onClickAddBookToLibrary: () -> Unit,
    selectedFileName: String?,
    selectedFileUri: Uri?
) {
    SettingsSectionTitle(stringResource(R.string.add_external_book))
    Text(
        text = stringResource(R.string.external_book_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp)
    )
    SelectBookButton(selectedFileName, onClickSelectBook)

    AnimatedVisibility(visible = selectedFileUri != null) {
        Button(shapes = ButtonDefaults.shapes(), onClick = onClickAddBookToLibrary, modifier = Modifier.fillMaxWidth()) {
            Icon(ShamelaIcons.LocalLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text = stringResource(R.string.add_to_library))
        }
    }
}

@Composable
private fun SelectBookButton(
    selectedFileName: String?,
    onClickSelectBook: () -> Unit,
) {
    OutlinedCard(
        onClick = onClickSelectBook,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (selectedFileName == null) ShamelaIcons.Add else ShamelaIcons.Book,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = selectedFileName ?: stringResource(R.string.select_book),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}


@ShamelaPrev
@Composable
private fun ExternalBooksScreenPrev() {
    AppTheme.ShamelaLibraryTheme {
        AppTheme.changeColorScheme(Green.lightColorScheme, Green.name)
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            ExternalBooksScreen(
                onClickSelectBook = { },
                onClickAddBookToLibrary = {},
                selectedFileName = stringResource(R.string.select_book),
                selectedFileUri = null,
            )
        }
    }
}