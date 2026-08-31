package com.memorygraph.backend.memory.search;

import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.memorygraph.backend.common.time.LocalDayRange;
import com.memorygraph.backend.memory.domain.MemoryType;

/**
 * A fully resolved search request: no absent values, no ambiguity left for a query to interpret.
 * <p>
 * Absent filters are widened to their most permissive form here rather than being handled as special
 * cases further down. That keeps the SQL free of "this clause only applies sometimes" branching, which
 * is where owner-scoping bugs hide. {@code personId} is the exception: there is no "every person"
 * widening that would still return memories with no people linked.
 *
 * @param userId   owner of every memory that may be returned; never widened, for obvious reasons
 * @param query    text to match, or {@code null} when the request is a filter-only browse
 * @param types    memory types to include; never empty
 * @param from     inclusive lower bound on when the memory happened
 * @param to       exclusive upper bound on when the memory happened
 * @param sort     ordering, already reconciled with whether there is anything to rank
 * @param personId when set, only memories linked to this person (owner-scoped elsewhere)
 * @param placeId  when set, only memories linked to this place
 */
public record SearchCriteria(
        UUID userId,
        String query,
        Set<MemoryType> types,
        Instant from,
        Instant to,
        SearchSort sort,
        UUID personId,
        UUID placeId) {

    public SearchCriteria {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(types, "types");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(sort, "sort");
        if (types.isEmpty()) {
            throw new IllegalArgumentException("types must not be empty; use every type to mean unfiltered");
        }
        types = Set.copyOf(types);
    }

    public static SearchCriteria of(UUID userId, String rawQuery, Collection<MemoryType> requestedTypes,
            LocalDayRange range, SearchSort requestedSort) {
        return of(userId, rawQuery, requestedTypes, range, requestedSort, null, null);
    }

    public static SearchCriteria of(UUID userId, String rawQuery, Collection<MemoryType> requestedTypes,
            LocalDayRange range, SearchSort requestedSort, UUID personId) {
        return of(userId, rawQuery, requestedTypes, range, requestedSort, personId, null);
    }

    public static SearchCriteria of(UUID userId, String rawQuery, Collection<MemoryType> requestedTypes,
            LocalDayRange range, SearchSort requestedSort, UUID personId, UUID placeId) {

        String query = normaliseQuery(rawQuery);
        Set<MemoryType> types = requestedTypes == null || requestedTypes.isEmpty()
                ? EnumSet.allOf(MemoryType.class)
                : EnumSet.copyOf(requestedTypes);

        SearchSort sort = requestedSort == SearchSort.RELEVANCE && query == null ? SearchSort.NEWEST : requestedSort;

        return new SearchCriteria(userId, query, types, range.from(), range.to(), sort, personId, placeId);
    }

    public boolean hasQuery() {
        return query != null;
    }

    public boolean hasPerson() {
        return personId != null;
    }

    public boolean hasPlace() {
        return placeId != null;
    }

    /**
     * Blank input is the same request as no input. Treating "   " as a search term would return nothing
     * and look broken.
     */
    private static String normaliseQuery(String rawQuery) {
        if (rawQuery == null) {
            return null;
        }
        String trimmed = rawQuery.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
