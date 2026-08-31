package com.memorygraph.backend.memory.search;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Finds the memories that match a request, in the order they should be shown.
 * <p>
 * This is the seam semantic search arrives through. Today there is one implementation, backed by
 * PostgreSQL full text; embeddings will add a second, and a hybrid that fuses the two. Naming the
 * contract now is what keeps that from becoming a rewrite: whatever the strategy, it has to
 * <ul>
 *   <li>return only memories belonging to {@link SearchCriteria#userId()},</li>
 *   <li>respect the type and date filters rather than ranking around them,</li>
 *   <li>page without losing or repeating rows, and</li>
 *   <li>express rank as position, not as a score only it understands.</li>
 * </ul>
 * An implementation that satisfies those can be dropped in without the service or API above it
 * changing, and two of them can be combined by position alone.
 */
public interface MemorySearcher {

    Page<SearchHit> search(SearchCriteria criteria, Pageable pageable);
}
