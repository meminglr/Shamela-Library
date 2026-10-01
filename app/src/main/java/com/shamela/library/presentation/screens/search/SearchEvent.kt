package com.shamela.library.presentation.screens.search

import com.shamela.library.domain.model.Category


sealed class SearchEvent {
    class OnChangeSearchQuery(val newText: String) : SearchEvent()
    class ItemChecked(val category: Category) : SearchEvent()

    object GetAllCategories : SearchEvent()
    object ToggleCategoriesList : SearchEvent()
    object CloseCategoriesList : SearchEvent()
}