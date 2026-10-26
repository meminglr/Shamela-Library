package com.folioreader.ui.activity.searchActivity

import android.content.Context

sealed class SearchEvent {
    class SearchBook(val bookId: String, val query: String, val context: Context) : SearchEvent()
    class OnSearchQueryChanged(val newSearchQuery: String) : SearchEvent()
    class InitEpub(val epubFilePath: String?) : SearchEvent()
    class SearchCategories(val searchQuery: String,  val searchCategories: List<String>, val context: Context) : SearchEvent()

    object ClearSearchQuery : SearchEvent()

    /** Shows the results of the previous search in this book again (from the reader's "back to results"). */
    class RestoreResults(val query: String, val results: List<com.folioreader.model.locators.SearchLocator>) : SearchEvent()
}
