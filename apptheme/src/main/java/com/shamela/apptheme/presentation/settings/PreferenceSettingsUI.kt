package com.shamela.apptheme.presentation.settings


import com.shamela.apptheme.presentation.theme.colors.AppColors
import com.shamela.apptheme.presentation.theme.ShamelaIcons
import com.shamela.apptheme.presentation.common.SettingsSectionTitle
import kotlin.math.roundToInt
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shamela.apptheme.R
import com.shamela.apptheme.domain.model.UserPrefs
import com.shamela.apptheme.presentation.theme.AppFonts
import com.shamela.apptheme.presentation.theme.AppTheme
import com.shamela.apptheme.presentation.util.ShamelaPrev
import kotlin.math.ceil
import kotlin.math.max


@Composable
fun PreferenceSettingsUI(
    modifier: Modifier = Modifier,
    uiState: PreferenceSettingsState,
    onEvent: (PreferenceSettingsEvent) -> Unit,
) {
    val isSystemDark = isSystemInDarkTheme()
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
    ) {
        SettingsSectionTitle(stringResource(R.string.change_theme))
        ChoiceChips(
            options = uiState.availableThemes,
            selected = uiState.userPrefs.theme,
            leading = { theme ->
                val icon = when (theme) {
                    AppTheme.LIGHT -> ShamelaIcons.LightMode
                    AppTheme.DARK -> ShamelaIcons.DarkMode
                    else -> ShamelaIcons.AutoMode
                }
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            },
        ) {
            onEvent(
                PreferenceSettingsEvent.OnChangeAppTheme(
                    colorScheme = AppTheme.themeOf(
                        theme = it,
                        colorScheme = uiState.userPrefs.colorSchemeName,
                        isSystemInDarkTheme = isSystemDark,
                        context = context
                    ),
                    uiState.userPrefs.copy(theme = it)
                )
            )
        }

        SettingsSectionTitle(stringResource(R.string.change_color))
        ChoiceChips(
            options = uiState.availableColorSchemes,
            selected = uiState.userPrefs.colorSchemeName,
            leading = { name ->
                val swatch = remember(name) {
                    AppColors.colorSchemeOf(name, context).lightColorScheme.primary
                }
                Box(
                    Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(swatch)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                )
            },
        ) {
            onEvent(
                PreferenceSettingsEvent.OnChangeAppTheme(
                    colorScheme = AppTheme.themeOf(
                        theme = uiState.userPrefs.theme,
                        colorScheme = it,
                        isSystemInDarkTheme = isSystemDark,
                        context = context
                    ),
                    userPrefs = uiState.userPrefs.copy(colorSchemeName = it)
                )
            )
        }

        SettingsSectionTitle(stringResource(R.string.font_size))
        FontSizeSelector(
            sliderPosition = uiState.sliderPosition,
            onSliderPositionChanged = {
                onEvent(PreferenceSettingsEvent.OnChangeSliderPosition(it))
            },
            list = uiState.availableFontSizes,
            onValueChangeFinished = { finalPosition ->
                val index = finalPosition.roundToInt().coerceIn(0, uiState.availableFontSizes.lastIndex)
                onEvent(
                    PreferenceSettingsEvent.OnChangeAppFontSize(
                        uiState.userPrefs.copy(fontSize = uiState.availableFontSizes[index])
                    )
                )
            }
        )

        SettingsSectionTitle(stringResource(R.string.font_family))
        FontsSection(
            options = uiState.availableFontFamilies,
            selectedOption = uiState.userPrefs.fontFamily,
        ) {
            onEvent(
                PreferenceSettingsEvent.OnChangeAppFont(uiState.userPrefs.copy(fontFamily = it))
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceChips(
    options: List<String>,
    selected: String,
    leading: @Composable (String) -> Unit,
    onOptionClicked: (String) -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onOptionClicked(option) },
                label = { Text(PreferenceLabels.label(option), style = MaterialTheme.typography.labelLarge) },
                leadingIcon = { leading(option) },
            )
        }
    }
}

@Composable
private fun FontsSection(
    options: List<String>,
    selectedOption: String,
    onOptionClicked: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(top = 8.dp)
            .selectableGroup()
    ) {
        options.forEach { option ->
            val isSelected = option == selectedOption
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .selectable(selected = isSelected, role = Role.RadioButton) { onOptionClicked(option) }
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = isSelected, onClick = null)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = PreferenceLabels.label(option),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                // Sample in the font itself, so the choice is visible before applying it.
                Text(
                    text = "بسم الله",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = AppFonts.fontFamilyOf(option),
                        textDirection = TextDirection.Rtl,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}


@Composable
private fun FontSizeSelector(
    sliderPosition: Float,
    list: List<Int>,
    onSliderPositionChanged: (Float) -> Unit,
    onValueChangeFinished: (Float) -> Unit,
) {
    // Tracks the live drag value so onValueChangeFinished always receives the current position,
    // not the stale one from the previous recomposition.
    var currentValue by remember { mutableFloatStateOf(sliderPosition) }
    LaunchedEffect(sliderPosition) { currentValue = sliderPosition }

    val labels = listOf(
        stringResource(R.string.xSmall),
        stringResource(R.string.small),
        stringResource(R.string.normal),
        stringResource(R.string.large),
        stringResource(R.string.xLarge),
    )
    val index = currentValue.roundToInt().coerceIn(0, max(list.lastIndex, 0))
    Column(modifier = Modifier.padding(top = 12.dp)) {
        Text(
            text = labels.getOrElse(index) { "" },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("A", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = currentValue,
                onValueChange = {
                    currentValue = it
                    onSliderPositionChanged(it)
                },
                onValueChangeFinished = { onValueChangeFinished(currentValue) },
                valueRange = 0f..max(list.lastIndex.toFloat(), 0f),
                // n positions need n - 2 intermediate steps.
                steps = max(list.size - 2, 0),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            )
            Text("A", style = MaterialTheme.typography.titleLarge)
        }
        // Live preview at the selected size.
        Text(
            text = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
            style = AppFonts.content(MaterialTheme.typography.bodyLarge).copy(
                fontSize = (16 + list.getOrElse(index) { 0 }).sp,
                textAlign = TextAlign.Center,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(16.dp),
        )
    }
}

@ShamelaPrev
@Composable
private fun PreferenceScreenPrev() {
    AppTheme.ShamelaLibraryTheme {
        PreferenceSettingsUI(
            uiState = PreferenceSettingsState(
                userPrefs = UserPrefs(),
                availableFontSizes = listOf(-4, -2, 0, 2, 4),
                availableFontFamilies = listOf("خط النسخ", "خط الرق"),
                availableColorSchemes = listOf("ذهبي", "ازرق"),
                availableThemes = listOf("فاتح", "مظلم", "تلقائي")
            ), onEvent = {}
        )
    }
}
