package com.memorygraph.backend.memory.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.memorygraph.backend.memory.domain.Place;

public record PlaceDetailResponse(
        UUID id,
        String displayName,
        double latitude,
        double longitude,
        boolean geocoded,
        boolean nameLocked,
        long memoryCount,
        Instant createdAt,
        List<MemorySummaryResponse> memories) {

    public static PlaceDetailResponse from(Place place, long memoryCount, List<MemorySummaryResponse> memories) {
        return new PlaceDetailResponse(
                place.getId(),
                place.getDisplayName(),
                place.getLatitude(),
                place.getLongitude(),
                place.getGeocodedAt() != null,
                place.isNameLocked(),
                memoryCount,
                place.getCreatedAt(),
                memories);
    }
}
