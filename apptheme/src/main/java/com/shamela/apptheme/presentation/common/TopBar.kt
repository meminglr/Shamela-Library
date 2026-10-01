package com.shamela.apptheme.presentation.common

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.shamela.apptheme.R
import com.shamela.apptheme.presentation.theme.AppFonts
import com.shamela.apptheme.presentation.theme.AppTheme
import com.shamela.apptheme.presentation.theme.ShamelaIcons
import com.shamela.apptheme.presentation.util.ShamelaPrev

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefaultTopBar(
    title: String,
    actionIcon: ImageVector? = null,
    onActionClick: (() -> Unit) = {},
    onNavigateBack: (() -> Unit)? = null,
    navigationContent: (@Composable () -> Unit)? = null,
) {
    CenterAlignedTopAppBar(
        modifier = Modifier,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        actions = {
            AnimatedVisibility(visible = actionIcon != null) {
                actionIcon?.let {
                    IconButton(onClick = onActionClick) {
                        Icon(it, contentDescription = stringResource(R.string.search))
                    }
                }
            }
        },
        navigationIcon = {
            when {
                navigationContent != null -> navigationContent()
                onNavigateBack != null -> IconButton(onClick = onNavigateBack) {
                    Icon(ShamelaIcons.NavigateBack, contentDescription = stringResource(R.string.back))
                }
            }
        }
    )
}

@ShamelaPrev
@Composable
private fun DefaultTopBarPrev() {
    AppTheme.ShamelaLibraryTheme {
        DefaultTopBar(stringResource(R.string.normal))
    }
}

@ShamelaPrev
@Composable
private fun DefaultTopBar2Prev() {
    AppTheme.ShamelaLibraryTheme {
        DefaultTopBar(stringResource(R.string.normal), onNavigateBack = {})
    }
}

@ShamelaPrev
@Composable
private fun DefaultTopBar3Prev() {
    AppTheme.ShamelaLibraryTheme {
        DefaultTopBar(
            stringResource(R.string.normal),
            onNavigateBack = {},
            actionIcon = ShamelaIcons.Search
        )
    }
}