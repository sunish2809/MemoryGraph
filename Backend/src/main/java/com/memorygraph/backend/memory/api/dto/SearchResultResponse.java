package com.memorygraph.backend.memory.api.dto;

import com.memorygraph.backend.memory.search.SearchHighlight;
import com.memorygraph.backend.memory.search.SearchResult;

/**
 * One search result: an ordinary memory summary, plus why it matched.
 * <p>
 * Composed rather than flattened so the memory keeps exactly the shape it has everywhere else in the
 * API, and a client can pass it to the same card component it already uses for lists and timelines.
 * <p>
 * No score is exposed. Position in the page is the ranking, and a raw text-search score is both
 * meaningless to a reader and about to change scale once semantic ranking lands.
 *
 * @param memory  the matching memory
 * @param snippet the matching passage, with matched words wrapped in {@link SearchHighlight#START} and
 *                {@link SearchHighlight#END}. Absent for a filter-only browse, where nothing was
 *                matched and {@code memory.excerpt} is the thing to show.
 */
public record SearchResultResponse(MemorySummaryResponse memory, String snippet) {

    public static SearchResultResponse from(SearchResult result) {
        return new SearchResultResponse(MemorySummaryResponse.from(result.memory()), result.snippet());
    }
}
