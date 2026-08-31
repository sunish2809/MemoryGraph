package com.memorygraph.backend.memory.api.dto;

import java.util.List;

public record TripsPageResponse(
        List<TripSummaryResponse> trips,
        List<TripSuggestionResponse> suggestions) {
}
