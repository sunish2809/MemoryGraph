package com.memorygraph.backend.memory.api.dto;

import java.time.Instant;

/**
 * Headline numbers for the dashboard.
 * <p>
 * Aggregated by the database rather than derived from a page of results, so the figures are the truth
 * about the whole collection and not about whatever happened to be on screen.
 */
public record MemoryStatsResponse(
        long totalMemories,

        /** When the earliest memory happened, which is how far back the timeline reaches. Null if empty. */
        Instant earliestOccurredAt,

        /** Distinct people recognised from chat senders (and later faces). */
        long totalPeople,

        /** Distinct places clustered from photo GPS. */
        long totalPlaces) {
}
