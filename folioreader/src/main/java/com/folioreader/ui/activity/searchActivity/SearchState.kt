package com.folioreader.ui.activity.searchActivity

import com.folioreader.model.locators.SearchLocator

data class SearchState(
    val isLoading:Boolean = false,
    val searchProgress:Float = 0f,
    val searchResults: List<SearchLocator> = emptyList(),
    val sectionSearchResults: List<Pair<String,SearchLocator>> = emptyList(),
    val isListEmpty: Boolean = false,
    val searchQuery:String = "",
    /** Chapter title per resource href (without fragment or leading slash), for result labels. */
    val chapterByHref: Map<String, String> = emptyMap(),
)
