package com.shamela.library.presentation.screens.settings.components

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import com.shamela.apptheme.presentation.theme.ShamelaIcons
import com.shamela.apptheme.presentation.common.SettingsSectionTitle
import androidx.compose.material3.FilterChip
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.text.format.Formatter
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shamela.apptheme.presentation.theme.AppFonts
import com.shamela.apptheme.presentation.util.AppLocale
import com.shamela.library.R
import com.shamela.library.presentation.common.ConfirmationDialog

@Composable
fun ColumnScope.GeneralSettingsScreen(
    downloadedBooksCount: Int,
    downloadedBytes: Long,
    onDeleteAllBooks: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
) {
    val context = LocalContext.current
    LanguageSection(context)

    SettingsSectionTitle(stringResource(R.string.storage))
    Text(
        text = stringResource(
            R.string.storage_usage,
            downloadedBooksCount,
            Formatter.formatShortFileSize(context, downloadedBytes)
        ),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(top = 12.dp)
    )
    var confirmDeleteAll by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { confirmDeleteAll = true },
        enabled = downloadedBooksCount > 0,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.padding(top = 8.dp)
    ) {
        Icon(ShamelaIcons.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.delete_all_books))
    }
    if (confirmDeleteAll) {
        ConfirmationDialog(
            title = stringResource(R.string.delete_all_books),
            message = stringResource(R.string.delete_all_books_message),
            onConfirm = {
                confirmDeleteAll = false
                onDeleteAllBooks()
            },
            onDismiss = { confirmDeleteAll = false },
        )
    }

    SettingsSectionTitle(stringResource(R.string.backup))
    Text(
        text = stringResource(R.string.backup_description),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp)
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(top = 8.dp)
    ) {
        Button(onClick = onExportBackup) {
            Text(stringResource(R.string.backup_export))
        }
        OutlinedButton(onClick = onImportBackup) {
            Text(stringResource(R.string.backup_import))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageSection(context: Context) {
    SettingsSectionTitle(stringResource(R.string.language))
    val selected = remember { AppLocale.selected(context) }
    FlowRow(
        modifier = Modifier.padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppLocale.supported.forEach { language ->
            FilterChip(
                selected = language == selected,
                onClick = { context.findActivity()?.let { AppLocale.select(it, language) } },
                label = {
                    Text(
                        text = stringResource(
                            when (language) {
                                AppLocale.ARABIC -> R.string.language_arabic
                                AppLocale.TURKISH -> R.string.language_turkish
                                else -> R.string.language_system
                            }
                        ),
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
