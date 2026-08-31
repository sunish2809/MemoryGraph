package com.memorygraph.backend.memory.api.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateTripRequest(
        @NotBlank @Size(max = 255) String title,
        @NotNull Instant startedAt,
        @NotNull Instant endedAt,
        @Size(max = 10_000) String notes) {
}
