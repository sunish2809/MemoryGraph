package com.memorygraph.backend.memory.api.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A memory the user typed. {@code occurredAt} is optional: leaving it out means "now", while setting
 * it is how a note about something that happened last year lands in the right place on the timeline.
 */
public record CreateTextMemoryRequest(
        @Size(max = 255) String title,
        @Size(max = 10_000) String description,
        @NotBlank @Size(max = 100_000) String content,
        Instant occurredAt) {
}
