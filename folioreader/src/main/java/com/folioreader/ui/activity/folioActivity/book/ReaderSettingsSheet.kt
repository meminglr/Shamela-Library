@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.folioreader.ui.activity.folioActivity.book

import com.shamela.apptheme.presentation.common.PillTabs
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.LeadingIconTab
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.folioreader.R
import com.folioreader.reader.ReaderSettings
import com.folioreader.reader.ReaderTheme
import com.shamela.apptheme.presentation.settings.PreferenceLabels
import com.shamela.apptheme.presentation.theme.AppFonts
import com.shamela.apptheme.presentation.theme.ShamelaIcons

/** The three groups of the reading settings panel. */
private enum class ReaderSettingsTab(val label: Int, val icon: ImageVector) {
    Page(R.string.reader_section_page, ShamelaIcons.Margins),
    Text(R.string.reader_section_text, ShamelaIcons.TextFields),
    Screen(R.string.reader_section_screen, ShamelaIcons.Brightness),
}

/**
 * Reading settings, laid out like the "Aa" panels of Apple Books / Kindle / Play Books: a short
 * sheet over the bottom third of the screen with no dimming, so the page stays visible and every
 * change shows on it at once. The two most used controls (page color, text size) are always on
 * top; the rest is split into three tabs of two or three rows each.
 */
@Composable
fun ReaderSettingsSheet(
    settings: ReaderSettings,
    isAppDark: Boolean,
    onChange: ((ReaderSettings) -> ReaderSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(ReaderSettingsTab.Page) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // No dimming: the reader must see the page while adjusting it.
        scrimColor = Color.Transparent,
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.padding(bottom = 12.dp)) {
            // Always visible: page colors and text size.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PageColorSwatches(settings.theme, isAppDark, Modifier.weight(1f)) { theme ->
                    onChange { it.copy(theme = theme) }
                }
                TextSizeStepper(settings.fontSize) { size -> onChange { it.copy(fontSize = size) } }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PillTabs(
                    labels = ReaderSettingsTab.entries.map { stringResource(it.label) },
                    icons = ReaderSettingsTab.entries.map { it.icon },
                    selectedIndex = tab.ordinal,
                    onSelect = { tab = ReaderSettingsTab.entries[it] },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onChange { ReaderSettings() } }, modifier = Modifier.padding(end = 8.dp)) {
                    Text(stringResource(R.string.reset))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Same height for every tab, so the sheet doesn't jump while switching.
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 168.dp)
                    .padding(horizontal = 20.dp, vertical = 4.dp)
            ) {
                when (tab) {
                    ReaderSettingsTab.Page -> Column {
                        SettingRow(stringResource(R.string.page_margins), ShamelaIcons.Margins) {
                            CompactChoice(
                                options = ReaderSettings.MARGINS,
                                selected = ReaderSettings.MARGINS.minBy { kotlin.math.abs(it - settings.margin) },
                                label = { spacingLabel(ReaderSettings.MARGINS.indexOf(it)) },
                                onSelect = { value -> onChange { it.copy(margin = value) } },
                            )
                        }
                        SettingRow(stringResource(R.string.text_alignment), ShamelaIcons.FormatAlignJustify) {
                            CompactChoice(
                                options = listOf(false, true),
                                selected = settings.justify,
                                label = { stringResource(if (it) R.string.align_justify else R.string.align_right) },
                                onSelect = { value -> onChange { it.copy(justify = value) } },
                            )
                        }
                        SettingRow(stringResource(R.string.page_turn_animation), ShamelaIcons.AutoStories) {
                            Switch(checked = settings.pageTurn, onCheckedChange = { c -> onChange { it.copy(pageTurn = c) } })
                        }
                    }

                    ReaderSettingsTab.Text -> Column {
                        SettingRow(stringResource(R.string.line_spacing), ShamelaIcons.FormatLineSpacing) {
                            CompactChoice(
                                options = ReaderSettings.LINE_HEIGHTS,
                                selected = ReaderSettings.LINE_HEIGHTS.minBy { kotlin.math.abs(it - settings.lineHeight) },
                                label = { spacingLabel(ReaderSettings.LINE_HEIGHTS.indexOf(it)) },
                                onSelect = { value -> onChange { it.copy(lineHeight = value) } },
                            )
                        }
                        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RowIcon(ShamelaIcons.FontDownload)
                            Text(stringResource(R.string.reader_font), style = MaterialTheme.typography.bodyLarge)
                        }
                        FontPicker(settings.fontKey) { key -> onChange { it.copy(fontKey = key) } }
                        SettingRow(stringResource(R.string.hide_harakat), ShamelaIcons.FormatClear) {
                            Switch(checked = settings.hideHarakat, onCheckedChange = { c -> onChange { it.copy(hideHarakat = c) } })
                        }
                    }

                    ReaderSettingsTab.Screen -> Column {
                        BrightnessRow(settings.brightness) { level -> onChange { it.copy(brightness = level) } }
                        SettingRow(stringResource(R.string.keep_screen_on), ShamelaIcons.Smartphone) {
                            Switch(checked = settings.keepScreenOn, onCheckedChange = { c -> onChange { it.copy(keepScreenOn = c) } })
                        }
                    }
                }
            }
        }
    }
}

