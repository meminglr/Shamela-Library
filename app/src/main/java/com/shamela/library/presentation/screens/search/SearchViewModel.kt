package com.shamela.library.presentation.screens.search


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shamela.library.data.local.files.FilesRepoImpl
import com.shamela.library.domain.usecases.books.BooksUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
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


    fun onEvent(event: SearchEvent) {
        when (event) {
            is SearchEvent.GetAllCategories -> {
                viewModelScope.launch {
                    _searchState.update { it.copy(isLoading = true) }
                    if (_searchState.value.allCategories.isEmpty()){
                    booksUseCases.getAllCategories().collect { category ->
                        _searchState.update {
                                it.copy(
                                    allCategories = it.allCategories + category,
                                    isLoading = false
                                )
                            }
                        }
                    }else{
                        _searchState.update { it.copy(allCategories = emptyList()) }
                        booksUseCases.getAllCategories().collect { category ->
                            _searchState.update {
                                it.copy(
                                    allCategories = it.allCategories + category,
                                    isLoading = false
                                )
                            }
                        }
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
