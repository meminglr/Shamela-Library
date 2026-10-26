@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.shamela.library.presentation.screens.settings.components

import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import com.shamela.apptheme.presentation.theme.ShamelaIcons
import com.shamela.apptheme.presentation.common.SettingsCard
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
import com.shamela.library.presentation.common.SourceBadges
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ColumnScope.GeneralSettingsScreen(
    downloadedBooksCount: Int,
    downloadedBytes: Long,
    onDeleteAllBooks: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    importProgress: Pair<Int, Int>? = null,
    onImportLibrary: () -> Unit = {},
    externalBooks: @Composable ColumnScope.() -> Unit = {},
) {
    val context = LocalContext.current
    LanguageSection(context)
    SourceBadgesSection(context)

    SettingsCard(stringResource(R.string.import_library), icon = ShamelaIcons.FileDownload) {
        Text(
            text = stringResource(R.string.import_library_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (importProgress == null) {
            Button(shapes = ButtonDefaults.shapes(), onClick = onImportLibrary) {
                Icon(ShamelaIcons.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.import_choose_folder))
            }
        } else {
            val (done, total) = importProgress
            Text(
                text = if (total == 0) stringResource(R.string.import_scanning)
                else stringResource(R.string.import_progress, done, total),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (total == 0) LinearProgressIndicator(Modifier.fillMaxWidth())
            else LinearProgressIndicator(progress = { done.toFloat() / total }, modifier = Modifier.fillMaxWidth())
        }
    }

    externalBooks()

    SettingsCard(stringResource(R.string.storage), icon = ShamelaIcons.Storage) {
        Text(
            text = stringResource(
                R.string.storage_usage,
                downloadedBooksCount,
                Formatter.formatShortFileSize(context, downloadedBytes)
            ),
            style = MaterialTheme.typography.bodyLarge,
        )
        var confirmDeleteAll by remember { mutableStateOf(false) }
        OutlinedButton(
            shapes = ButtonDefaults.shapes(),
            onClick = { confirmDeleteAll = true },
            enabled = downloadedBooksCount > 0,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
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
    }

    SettingsCard(stringResource(R.string.backup), icon = ShamelaIcons.Backup) {
        Text(
            text = stringResource(R.string.backup_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(shapes = ButtonDefaults.shapes(), onClick = onExportBackup) {
                Icon(ShamelaIcons.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.backup_export))
            }
            OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = onImportBackup) {
                Icon(ShamelaIcons.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.backup_import))
            }
        }
    }
}

/** Show or hide the library numbers (1, 2) on book icons. */
@Composable
private fun SourceBadgesSection(context: Context) {
    SettingsCard(stringResource(R.string.source_badges_setting), icon = ShamelaIcons.LocalLibrary) {
        val visible by SourceBadges.visible.collectAsStateWithLifecycle()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = visible,
                    role = Role.Switch,
                    onValueChange = { SourceBadges.setVisible(context, it) },
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.source_badges_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            // The row handles the tap, so the whole line toggles and is read as one switch.
            Switch(checked = visible, onCheckedChange = null)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageSection(context: Context) {
    SettingsCard(stringResource(R.string.language), icon = ShamelaIcons.Language) {
        val selected = remember { AppLocale.selected(context) }
        FlowRow(
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
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
