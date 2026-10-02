package com.shamela.library.presentation.screens.favorite


import androidx.compose.foundation.layout.Arrangement
import com.shamela.library.presentation.common.SegmentGap
import androidx.compose.foundation.lazy.itemsIndexed
import com.shamela.apptheme.presentation.common.SegmentedTabs
import com.shamela.library.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shamela.apptheme.presentation.common.EmptyListScreen
import com.shamela.apptheme.presentation.theme.AppFonts
import com.shamela.library.data.local.files.FilesBooksRepoImpl
import com.shamela.library.domain.model.Book
import com.shamela.library.presentation.common.FavoriteBookItem
import com.shamela.library.presentation.common.QuoteItem
import com.shamela.library.presentation.common.StringHeader
import com.shamela.library.presentation.screens.LocalPaddingValues

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FavoriteScreen(
    navigateToBookDetails: (Book) -> Unit,
    viewModel: FavoriteViewModel = hiltViewModel(),
) {
    val state = viewModel.favoriteState.collectAsStateWithLifecycle().value
    val localPadding = LocalPaddingValues.current

    LaunchedEffect(Unit){
        viewModel.onEvent(FavoriteEvent.LoadFavoriteQuotes)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(localPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SegmentGap),
        contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
    ) {
        item {
            SegmentedTabs(
                options = FavoriteViewType.entries,
                selected = state.viewType,
                label = { stringResource(it.label) },
                onSelect = { viewModel.onEvent(FavoriteEvent.OnChangeViewType(it)) },
            )
        }
        when(state.viewType){
            FavoriteViewType.Quotes -> {
                val quotesMap = state.favoriteQuotes.groupBy { it.bookName }
                quotesMap.forEach{ (bookName, quotes) ->
                    stickyHeader {
                        StringHeader(
                            modifier = Modifier,
                            bookName = bookName
                        )
                    }
                    itemsIndexed(quotes, key = { _, it -> it.quoteId }) { index, currentQuote ->
                        QuoteItem(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .animateItem(),
                            item = currentQuote,
                            onClick = { viewModel.onEvent(FavoriteEvent.OpenBookForQuote(currentQuote)) },
                            index = index,
                            count = quotes.size,
                        )
                    }

                }
                item {
                    EmptyListScreen(
                        visibility = state.isListEmpty,
                        text = stringResource(R.string.favorite_quotes_empty),
                        modifier = Modifier.fillParentMaxSize()
                    )
                }
            }
            FavoriteViewType.Books -> {
                itemsIndexed(state.favoriteBooks, key = { _, it -> it.id }) { index, currentBook ->
                    FavoriteBookItem(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .animateItem(),
                        onClick = {
                            FilesBooksRepoImpl.openEpub(
                                currentBook,
                                onAddQuoteToFavorite = { quote ->
                                    viewModel.onEvent(FavoriteEvent.AddQuoteToFavorite(quote))
                                })
                        },
                        index = index,
                        count = state.favoriteBooks.size,
                        onFavoriteIconClicked = {
                            viewModel.onEvent(FavoriteEvent.ToggleFavorite(currentBook))
                        },
                        item = currentBook,
                        onInfoClick = { navigateToBookDetails(currentBook) },
                    )
                }
                item {
                    EmptyListScreen(
                        visibility = state.isListEmpty,
                        text = stringResource(R.string.favorite_books_empty),
                        modifier = Modifier.fillParentMaxSize())
                }
            }
        }

    }
}
