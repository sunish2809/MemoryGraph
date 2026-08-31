package com.memorygraph.backend.memory.api.dto;

import java.time.Instant;

import jakarta.validation.constraints.Size;

/** Null fields are left unchanged; blank {@code notes} clears them. */
public record UpdateTripRequest(
        @Size(max = 255) String title,
        Instant startedAt,
        Instant endedAt,
        @Size(max = 10_000) String notes) {
}
