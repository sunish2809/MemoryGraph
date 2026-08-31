package com.memorygraph.backend.memory.api.dto;

import java.time.Instant;
import java.util.UUID;

import com.memorygraph.backend.memory.domain.Place;

public record PlaceSummaryResponse(
        UUID id,
        String displayName,
        double latitude,
        double longitude,
        boolean geocoded,
        boolean nameLocked,
        long memoryCount,
        Instant createdAt) {

    public static PlaceSummaryResponse from(Place place, long memoryCount) {
        return new PlaceSummaryResponse(
                place.getId(),
                place.getDisplayName(),
                place.getLatitude(),
                place.getLongitude(),
                place.getGeocodedAt() != null,
                place.isNameLocked(),
                memoryCount,
                place.getCreatedAt());
    }
}
