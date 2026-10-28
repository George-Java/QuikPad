package com.yg.liquidink.ui.editor

data class SearchResults(
    val ranges: List<IntRange>,
    val truncated: Boolean,
) {
    val count: Int get() = ranges.size

    companion object {
        val Empty = SearchResults(emptyList(), false)
    }
}

/**
 * Literal, non-overlapping search. Returns at most [limit] ranges so a one-letter
 * query in a huge document cannot allocate an unbounded list on the UI thread.
 */
fun findLiteralMatches(
    text: String,
    query: String,
    caseSensitive: Boolean,
    limit: Int = 2000,
): SearchResults {
    if (query.isEmpty() || text.isEmpty()) return SearchResults.Empty
    val ranges = ArrayList<IntRange>(minOf(limit, 32))
    var index = text.indexOf(query, ignoreCase = !caseSensitive)
    while (index >= 0 && index + query.length <= text.length) {
        ranges += index until (index + query.length)
        if (ranges.size >= limit && index + query.length < text.length) {
            // Check whether at least one later match exists for "n+" display.
            val next = text.indexOf(
                query,
                startIndex = index + query.length,
                ignoreCase = !caseSensitive,
            )
            return SearchResults(ranges, truncated = next >= 0)
        }
        index = text.indexOf(
            query,
            startIndex = index + query.length,
            ignoreCase = !caseSensitive,
        )
    }
    return SearchResults(ranges, truncated = false)
}
