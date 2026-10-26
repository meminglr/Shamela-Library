package com.shamela.library.presentation.screens.search


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shamela.library.data.local.files.FilesRepoImpl
import com.shamela.library.domain.usecases.books.BooksUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    @FilesRepoImpl private val booksUseCases: BooksUseCases,
) : ViewModel() {
    private val _searchState = MutableStateFlow<SearchState>(SearchState())
    val searchState = _searchState.asStateFlow()
    private var loadJob: Job? = null


    fun onEvent(event: SearchEvent) {
        when (event) {
            is SearchEvent.GetAllCategories -> {
                // Re-read on every visit (books may have been downloaded meanwhile), but swap the
                // list in once: clearing it first made the sections flicker, and an empty library
                // left the screen loading forever.
                loadJob?.cancel()
                loadJob = viewModelScope.launch {
                    _searchState.update { it.copy(isLoading = it.allCategories.isEmpty()) }
                    val categories = booksUseCases.getAllCategories().toList()
                    _searchState.update { state ->
                        state.copy(
                            allCategories = categories,
                            // Keep the selection, minus sections that no longer exist.
                            selectedCategories = state.selectedCategories.mapNotNull { selected ->
                                categories.firstOrNull { it.name == selected.name }
                            },
                            isLoading = false,
                        )
                    }
                }
            }

            SearchEvent.ToggleCategoriesList -> {
                _searchState.update { it.copy(isListExpanded = !it.isListExpanded) }
            }

            SearchEvent.CloseCategoriesList -> {
                _searchState.update { it.copy(isListExpanded = false) }
            }

            is SearchEvent.ItemChecked -> {
                _searchState.update {
                    it.copy(
                        selectedCategories =
                        if (it.selectedCategories.contains(event.category))
                            it.selectedCategories - event.category
                        else
                            it.selectedCategories + event.category
                    )
                }
            }

            is SearchEvent.OnChangeSearchQuery -> {
                _searchState.update {
                    it.copy(searchQuery = event.newText)
                }
            }
        }

    }
}
