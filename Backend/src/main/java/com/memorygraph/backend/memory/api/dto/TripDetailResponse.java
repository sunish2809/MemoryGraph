package com.memorygraph.backend.memory.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.memorygraph.backend.memory.domain.Trip;

public record TripDetailResponse(
        UUID id,
        String title,
        Instant startedAt,
        Instant endedAt,
        String notes,
        long memoryCount,
        Instant createdAt,
        List<PlaceSummaryResponse> places,
        List<PersonSummaryResponse> people,
        List<MemorySummaryResponse> memories) {

    public static TripDetailResponse from(
            Trip trip,
            long memoryCount,
            List<PlaceSummaryResponse> places,
            List<PersonSummaryResponse> people,
            List<MemorySummaryResponse> memories) {
        return new TripDetailResponse(
                trip.getId(),
                trip.getTitle(),
                trip.getStartedAt(),
                trip.getEndedAt(),
                trip.getNotes(),
                memoryCount,
                trip.getCreatedAt(),
                places,
                people,
                memories);
    }
}
