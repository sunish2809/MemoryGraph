package com.memorygraph.backend.memory.api.dto;

import java.time.Instant;
import java.util.UUID;

import com.memorygraph.backend.memory.domain.Trip;

public record TripSummaryResponse(
        UUID id,
        String title,
        Instant startedAt,
        Instant endedAt,
        String notes,
        long memoryCount,
        long placeCount,
        long personCount,
        String primaryPlaceName,
        Instant createdAt) {

    public static TripSummaryResponse from(
            Trip trip,
            long memoryCount,
            long placeCount,
            long personCount,
            String primaryPlaceName) {
        return new TripSummaryResponse(
                trip.getId(),
                trip.getTitle(),
                trip.getStartedAt(),
                trip.getEndedAt(),
                trip.getNotes(),
                memoryCount,
                placeCount,
                personCount,
                primaryPlaceName,
                trip.getCreatedAt());
    }
}