/** A− size A+ (the most used control, always visible). */
@Composable
private fun TextSizeStepper(size: Int, onChange: (Int) -> Unit) {
    val range = ReaderSettings.FONT_SIZE_RANGE
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(
            onClick = { onChange((size - 1).coerceIn(range)) },
            enabled = size > range.first,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(36.dp),
        ) { Text("A", style = MaterialTheme.typography.labelMedium) }
        Text(
            text = size.toString(),
            style = MaterialTheme.typography.titleMediumEmphasized,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(36.dp)
        )
        FilledTonalIconButton(
            onClick = { onChange((size + 1).coerceIn(range)) },
            enabled = size < range.last,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(36.dp),
        ) { Text("A", style = MaterialTheme.typography.titleMedium) }
    }
}

/** Label on the start side, control(s) on the end side, 48dp minimum height. */
@Composable
private fun SettingRow(label: String, icon: ImageVector, control: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowIcon(icon)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        )
        control()
    }
}

/** Small leading icon of a setting row. */
@Composable
private fun RowIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(end = 12.dp).size(20.dp),
    )
}

/** Small connected toggle buttons (Expressive), sized to their labels. */
@Composable
private fun <T> CompactChoice(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { index, option ->
            ToggleButton(
                checked = option == selected,
                onCheckedChange = { onSelect(option) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = ToggleButtonDefaults.tonalToggleButtonColors(),
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.heightIn(min = 36.dp),
            ) {
                Text(label(option), style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}

@Composable
private fun spacingLabel(index: Int): String = stringResource(
    when (index) {
        0 -> R.string.spacing_compact
        2 -> R.string.spacing_wide
        else -> R.string.spacing_normal
    }
)

/** Five small round swatches; "auto" is split light/dark, the selected one gets a ring and check. */
@Composable
private fun PageColorSwatches(
    selected: ReaderTheme,
    isAppDark: Boolean,
    modifier: Modifier = Modifier,
    onSelect: (ReaderTheme) -> Unit,
) {
    Row(
        modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReaderTheme.entries.forEach { theme ->
                val isSelected = theme == selected
                // "Follow app" shows the app palette's page color.
                val page = Color(theme.resolve(isAppDark).background)
                val fill = Brush.linearGradient(listOf(page, page))
                val name = stringResource(
                    when (theme) {
                        ReaderTheme.FollowApp -> R.string.reader_theme_auto
                        ReaderTheme.Light -> R.string.reader_theme_light
                        ReaderTheme.Sepia -> R.string.reader_theme_sepia
                        ReaderTheme.Dark -> R.string.reader_theme_dark
                        ReaderTheme.Black -> R.string.reader_theme_black
                    }
                )
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(fill)
                        .border(
                            BorderStroke(
                                if (isSelected) 3.dp else 1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            CircleShape
                        )
                        .clickable { onSelect(theme) }
                        .semantics { contentDescription = name },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            ShamelaIcons.Check,
                            contentDescription = null,
                            tint = Color(theme.resolve(isAppDark).text),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Horizontal list of fonts, each label drawn in its own font. */
@Composable
private fun FontPicker(selectedKey: String?, onSelect: (String?) -> Unit) {
    val fonts = listOf<String?>(null) + AppFonts.getAvailableFontFamilies().toList()
    LazyRow(
        contentPadding = PaddingValues(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(fonts) { key ->
            FilterChip(
                selected = key == selectedKey,
                onClick = { onSelect(key) },
                label = {
                    Text(
                        text = if (key == null) stringResource(R.string.reader_font_app) else PreferenceLabels.label(key),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = if (key == null) null else AppFonts.fontFamilyOf(key),
                            textDirection = TextDirection.Content,
                            fontSize = 15.sp,
                        ),
                    )
                },
            )
        }
    }
}

/** "Brightness" with an inline slider; "auto" follows the system brightness. */
@Composable
private fun BrightnessRow(brightness: Float?, onChange: (Float?) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RowIcon(ShamelaIcons.Brightness)
        Text(
            stringResource(R.string.brightness),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(end = 8.dp)
        )
        Slider(
            value = brightness ?: 0.5f,
            onValueChange = { onChange(it.coerceIn(0.05f, 1f)) },
            enabled = brightness != null,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        FilterChip(
            selected = brightness == null,
            onClick = { onChange(if (brightness == null) 0.5f else null) },
            label = { Text(stringResource(R.string.reader_theme_auto)) },
        )
    }
}
