package com.memorygraph.backend.memory.search;

import com.memorygraph.backend.memory.domain.Memory;

/**
 * A match with its memory loaded, ready to be rendered.
 *
 * @param memory  the matching memory, with its media already fetched
 * @param snippet the matching passage with matched words marked using {@link SearchHighlight};
 *                {@code null} for a filter-only browse, where the client should fall back to the
 *                memory's own excerpt
 */
public record SearchResult(Memory memory, String snippet) {
}
