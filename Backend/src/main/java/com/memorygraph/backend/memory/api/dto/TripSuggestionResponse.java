package com.memorygraph.backend.memory.api.dto;

import java.time.Instant;
import java.util.List;

public record TripSuggestionResponse(
        String title,
        Instant startedAt,
        Instant endedAt,
        long memoryCount,
        long placeCount,
        long personCount,
        String primaryPlaceName,
        List<PlaceSummaryResponse> places,
        List<PersonSummaryResponse> people) {
}
