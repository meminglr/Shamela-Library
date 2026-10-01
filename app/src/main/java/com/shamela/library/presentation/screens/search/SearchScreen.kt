package com.shamela.library.presentation.screens.search


import com.shamela.apptheme.presentation.common.EmptyState
import com.shamela.apptheme.presentation.common.SettingsSectionTitle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.AssistChip
import androidx.compose.material3.InputChip
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.shamela.library.presentation.common.sectionDisplayName
import com.shamela.library.R
import androidx.compose.ui.res.stringResource
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.folioreader.ui.activity.searchActivity.SearchActivity
import com.shamela.apptheme.presentation.common.EmptyListScreen
import com.shamela.apptheme.presentation.common.LoadingScreen
import com.shamela.apptheme.presentation.theme.AppFonts
import com.shamela.apptheme.presentation.theme.ShamelaIcons
import com.shamela.library.domain.model.Category
import com.shamela.library.presentation.screens.LocalPaddingValues

@Composable
fun SearchScreen(
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val searchState = viewModel.searchState.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    val localPadding = LocalPaddingValues.current
    LaunchedEffect(key1 = Unit, block = {
        viewModel.onEvent(SearchEvent.GetAllCategories)
    })
    val canSearch = searchState.searchQuery.isNotBlank() && searchState.selectedCategories.isNotEmpty()
    val startSearch = {
        if (canSearch) {
            val intent = Intent(context, SearchActivity::class.java).apply {
                putExtra(SearchActivity.Search_Type, SearchActivity.Search_Type_SectionsSearch)
                putExtra(SearchActivity.Search_Query, searchState.searchQuery)
                putExtra(
                    SearchActivity.Search_Categories,
                    searchState.selectedCategories.map { it.name }.toTypedArray()
                )
            }
            context.startActivity(intent)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(localPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        SearchTextField(
            value = searchState.searchQuery,
            onValueChanged = { viewModel.onEvent(SearchEvent.OnChangeSearchQuery(it)) },
            onSearch = startSearch,
            onClear = { viewModel.onEvent(SearchEvent.OnChangeSearchQuery("")) }
        )
        SettingsSectionTitle(stringResource(R.string.view_sections))
        SelectedSections(
            allCategories = searchState.allCategories,
            selectedCategories = searchState.selectedCategories,
            expanded = searchState.isListExpanded,
            onExpandedChange = { viewModel.onEvent(SearchEvent.ToggleCategoriesList) },
            onDismiss = { viewModel.onEvent(SearchEvent.CloseCategoriesList) },
            onItemChecked = { category -> viewModel.onEvent(SearchEvent.ItemChecked(category)) }
        )
        AnimatedVisibility(visible = searchState.selectedCategories.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = ShamelaIcons.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    stringResource(R.string.search_select_sections_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Button(
            onClick = startSearch,
            enabled = canSearch,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
        ) {
            Icon(ShamelaIcons.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.nav_search))
        }
    }
    LoadingScreen(visibility = searchState.isLoading && searchState.allCategories.isNotEmpty())
    if (searchState.allCategories.isEmpty() && !searchState.isLoading) {
        EmptyState(
            icon = ShamelaIcons.Search,
            title = stringResource(R.string.search_requires_downloads),
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        )
    }
}

@Composable
private fun SearchTextField(
    value: String,
    onValueChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
) {
    OutlinedTextField(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        value = value,
        onValueChange = onValueChanged,
        textStyle = AppFonts.content(MaterialTheme.typography.bodyLarge),
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        leadingIcon = { Icon(ShamelaIcons.Search, contentDescription = null) },
        placeholder = { Text(text = stringResource(R.string.search_word_placeholder)) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        trailingIcon = {
            AnimatedVisibility(
                value.isNotEmpty(),
                enter = fadeIn(), exit = fadeOut()
            ) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = ShamelaIcons.Cancel,
                        contentDescription = stringResource(R.string.action_clear)
                    )
                }
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectedSections(
    allCategories: List<Category>,
    selectedCategories: List<Category>,
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    onDismiss: () -> Unit,
    onItemChecked: (Category) -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        selectedCategories.forEach { category ->
            InputChip(
                selected = true,
                onClick = { onItemChecked(category) },
                label = {
                    Text(
                        sectionDisplayName(category.name),
                        style = AppFonts.content(MaterialTheme.typography.labelLarge),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingIcon = {
                    Icon(
                        ShamelaIcons.Cancel,
                        contentDescription = stringResource(R.string.action_clear),
                        modifier = Modifier.size(18.dp)
                    )
                },
            )
        }
        AssistChip(
            onClick = onExpandedChange,
            label = { Text(stringResource(R.string.choose_section)) },
            leadingIcon = { Icon(ShamelaIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
    }
    if (expanded)
        AlertDialog(
            title = { Text(stringResource(R.string.choose_sections)) },
            onDismissRequest = { onDismiss() },
            text = {
                LazyColumn {
                    item {
                        val allSelected = selectedCategories.size == allCategories.size
                        CategoryCheckRow(
                            label = stringResource(R.string.select_all),
                            checked = allSelected,
                            emphasized = true,
                        ) {
                            allCategories
                                .filter { (it in selectedCategories) == allSelected }
                                .forEach(onItemChecked)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    items(allCategories) { item ->
                        CategoryCheckRow(
                            label = sectionDisplayName(item.name),
                            checked = item in selectedCategories,
                        ) { onItemChecked(item) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onDismiss() }) {
                    Text(stringResource(R.string.done))
                }
            },
        )
}

@Composable
private fun CategoryCheckRow(
    label: String,
    checked: Boolean,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onClick() })
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
        Text(
            text = label,
            style = if (emphasized) MaterialTheme.typography.titleSmall
            else AppFonts.content(MaterialTheme.typography.bodyLarge),
        )
    }
}
