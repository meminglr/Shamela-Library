package com.shamela.apptheme.presentation.common

import com.shamela.apptheme.presentation.theme.AppFonts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shamela.apptheme.R
import com.shamela.apptheme.presentation.theme.AppTheme
import com.shamela.apptheme.presentation.theme.ShamelaIcons
import com.shamela.apptheme.presentation.util.ShamelaPrev


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTopBar(
    onNavigateBack: () -> Unit = {},
    value: String,
    onValueChanged: (String) -> Unit,
    onClickSearch: (String) -> Unit = {},
    onClickClear: () -> Unit = {},
    hint: String,
    focusRequester: FocusRequester,
    // Screens already padded for the status bar (inside the home Scaffold) pass WindowInsets(0);
    // standalone activities keep the default so the bar doesn't slide under the status bar.
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
) {
    TopAppBar(
        windowInsets = windowInsets,
        title = {
            SearchTextField(
                value,
                onValueChanged,
                hint = hint,
                focusRequester = focusRequester,
                onSearch = { onClickSearch(value) }
            )
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(ShamelaIcons.NavigateBack, contentDescription = stringResource(R.string.back))
            }
        },
        actions = {
            AnimatedVisibility(
                value.trim().isNotEmpty(),
                enter = fadeIn(), exit = fadeOut()
            ) {
                IconButton(
                    onClick = onClickClear
                ) {
                    Icon(
                        imageVector = ShamelaIcons.Cancel,
                        contentDescription = stringResource(R.string.clear)
                    )
                }
            }
        }
    )
}

@Composable
private fun SearchTextField(
    value: String,
    onValueChanged: (String) -> Unit,
    hint: String,
    focusRequester: FocusRequester,
    onSearch: () -> Unit = {},
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    BasicTextField(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) keyboardController?.show() },
        value = value,
        onValueChange = onValueChanged,
        singleLine = true,
        textStyle = AppFonts.content(MaterialTheme.typography.bodyLarge)
            .copy(color = MaterialTheme.colorScheme.onSurface),
        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = {
                keyboardController?.hide()
                onSearch()
            }
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterStart
            ) {
                AnimatedVisibility(visible = value.isEmpty(), enter = fadeIn(), exit = fadeOut()) {
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                innerTextField()
            }
        }
    )
}

@ShamelaPrev
@Composable
private fun SearchTopBarPrev() {
    var searchText by remember { mutableStateOf("") }
    AppTheme.ShamelaLibraryTheme {
        SearchTopBar(
            value = searchText,
            onValueChanged = { searchText = it },
            hint = stringResource(R.string.search),
            focusRequester = FocusRequester(),
            onNavigateBack = {},
            onClickSearch = {},
            onClickClear = { searchText = "" }
        )
    }
}