@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.shamela.library.presentation.screens.bookDetails

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LoadingIndicator
import com.shamela.library.presentation.common.sectionDisplayName
import com.shamela.apptheme.presentation.util.AppLocale
import androidx.compose.ui.platform.LocalContext
import com.shamela.library.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.folioreader.ui.composables.TopicsList
import com.shamela.apptheme.presentation.common.DefaultTopBar
import com.shamela.apptheme.presentation.theme.AppFonts
import com.shamela.apptheme.presentation.theme.ShamelaIcons
import com.shamela.library.BuildConfig
import com.shamela.library.domain.model.Book
import com.shamela.library.domain.model.BookDetails
import com.shamela.library.domain.model.BookInfoItem
import com.shamela.library.presentation.common.BookDetailsViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookDetailsScreen(
    book: Book,
    navigateBack: () -> Unit,
    viewModel: BookDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(book.id) { viewModel.load(book) }

    CompositionLocalProvider(LocalLayoutDirection provides AppLocale.layoutDirection(LocalContext.current)) {
        Scaffold(
            topBar = { DefaultTopBar(title = stringResource(R.string.about_book), onNavigateBack = navigateBack) },
            bottomBar = {
                BookActionBottomBar(
                    isDownloaded = state.isDownloaded,
                    isDownloading = state.isDownloading,
                    onDownloadClick = { viewModel.downloadBook() },
                    onOpenClick = { viewModel.openBook() },
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // ---- Modernized Pinned Header ----
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
                        Text(
                            text = book.title,
                            style = AppFonts.content(MaterialTheme.typography.headlineSmallEmphasized),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(12.dp))

                        // Beautiful FlowRow for Metadata with Icons
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            MetaChip(icon = ShamelaIcons.AutoStories, text = stringResource(R.string.book_pages, book.pageCount))
                            book.author.takeUnless { it.isBlank() || it == "-" }?.let {
                                MetaChip(icon = ShamelaIcons.Person, text = it, isContent = true)
                            }
                            state.details?.authorDeathYear?.let {
                                MetaChip(icon = ShamelaIcons.Event, text = stringResource(R.string.author_death_year, it.toString()))
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Category Tag
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = sectionDisplayName(book.categoryName),
                                style = AppFonts.content(MaterialTheme.typography.labelLarge),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }

                val details = state.details
                if (state.isLoading) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }
                } else if (details == null ||
                    (details.description.isNullOrBlank() && details.topics.isEmpty() &&
                            details.about.isEmpty())
                ) {
                    Box(
                        Modifier.fillMaxWidth().weight(1f).padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = ShamelaIcons.Info,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.outlineVariant
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.book_details_unavailable),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    val tabs = buildList {
                        if (!details.description.isNullOrBlank()) add(R.string.tab_description)
                        if (details.topics.isNotEmpty()) add(R.string.tab_topics)
                        if (details.about.isNotEmpty()) add(R.string.tab_details)
                    }
                    var selected by remember { mutableIntStateOf(0) }
                    val current = selected.coerceIn(0, tabs.lastIndex)

                    // Elegant Tab Row
                    TabRow(
                        selectedTabIndex = current,
                        containerColor = MaterialTheme.colorScheme.surface,
                        divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) },
                        indicator = { tabPositions ->
                            if (current < tabPositions.size) {
                                TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[current]),
                                    height = 3.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    ) {
                        tabs.forEachIndexed { i, title ->
                            Tab(
                                selected = current == i,
                                onClick = { selected = i },
                                text = {
                                    Text(
                                        text = stringResource(title),
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                },
                                selectedContentColor = MaterialTheme.colorScheme.primary,
                                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Smooth Transition between tabs
                    Crossfade(
                        targetState = tabs[current],
                        animationSpec = tween(300),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        label = "Tab Transition"
                    ) { currentTab ->
                        when (currentTab) {
                            R.string.tab_description -> DescriptionTab(details)
                            R.string.tab_topics -> TopicsList(details.topics)
                            R.string.tab_details -> DetailsTab(details.about)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaChip(icon: ImageVector, text: String, isContent: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = if (isContent) AppFonts.content(MaterialTheme.typography.bodyMedium) else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun BookDetailsUnavailable(navigateBack: () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides AppLocale.layoutDirection(LocalContext.current)) {
        Scaffold(
            topBar = { DefaultTopBar(title = stringResource(R.string.about_book), onNavigateBack = navigateBack) }
        ) { padding ->
            Box(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = ShamelaIcons.Info,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.book_details_error),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DescriptionTab(details: BookDetails) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = details.description.orEmpty(),
            style = AppFonts.content(MaterialTheme.typography.bodyLarge).copy(lineHeight = 30.sp),
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(24.dp))

        // Beautiful Info Box for Source / AI Disclaimer
        val src = details.descriptionSource?.takeIf { it.isNotBlank() }
        val isAI = src == null

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = ShamelaIcons.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (!isAI) stringResource(R.string.description_source, src.orEmpty()) else stringResource(R.string.description_ai_generated),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (BuildConfig.DEBUG && isAI) {
                        val model = details.descriptionModel?.takeIf { it.isNotBlank() } ?: stringResource(R.string.unknown)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.description_model, model),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailsTab(about: List<BookInfoItem>) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(vertical = 8.dp)) {
                about.forEachIndexed { index, info ->
                    InfoRow(info)
                    if (index < about.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(info: BookInfoItem) {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        if (info.label.isBlank()) {
            Text(
                text = info.value,
                style = AppFonts.content(MaterialTheme.typography.bodyMedium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = info.label,
                    style = AppFonts.content(MaterialTheme.typography.labelLarge),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(0.35f)
                )
                Text(
                    text = info.value,
                    style = AppFonts.content(MaterialTheme.typography.bodyMedium),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(0.65f),
                )
            }
        }
    }
}

@Composable
private fun BookActionBottomBar(
    isDownloaded: Boolean,
    isDownloading: Boolean,
    onDownloadClick: () -> Unit,
    onOpenClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Button(
                shapes = ButtonDefaults.shapes(),
                onClick = if (isDownloaded) onOpenClick else onDownloadClick,
                enabled = !isDownloading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),

            ) {

                if (isDownloading) {
                    // Show progress spinner
                    LoadingIndicator(
                        modifier = Modifier.size(28.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(text = stringResource(R.string.downloading), style = MaterialTheme.typography.titleMedium)
                } else if (isDownloaded) {
                    // Show Read button
                    Icon(
                        imageVector = ShamelaIcons.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(text = stringResource(R.string.read_book), style = MaterialTheme.typography.titleMedium)
                } else {
                    // Show Download button
                    Icon(
                        imageVector = ShamelaIcons.FileDownload,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(text = stringResource(R.string.download_book), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}