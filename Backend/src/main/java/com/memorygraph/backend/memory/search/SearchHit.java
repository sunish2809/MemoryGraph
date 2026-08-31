package com.memorygraph.backend.memory.search;

import java.util.UUID;

/**
 * One matching memory, identified but not yet loaded.
 * <p>
 * Deliberately carries no score. Rank is expressed by position in the returned page, which is a
 * contract every future strategy can meet: two ranked lists can be combined by position without
 * needing a shared score scale, whereas a raw {@code ts_rank_cd} value has no meaning next to a vector
 * distance.
 *
 * @param memoryId the match
 * @param snippet  the part of the memory that matched, with the matching words marked; {@code null}
 *                 for a filter-only browse, where nothing was matched against
 */
public record SearchHit(UUID memoryId, String snippet) {
}
